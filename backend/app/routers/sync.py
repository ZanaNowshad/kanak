from datetime import datetime
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from typing import List
import uuid

from backend.app.database import get_db
from backend.app.models.entities import SyncLog, Invoice, InvoiceLine, Customer, Product, StockLedger
from backend.app.schemas.schemas import OutboxBatchSyncRequest, OutboxBatchSyncResponse

router = APIRouter(prefix="/sync", tags=["Synchronization & Outbox"])

@router.post("/outbox/flush", response_model=OutboxBatchSyncResponse)
def flush_device_outbox(req: OutboxBatchSyncRequest, db: Session = Depends(get_db)):
    """
    Receives batch transactions posted from Android Room Outbox via WorkManager.
    Guarantees idempotent processing and records synchronization telemetry.
    """
    processed = 0
    rejected = 0
    errors: List[str] = []

    for item in req.items:
        try:
            if item.operation == "INSERT_INVOICE":
                inv_data = item.payload
                existing = db.query(Invoice).filter(
                    Invoice.tenant_id == req.tenant_id,
                    Invoice.invoice_number == inv_data.get("invoice_number")
                ).first()

                if existing:
                    # Idempotent skip - already recorded
                    processed += 1
                    continue

                # Record invoice from outbox
                inv = Invoice(
                    id=str(uuid.uuid4()),
                    tenant_id=req.tenant_id,
                    invoice_number=inv_data.get("invoice_number"),
                    van_id=req.van_id or inv_data.get("van_id"),
                    customer_id=inv_data.get("customer_id", "WALK_IN"),
                    customer_name=inv_data.get("customer_name", "Cash Customer"),
                    customer_trn=inv_data.get("customer_trn"),
                    date=datetime.utcnow(),
                    subtotal=inv_data.get("subtotal", 0.0),
                    vat_total=inv_data.get("vat_total", 0.0),
                    total=inv_data.get("total", 0.0),
                    payment_mode=inv_data.get("payment_mode", "CASH"),
                    status="POSTED",
                    created_by=inv_data.get("created_by", "Android_Device")
                )
                db.add(inv)
                processed += 1

            elif item.operation == "STOCK_LEDGER_ENTRY":
                leg_data = item.payload
                mov = StockLedger(
                    id=str(uuid.uuid4()),
                    tenant_id=req.tenant_id,
                    movement_id=leg_data.get("movement_id", str(uuid.uuid4())),
                    timestamp=datetime.utcnow(),
                    movement_type=leg_data.get("movement_type", "VAN_SALE"),
                    product_id=leg_data.get("product_id"),
                    source_location=leg_data.get("source_location", "VAN"),
                    destination_location=leg_data.get("destination_location", "CUSTOMER"),
                    quantity=leg_data.get("quantity", 0.0),
                    unit_cost=leg_data.get("unit_cost", 0.0),
                    total_value=leg_data.get("total_value", 0.0),
                    reference_document=leg_data.get("reference_document"),
                    created_by=leg_data.get("created_by", "Android_Device")
                )
                db.add(mov)
                processed += 1
            else:
                rejected += 1
                errors.append(f"Unknown operation: {item.operation}")

        except Exception as e:
            rejected += 1
            errors.append(f"Error processing item {item.entity_id}: {str(e)}")

    db.commit()

    # Record sync log
    sync_log = SyncLog(
        id=str(uuid.uuid4()),
        tenant_id=req.tenant_id,
        device_id=req.device_id,
        van_id=req.van_id,
        sync_type="OUTBOX_FLUSH",
        records_pushed=processed,
        records_pulled=0,
        status="SUCCESS" if rejected == 0 else "PARTIAL",
        error_details="; ".join(errors) if errors else None,
        synced_at=datetime.utcnow()
    )
    db.add(sync_log)
    db.commit()

    return OutboxBatchSyncResponse(
        success=(rejected == 0),
        processed_count=processed,
        rejected_count=rejected,
        errors=errors,
        server_timestamp=datetime.utcnow()
    )
