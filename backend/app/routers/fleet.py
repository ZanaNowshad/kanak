from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session
from typing import List
import uuid
from decimal import Decimal
from pydantic import BaseModel
from backend.app.database import get_db
from backend.app.models.entities import Van, VanStockAllowance, Product, StockLedger, User
from backend.app.security import get_authenticated_user
from backend.app.schemas.schemas import VanCreate, VanResponse, VanStockTransferRequest

router = APIRouter(prefix="/fleet", tags=["Fleet & Vans"])

class VanStockAllowanceItem(BaseModel):
    product_id: str
    product_name: str
    barcode: str
    current_quantity: Decimal
    assigned_quantity: Decimal
    unit_price: Decimal

@router.get("/vans", response_model=List[VanResponse])
def get_vans(tenant_id: str = Query(...), db: Session = Depends(get_db)):
    return db.query(Van).filter(Van.tenant_id == tenant_id, Van.is_active == True).order_by(Van.van_code).all()

@router.post("/vans", response_model=VanResponse)
def create_van(req: VanCreate, db: Session = Depends(get_db)):
    existing = db.query(Van).filter(Van.tenant_id == req.tenant_id, Van.van_code == req.van_code.strip()).first()
    if existing:
        raise HTTPException(status_code=400, detail="Van with this code already exists")

    van = Van(
        tenant_id=req.tenant_id,
        van_code=req.van_code.strip(),
        plate_number=req.plate_number.strip(),
        model=req.model,
        driver_id=req.driver_id,
        is_active=True
    )
    db.add(van)
    db.commit()
    db.refresh(van)
    return van

@router.get("/vans/{van_id}/stock", response_model=List[VanStockAllowanceItem])
def get_van_stock(van_id: str, db: Session = Depends(get_db),
                  user: User = Depends(get_authenticated_user)):
    if user.role.upper() == "VAN_REP" and user.assigned_van_id != van_id:
        raise HTTPException(status_code=403, detail="Assigned van required")
    van = db.query(Van).filter(Van.id == van_id, Van.tenant_id == user.tenant_id).first()
    if van is None:
        raise HTTPException(status_code=404, detail="Van not found")
    allowances = db.query(VanStockAllowance).filter(VanStockAllowance.van_id == van_id,
                                                    VanStockAllowance.tenant_id == user.tenant_id).all()
    results = []
    for a in allowances:
        prod = db.query(Product).filter(Product.id == a.product_id,
                                        Product.tenant_id == user.tenant_id).first()
        if prod:
            results.append(VanStockAllowanceItem(
                product_id=prod.id,
                product_name=prod.name,
                barcode=prod.barcode,
                current_quantity=a.current_quantity,
                assigned_quantity=a.assigned_quantity,
                unit_price=prod.unit_price
            ))
    return results

@router.post("/vans/transfer")
def transfer_stock(req: VanStockTransferRequest, db: Session = Depends(get_db),
                   user: User = Depends(get_authenticated_user)):
    """
    Executes a stock transfer between warehouse and van with ledger tracking.
    """
    van = db.query(Van).filter(Van.id == req.van_id, Van.tenant_id == req.tenant_id).first()
    if not van:
        raise HTTPException(status_code=404, detail="Van not found")

    prod = db.query(Product).filter(Product.id == req.product_id, Product.tenant_id == req.tenant_id).with_for_update().first()
    if not prod:
        raise HTTPException(status_code=404, detail="Product not found")

    qty = Decimal(str(req.quantity))
    if not qty.is_finite() or qty <= 0 or qty.as_tuple().exponent < -3:
        raise HTTPException(status_code=422, detail="Transfer quantity must be positive with at most three decimals")

    allowance = db.query(VanStockAllowance).filter(
        VanStockAllowance.van_id == req.van_id,
        VanStockAllowance.product_id == req.product_id,
        VanStockAllowance.tenant_id == req.tenant_id
    ).with_for_update().first()

    if req.transfer_type == "LOAD_TO_VAN":
        if prod.warehouse_stock < qty:
            raise HTTPException(status_code=400, detail="Insufficient warehouse stock")
        prod.warehouse_stock -= qty
        if not allowance:
            allowance = VanStockAllowance(
                tenant_id=req.tenant_id,
                van_id=req.van_id,
                product_id=req.product_id,
                assigned_quantity=qty,
                current_quantity=qty
            )
            db.add(allowance)
        else:
            allowance.current_quantity += qty
            allowance.assigned_quantity += qty
        
        movement_type = "VAN_LOAD"
        src = "WAREHOUSE_MAIN"
        dst = f"VAN:{van.van_code}"

    elif req.transfer_type == "UNLOAD_TO_WAREHOUSE":
        if not allowance or allowance.current_quantity < qty:
            raise HTTPException(status_code=400, detail="Insufficient stock in van")
        allowance.current_quantity -= qty
        prod.warehouse_stock += qty
        movement_type = "VAN_UNLOAD"
        src = f"VAN:{van.van_code}"
        dst = "WAREHOUSE_MAIN"
    else:
        raise HTTPException(status_code=400, detail="Invalid transfer type")

    ledger_entry = StockLedger(
        tenant_id=req.tenant_id,
        movement_id=f"MOV-TRF-{uuid.uuid4()}",
        movement_type=movement_type,
        product_id=prod.id,
        source_location=src,
        destination_location=dst,
        quantity=qty,
        unit_cost=prod.unit_cost,
        total_value=qty * prod.unit_price,
        reference_document=f"TRF-{van.van_code}",
        created_by=user.id
    )
    db.add(ledger_entry)
    db.commit()

    return {"status": "SUCCESS", "message": f"Successfully transferred {qty} of {prod.name}"}
