from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session
from typing import List, Optional
import urllib.parse
from backend.app.database import get_db
from backend.app.models.entities import Product
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
