import io
import uuid
from decimal import Decimal
from datetime import datetime
from fastapi import APIRouter, Depends, HTTPException, Query, Response
from sqlalchemy.orm import Session
from typing import List
from reportlab.lib.pagesizes import A4
from reportlab.pdfgen import canvas
from reportlab.lib import colors

from backend.app.database import get_db
from backend.app.models.entities import Invoice, InvoiceLine, Product, Customer, VanStockAllowance, StockLedger, Tenant, User, Van
from backend.app.schemas.schemas import InvoiceCreate, InvoiceResponse
from backend.app.security import get_authenticated_user

router = APIRouter(prefix="/invoices", tags=["Invoices"])

def quantize_bhd(val: Decimal) -> Decimal:
    return val.quantize(Decimal("0.001"))

@router.get("", response_model=List[InvoiceResponse])
def get_invoices(
    tenant_id: str = Query(...),
    van_id: str = Query(None),
    limit: int = 50,
    db: Session = Depends(get_db),
    user: User = Depends(get_authenticated_user)
):
    if user.role.upper() == "VAN_REP":
        if not user.assigned_van_id:
            raise HTTPException(status_code=403, detail="No assigned van")
        if van_id and van_id != user.assigned_van_id:
            raise HTTPException(status_code=403, detail="Assigned van required")
        van_id = user.assigned_van_id
    q = db.query(Invoice).filter(Invoice.tenant_id == tenant_id)
    if van_id:
        q = q.filter(Invoice.van_id == van_id)
    return q.order_by(Invoice.date.desc()).limit(limit).all()

