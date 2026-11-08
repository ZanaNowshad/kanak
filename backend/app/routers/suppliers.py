from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session
from typing import List, Optional
from backend.app.database import get_db
from backend.app.models.entities import Supplier
from backend.app.schemas.schemas import SupplierCreate, SupplierResponse

router = APIRouter(prefix="/suppliers", tags=["Suppliers"])

@router.get("", response_model=List[SupplierResponse])
def get_suppliers(
    tenant_id: str = Query(...),
    search: Optional[str] = None,
    db: Session = Depends(get_db)
):
    q = db.query(Supplier).filter(Supplier.tenant_id == tenant_id, Supplier.is_active == True)
    if search:
        search_filter = f"%{search}%"
        q = q.filter((Supplier.name.ilike(search_filter)) | (Supplier.phone.ilike(search_filter)))
    return q.order_by(Supplier.name).all()

@router.post("", response_model=SupplierResponse)
def create_supplier(req: SupplierCreate, db: Session = Depends(get_db)):
    sup = Supplier(
        tenant_id=req.tenant_id,
        name=req.name.strip(),
        contact_person=req.contact_person,
        phone=req.phone,
        email=req.email,
        address=req.address,
        cr_number=req.cr_number,
        trn_number=req.trn_number,
        is_active=True
    )
    db.add(sup)
    db.commit()
    db.refresh(sup)
    return sup
