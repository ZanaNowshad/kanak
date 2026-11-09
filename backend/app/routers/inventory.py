from datetime import datetime
from decimal import Decimal
from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session
from typing import List, Optional
from pydantic import BaseModel
import uuid

from backend.app.database import get_db
from backend.app.models.entities import Product, StockLedger, User
from backend.app.security import get_authenticated_user

router = APIRouter(prefix="/inventory", tags=["Inventory & Ledger"])

class StockMovementResponse(BaseModel):
    id: str
    movement_id: str
    timestamp: datetime
    movement_type: str
    product_id: str
    source_location: str
    destination_location: str
    quantity: Decimal
    unit_cost: Decimal
    total_value: Decimal
    reference_document: Optional[str]
    created_by: str

    class Config:
        from_attributes = True

class StockAdjustmentRequest(BaseModel):
    tenant_id: str
    product_id: str
    new_quantity: Decimal
    reason: str
    performed_by: str

@router.get("/ledger", response_model=List[StockMovementResponse])
def get_stock_ledger(
    tenant_id: str = Query(...),
    product_id: Optional[str] = None,
    limit: int = 100,
    db: Session = Depends(get_db)
):
    q = db.query(StockLedger).filter(StockLedger.tenant_id == tenant_id)
    if product_id:
        q = q.filter(StockLedger.product_id == product_id)
    return q.order_by(StockLedger.timestamp.desc()).limit(limit).all()

@router.post("/adjust")
def adjust_warehouse_stock(req: StockAdjustmentRequest, db: Session = Depends(get_db),
                           user: User = Depends(get_authenticated_user)):
    if not req.new_quantity.is_finite() or req.new_quantity < 0 or req.new_quantity.as_tuple().exponent < -3:
        raise HTTPException(status_code=422, detail="Warehouse stock cannot be negative or exceed three decimal places")
    if not req.reason.strip():
        raise HTTPException(status_code=422, detail="Adjustment reason is required")
    prod = db.query(Product).filter(Product.id == req.product_id, Product.tenant_id == req.tenant_id).with_for_update().first()
    if not prod:
        raise HTTPException(status_code=404, detail="Product not found")

    old_stock = prod.warehouse_stock
    difference = req.new_quantity - old_stock
    prod.warehouse_stock = req.new_quantity

    movement = StockLedger(
        id=str(uuid.uuid4()),
        tenant_id=req.tenant_id,
        movement_id=f"MOV-ADJ-{uuid.uuid4()}",
        timestamp=datetime.utcnow(),
        movement_type="AUDIT_ADJUSTMENT",
        product_id=prod.id,
        source_location="WAREHOUSE_MAIN",
        destination_location="AUDIT_CORRECTION",
        quantity=abs(difference),
        unit_cost=prod.unit_cost,
        total_value=abs(difference) * prod.unit_price,
        reference_document=f"REASON: {req.reason}",
        created_by=user.id
    )
    db.add(movement)
    db.commit()

    return {
        "status": "SUCCESS",
        "product_id": prod.id,
        "old_quantity": old_stock,
        "new_quantity": req.new_quantity,
        "difference": difference
    }