@router.post("", response_model=InvoiceResponse)
def create_invoice(req: InvoiceCreate, db: Session = Depends(get_db),
                   user: User = Depends(get_authenticated_user)):
    if user.role.upper() == "VAN_REP":
        if not user.assigned_van_id or req.van_id != user.assigned_van_id:
            raise HTTPException(status_code=403, detail="Salesperson can only sell from assigned van")
    if not req.lines:
        raise HTTPException(status_code=400, detail="Invoice must contain at least one line item")
    if not req.invoice_number.strip():
        raise HTTPException(status_code=400, detail="Invoice number is required")
    existing = db.query(Invoice).filter(Invoice.invoice_number == req.invoice_number.strip()).first()
    if existing is not None:
        raise HTTPException(status_code=409, detail="Duplicate invoice number: inspect existing invoice before retrying")

    # Tenant-bound object validation; never decrement inventory in another company.
    if req.van_id is not None:
        van = db.query(Van).filter(Van.id == req.van_id, Van.tenant_id == req.tenant_id).first()
        if van is None:
            raise HTTPException(status_code=404, detail="Van not found")
    # Verify customer
    customer = db.query(Customer).filter(Customer.id == req.customer_id, Customer.tenant_id == req.tenant_id).with_for_update().first()
    if not customer:
        raise HTTPException(status_code=404, detail="Customer not found")

    invoice_subtotal = Decimal("0.000")
    invoice_vat_total = Decimal("0.000")
    invoice_grand_total = Decimal("0.000")

    processed_lines = []

    for item in req.lines:
        product = db.query(Product).filter(Product.id == item.product_id,
                                           Product.tenant_id == req.tenant_id,
                                           Product.is_active.is_(True)).first()
        if product is None:
            raise HTTPException(status_code=404, detail="Product not found")
        if Decimal(str(item.unit_price)) != Decimal(str(product.unit_price)) or Decimal(str(item.vat_rate)) != Decimal(str(product.vat_rate)):
            raise HTTPException(status_code=409, detail="Product pricing changed; refresh the invoice")
        qty = quantize_bhd(Decimal(str(item.quantity)))
        unit_price = quantize_bhd(Decimal(str(item.unit_price)))
        vat_rate = quantize_bhd(Decimal(str(item.vat_rate)))

        if qty <= 0 or unit_price < 0 or vat_rate < 0:
            raise HTTPException(status_code=400, detail="Invalid invoice quantity or price")
        line_gross = quantize_bhd(qty * unit_price)
        one_plus_rate = Decimal("1.000") + vat_rate
        line_net = quantize_bhd(line_gross / one_plus_rate)
        line_vat = line_gross - line_net

        invoice_subtotal += line_net
        invoice_vat_total += line_vat
        invoice_grand_total += line_gross

        processed_lines.append({
            "product_id": item.product_id,
            "product_name": product.name,
            "quantity": qty,
            "unit_price": unit_price,
            "vat_rate": vat_rate,
            "vat_amount": line_vat,
            "line_total": line_gross
        })

    # Credit limit validation
    if req.payment_mode.upper() == "CREDIT":
        new_balance = customer.outstanding_balance + invoice_grand_total
        if customer.credit_limit > 0 and new_balance > customer.credit_limit:
            raise HTTPException(
                status_code=400,
                detail=f"Credit limit exceeded. Limit: BHD {customer.credit_limit}, New Balance: BHD {new_balance}"
            )
        customer.outstanding_balance = new_balance

    # Create Invoice entity
    invoice_id = str(uuid.uuid4())
    invoice = Invoice(
        id=invoice_id,
        tenant_id=req.tenant_id,
        invoice_number=req.invoice_number.strip(),
        van_id=req.van_id,
        customer_id=customer.id,
        customer_name=customer.name,
        customer_trn=customer.trn_number or req.customer_trn,
        date=datetime.utcnow(),
        subtotal=invoice_subtotal,
        vat_total=invoice_vat_total,
        total=invoice_grand_total,
        payment_mode=req.payment_mode.upper(),
        status="POSTED",
        created_by=user.id
    )
    db.add(invoice)

    # Process lines & deduct stock
    for index, pline in enumerate(processed_lines):
        line_entity = InvoiceLine(
            id=str(uuid.uuid4()),
            invoice_id=invoice_id,
            product_id=pline["product_id"],
            product_name=pline["product_name"],
            quantity=pline["quantity"],
            unit_price=pline["unit_price"],
            vat_rate=pline["vat_rate"],
            vat_amount=pline["vat_amount"],
            line_total=pline["line_total"]
        )
        db.add(line_entity)

        # Deduct van stock allowance if van is provided
        if req.van_id:
            van_allowance = db.query(VanStockAllowance).filter(
                VanStockAllowance.van_id == req.van_id,
                VanStockAllowance.tenant_id == req.tenant_id,
                VanStockAllowance.product_id == pline["product_id"]
            ).with_for_update().first()
            if not van_allowance or van_allowance.current_quantity < pline["quantity"]:
                db.rollback()
                raise HTTPException(status_code=409, detail="Insufficient van stock")
            van_allowance.current_quantity -= pline["quantity"]
        else:
            warehouse_product = db.query(Product).filter(Product.id == pline["product_id"],
                Product.tenant_id == req.tenant_id).with_for_update().first()
            if warehouse_product is None or warehouse_product.warehouse_stock < pline["quantity"]:
                db.rollback()
                raise HTTPException(status_code=409, detail="Insufficient warehouse stock")
            warehouse_product.warehouse_stock -= pline["quantity"]

        # Write immutable stock ledger movement
        movement = StockLedger(
            id=str(uuid.uuid4()),
            tenant_id=req.tenant_id,
            movement_id=f"MOV-SALE-{invoice_id}-{index}",
            timestamp=datetime.utcnow(),
            movement_type="VAN_SALE",
            product_id=pline["product_id"],
            source_location=f"VAN:{req.van_id}" if req.van_id else "WAREHOUSE_MAIN",
            destination_location=f"CUSTOMER:{customer.name}",
            quantity=pline["quantity"],
            unit_cost=Decimal("0.000"),
            total_value=pline["line_total"],
            reference_document=req.invoice_number,
            created_by=user.id
        )
        db.add(movement)

    db.commit()
    db.refresh(invoice)
    return invoice

