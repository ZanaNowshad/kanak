"""Fail-closed outbox endpoint until atomic, price-verified replay is implemented.

The former handler trusted arbitrary invoice totals and stock-ledger entries from
Android clients, committed incomplete invoices, and returned misleading success.
No financial record should be committed until the server can independently
validate membership, stock, pricing, idempotency and transaction integrity.
"""
from fastapi import APIRouter, HTTPException
from backend.app.schemas.schemas import OutboxBatchSyncRequest, OutboxBatchSyncResponse

router = APIRouter(prefix="/sync", tags=["Synchronization & Outbox"])

@router.post("/outbox/flush", response_model=OutboxBatchSyncResponse)
def flush_device_outbox(req: OutboxBatchSyncRequest):
    raise HTTPException(
        status_code=503,
        detail="Offline replay is temporarily unavailable until server-validated atomic transactions are implemented. Retain queued operations.",
        headers={"Retry-After": "300"},
    )
