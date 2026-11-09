from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session
from typing import List, Optional
from backend.app.database import get_db
from backend.app.models.entities import Customer, User
from backend.app.security import get_authenticated_user
from backend.app.schemas.schemas import CustomerCreate, CustomerResponse

router = APIRouter(prefix="/customers", tags=["Customers"])

@router.get("", response_model=List[CustomerResponse])
def get_customers(
    tenant_id: str = Query(...),
    search: Optional[str] = None,
    db: Session = Depends(get_db)
):
    q = db.query(Customer).filter(Customer.tenant_id == tenant_id, Customer.is_active == True)
    if search:
        search_filter = f"%{search}%"
        q = q.filter((Customer.name.ilike(search_filter)) | (Customer.phone.ilike(search_filter)))
    return q.order_by(Customer.name).all()

@router.post("", response_model=CustomerResponse)
def create_customer(req: CustomerCreate, db: Session = Depends(get_db)):
    cust = Customer(
        tenant_id=req.tenant_id,
        name=req.name.strip(),
        phone=req.phone,
        address=req.address,
        cr_number=req.cr_number,
        trn_number=req.trn_number,
        credit_limit=req.credit_limit,
        outstanding_balance=0.000,
        payment_terms_days=req.payment_terms_days,
        is_active=True
    )
    db.add(cust)
    db.commit()
    db.refresh(cust)
    return cust

@router.get("/{customer_id}", response_model=CustomerResponse)
def get_customer(customer_id: str, db: Session = Depends(get_db),
                 user: User = Depends(get_authenticated_user)):
    cust = db.query(Customer).filter(Customer.id == customer_id,
                                      Customer.tenant_id == user.tenant_id).first()
    if not cust:
        raise HTTPException(status_code=404, detail="Customer not found")
    return cust