@router.get("/{invoice_id}/pdf")
def generate_tax_invoice_pdf(invoice_id: str, db: Session = Depends(get_db),
                             user: User = Depends(get_authenticated_user)):
    """
    Renders an authoritative, vector A4 Bahrain Tax Invoice conforming to NBR standards.
    """
    invoice = db.query(Invoice).filter(Invoice.id == invoice_id,
                                       Invoice.tenant_id == user.tenant_id).first()
    if not invoice:
        raise HTTPException(status_code=404, detail="Invoice not found")

    tenant = db.query(Tenant).filter(Tenant.id == invoice.tenant_id).first()

    buffer = io.BytesIO()
    p = canvas.Canvas(buffer, pagesize=A4)
    width, height = A4

    # Header Banner
    p.setFillColor(colors.HexColor("#0D47A1"))
    p.rect(0, height - 80, width, 80, fill=True, stroke=False)
    p.setFillColor(colors.white)
    p.setFont("Helvetica-Bold", 20)
    p.drawString(40, height - 45, tenant.company_name if tenant else "KANAK FMCG Wholesale")
    p.setFont("Helvetica", 11)
    p.drawString(40, height - 65, "TAX INVOICE" if tenant and tenant.trn_number else "SALES INVOICE - TRN NOT PROVIDED")

    # NBR VAT Metadata
    p.setFillColor(colors.black)
    p.setFont("Helvetica-Bold", 10)
    p.drawString(40, height - 110, "Tax Invoice No:")
    p.setFont("Helvetica", 10)
    p.drawString(130, height - 110, invoice.invoice_number)

    p.setFont("Helvetica-Bold", 10)
    p.drawString(40, height - 125, "Date & Time:")
    p.setFont("Helvetica", 10)
    p.drawString(130, height - 125, invoice.date.strftime("%Y-%m-%d %H:%M:%S"))

    p.setFont("Helvetica-Bold", 10)
    p.drawString(340, height - 110, "Supplier TRN:")
    p.setFont("Helvetica", 10)
    p.drawString(430, height - 110, tenant.trn_number if tenant and tenant.trn_number else "Not provided")

    p.setFont("Helvetica-Bold", 10)
    p.drawString(340, height - 125, "Supplier CR:")
    p.setFont("Helvetica", 10)
    p.drawString(430, height - 125, tenant.cr_number if tenant and tenant.cr_number else "Not provided")

    # Customer Information Box
    p.setStrokeColor(colors.HexColor("#B0BEC5"))
    p.rect(40, height - 185, width - 80, 45, fill=False)
    p.setFont("Helvetica-Bold", 10)
    p.drawString(50, height - 155, "Customer / العميل:")
    p.setFont("Helvetica", 10)
    p.drawString(150, height - 155, invoice.customer_name)

    p.setFont("Helvetica-Bold", 10)
    p.drawString(50, height - 175, "Customer TRN:")
    p.setFont("Helvetica", 10)
    p.drawString(150, height - 175, invoice.customer_trn or "N/A (Cash / Retail)")

    # Line Item Table Header
    y = height - 215
    p.setFillColor(colors.HexColor("#ECEFF1"))
    p.rect(40, y - 5, width - 80, 20, fill=True, stroke=False)
    p.setFillColor(colors.black)
    p.setFont("Helvetica-Bold", 9)
    p.drawString(45, y, "Item Description")
    p.drawString(240, y, "Qty")
    p.drawString(300, y, "Unit Price")
    p.drawString(380, y, "VAT (10%)")
    p.drawString(460, y, "Total (BHD)")

    y -= 25
    p.setFont("Helvetica", 9)
    for line in invoice.lines:
        p.drawString(45, y, line.product_name[:35])
        p.drawString(240, y, f"{line.quantity:.3f}")
        p.drawString(300, y, f"{line.unit_price:.3f}")
        p.drawString(380, y, f"{line.vat_amount:.3f}")
        p.drawString(460, y, f"{line.line_total:.3f}")
        y -= 20
        if y < 150:
            p.showPage()
            y = height - 50

    # Totals Summary Box
    p.setStrokeColor(colors.HexColor("#B0BEC5"))
    p.rect(320, 100, width - 360, 80, fill=False)
    p.setFont("Helvetica-Bold", 10)
    p.drawString(330, 160, "Subtotal (Excl. VAT):")
    p.drawString(460, 160, f"BHD {invoice.subtotal:.3f}")

    p.drawString(330, 140, "VAT Total (10%):")
    p.drawString(460, 140, f"BHD {invoice.vat_total:.3f}")

    p.setFont("Helvetica-Bold", 11)
    p.setFillColor(colors.HexColor("#0D47A1"))
    p.drawString(330, 115, "Grand Total:")
    p.drawString(460, 115, f"BHD {invoice.total:.3f}")

    # Payment mode footer
    p.setFillColor(colors.HexColor("#546E7A"))
    p.setFont("Helvetica-Oblique", 9)
    p.drawString(40, 80, f"Payment Mode: {invoice.payment_mode} • Issued under National Bureau for Revenue (NBR) Regulations")

    p.save()
    buffer.seek(0)
    return Response(content=buffer.getvalue(), media_type="application/pdf")
