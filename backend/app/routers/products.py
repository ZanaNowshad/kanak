from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session
from typing import List, Optional
import urllib.parse
from backend.app.database import get_db
from backend.app.models.entities import Product, VanStockAllowance
from backend.app.schemas.schemas import ProductCreate, ProductResponse

router = APIRouter(prefix="/products", tags=["Products"])

def generate_bing_thumbnail(barcode: str, name: str) -> str:
    cleaned_barcode = barcode.strip()
    cleaned_name = " ".join(name.strip().split())
    query = f"{cleaned_barcode} {cleaned_name}".strip()
    encoded_query = urllib.parse.quote_plus(query)
    return f"https://tse1.mm.bing.net/th?q={encoded_query}"

@router.get("", response_model=List[ProductResponse])
def get_products(
    tenant_id: str = Query(...),
    category: Optional[str] = None,
    search: Optional[str] = None,
    db: Session = Depends(get_db)
):
    query = db.query(Product).filter(Product.tenant_id == tenant_id, Product.is_active == True)
    if category:
        query = query.filter(Product.category == category)
    if search:
        search_filter = f"%{search}%"
        query = query.filter((Product.name.ilike(search_filter)) | (Product.barcode.ilike(search_filter)))
    
    return query.order_by(Product.name).all()

@router.get("/barcode/{barcode}", response_model=ProductResponse)
def get_product_by_barcode(
    barcode: str,
    tenant_id: str = Query(...),
    db: Session = Depends(get_db)
):
    prod = db.query(Product).filter(
        Product.tenant_id == tenant_id,
        Product.barcode == barcode.strip(),
        Product.is_active == True
    ).first()
    if not prod:
        raise HTTPException(status_code=404, detail="Product not found")
    return prod

@router.post("", response_model=ProductResponse)
def create_product(req: ProductCreate, db: Session = Depends(get_db)):
    existing = db.query(Product).filter(
        Product.tenant_id == req.tenant_id,
        Product.barcode == req.barcode.strip()
    ).first()
    if existing:
        raise HTTPException(status_code=400, detail="Product with this barcode already exists for tenant")

    if (not req.barcode.strip() or not req.name.strip() or
            req.unit_price < 0 or req.unit_cost < 0 or req.vat_rate < 0 or
            req.vat_rate > 1 or req.warehouse_stock != 0 or
            not all(x.is_finite() for x in
                (req.unit_price, req.unit_cost, req.vat_rate, req.warehouse_stock))):
        raise HTTPException(status_code=422, detail="Invalid SKU, price or opening stock: stock must start at zero")

    image_url = req.image_url
    if not image_url:
        image_url = generate_bing_thumbnail(req.barcode, req.name)

    prod = Product(
        tenant_id=req.tenant_id,
        barcode=req.barcode.strip(),
        name=req.name.strip(),
        category=req.category.strip(),
        unit=req.unit.strip(),
        unit_cost=req.unit_cost,
        unit_price=req.unit_price,
        vat_rate=req.vat_rate,
        warehouse_stock=req.warehouse_stock,
        image_url=image_url,
        is_active=True
    )
    db.add(prod)
    db.commit()
    db.refresh(prod)
    return prod


@router.put("/{product_id}", response_model=ProductResponse)
def update_product(product_id: str, req: ProductCreate, db: Session = Depends(get_db)):
    prod = db.query(Product).filter(
        Product.id == product_id, Product.tenant_id == req.tenant_id,
        Product.is_active.is_(True)
    ).with_for_update().first()
    if not prod:
        raise HTTPException(status_code=404, detail="Product not found")
    if (not req.barcode.strip() or not req.name.strip() or
            any(not x.is_finite() or x < 0 for x in
                (req.unit_price, req.unit_cost, req.vat_rate)) or req.vat_rate > 1):
        raise HTTPException(status_code=422, detail="Invalid product pricing")
    collision = db.query(Product).filter(
        Product.tenant_id == req.tenant_id, Product.barcode == req.barcode.strip(),
        Product.id != product_id,
    ).first()
    if collision:
        raise HTTPException(status_code=409, detail="Barcode belongs to a different SKU")
    prod.barcode = req.barcode.strip()
    prod.name = req.name.strip()
    prod.unit_cost = req.unit_cost
    prod.unit_price = req.unit_price
    prod.vat_rate = req.vat_rate
    prod.image_url = req.image_url or generate_bing_thumbnail(prod.barcode, prod.name)
    db.commit()
    db.refresh(prod)
    return prod


@router.delete("/{product_id}")
def deactivate_product(product_id: str, tenant_id: str = Query(...),
                       db: Session = Depends(get_db)):
    prod = db.query(Product).filter(
        Product.id == product_id, Product.tenant_id == tenant_id,
    ).with_for_update().first()
    if not prod:
        raise HTTPException(status_code=404, detail="Product not found")
    if prod.warehouse_stock != 0:
        raise HTTPException(status_code=409, detail="Transfer or reconcile warehouse stock before deactivating SKU")
    allocations = db.query(VanStockAllowance).filter(
        VanStockAllowance.tenant_id == tenant_id,
        VanStockAllowance.product_id == product_id
    ).all()
    if any(a.current_quantity != 0 for a in allocations):
        raise HTTPException(status_code=409, detail="Reconcile van stock before deactivating SKU")
    prod.is_active = False
    db.commit()
    return {"status": "DEACTIVATED"}
