"""Transactional outbox intake for validated offline invoice replay.

Only invoice operations are enabled. Every accepted item goes through the same
server-side pricing, actor, tenant, van and stock checks as online checkout.
Unknown mutation types remain rejected rather than trusted from Android.
"""
from datetime import datetime
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException
from pydantic import ValidationError
from sqlalchemy.orm import Session

from backend.app.database import get_db
from backend.app.models.entities import User
from backend.app.routers.invoices import create_invoice
from backend.app.schemas.schemas import InvoiceCreate, OutboxBatchSyncRequest, OutboxBatchSyncResponse
from backend.app.security import get_authenticated_user

router = APIRouter(prefix="/sync", tags=["Synchronization & Outbox"])


@router.post("/outbox/flush", response_model=OutboxBatchSyncResponse)
def flush_device_outbox(
    req: OutboxBatchSyncRequest,
    db: Session = Depends(get_db),
    user: User = Depends(get_authenticated_user),
):
    if not req.device_id.strip() or len(req.items) > 100:
        raise HTTPException(status_code=422, detail="A device ID and batch of at most 100 items are required")
    if req.tenant_id != user.tenant_id:
        raise HTTPException(status_code=403, detail="Tenant boundary violation")

    acknowledged = []
    rejected = []
    errors = []
    for item in req.items:
        try:
            UUID(item.entity_id)  # stable, client-supplied idempotency identity
            if item.operation.upper() not in {"INSERT_INVOICE", "CREATE_INVOICE"}:
                raise HTTPException(status_code=422, detail="Unsupported outbox operation")
            payload = item.payload
            if not isinstance(payload, dict):
                raise HTTPException(status_code=422, detail="Malformed invoice payload")
            rows = []
            for line in payload["lines"]:
                rows.append({
                    "product_id": line["product_id"],
                    "product_name": line.get("product_name", ""),
                    "quantity": line.get("quantity", line.get("qty")),
                    "unit_price": line["unit_price"],
                    "vat_rate": line["vat_rate"],
                })
            request = InvoiceCreate.model_validate({
                "tenant_id": user.tenant_id,
                "client_operation_id": item.entity_id,
                "invoice_number": payload["invoice_number"],
                "customer_id": payload["customer_id"],
                "customer_name": payload.get("customer_name", ""),
                "van_id": req.van_id or payload.get("van_id"),
                "payment_mode": payload.get("payment_mode", payload.get("payment_type", "CASH")),
                "created_by": user.id,  # never trust client actor
                "lines": rows,
            })
            create_invoice(request, db=db, user=user)
            acknowledged.append(item.entity_id)
        except HTTPException as error:
            db.rollback()
            rejected.append(item.entity_id)
            errors.append(f"{item.entity_id}: {error.status_code} {error.detail}")
        except (KeyError, TypeError, ValueError, ValidationError) as error:
            db.rollback()
            rejected.append(item.entity_id)
            errors.append(f"{item.entity_id}: invalid payload ({type(error).__name__})")

    return OutboxBatchSyncResponse(
        success=not rejected,
        processed_count=len(acknowledged),
        rejected_count=len(rejected),
        acknowledged_ids=acknowledged,
        rejected_ids=rejected,
        errors=errors,
        server_timestamp=datetime.utcnow(),
    )
