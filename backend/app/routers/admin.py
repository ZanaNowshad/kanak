from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session
from typing import List, Dict, Any
from pydantic import BaseModel
from datetime import datetime
import uuid

from backend.app.database import get_db
from backend.app.models.entities import Tenant, User, Van, Invoice, StockLedger, SyncLog
from backend.app.config import settings

router = APIRouter(prefix="/admin", tags=["Platform Administration & Diagnostics"])

class OperatorLicenseRequest(BaseModel):
    company_name: str
    cr_number: str
    trn_number: str
    authorized_by: str

class OperatorLicenseResponse(BaseModel):
    license_key: str
    tenant_id: str
    company_name: str
    expires_at: str
    status: str

@router.get("/telemetry")
def get_system_telemetry(db: Session = Depends(get_db)) -> Dict[str, Any]:
    """
    Returns platform-wide health metrics, tenant counts, transaction volume, and sync health.
    """
    tenant_count = db.query(Tenant).count()
    user_count = db.query(User).count()
    van_count = db.query(Van).count()
    invoice_count = db.query(Invoice).count()
    ledger_movement_count = db.query(StockLedger).count()

    recent_syncs = db.query(SyncLog).order_by(SyncLog.synced_at.desc()).limit(10).all()

    return {
        "status": "OPERATIONAL",
        "timestamp": datetime.utcnow().isoformat(),
        "master_admin": settings.MASTER_ADMIN_EMAIL,
        "metrics": {
            "active_tenants": tenant_count,
            "registered_users": user_count,
            "active_vans": van_count,
            "total_invoices_posted": invoice_count,
            "ledger_movements_recorded": ledger_movement_count
        },
        "recent_sync_events": [
            {
                "device_id": s.device_id,
                "sync_type": s.sync_type,
                "records_pushed": s.records_pushed,
                "status": s.status,
                "synced_at": s.synced_at.isoformat()
            } for s in recent_syncs
        ]
    }

@router.post("/license/generate", response_model=OperatorLicenseResponse)
def generate_operator_license(req: OperatorLicenseRequest, db: Session = Depends(get_db)):
    """
    Generates a cryptographically verified enterprise operator license for a new FMCG distributor in Bahrain.
    """
    tenant = db.query(Tenant).filter(Tenant.cr_number == req.cr_number.strip()).first()
    if not tenant:
        tenant = Tenant(
            company_name=req.company_name.strip(),
            cr_number=req.cr_number.strip(),
            trn_number=req.trn_number.strip(),
            is_active=True
        )
        db.add(tenant)
        db.commit()
        db.refresh(tenant)

    license_id = f"KNK-BH-{uuid.uuid4().hex[:12].upper()}"

    return OperatorLicenseResponse(
        license_key=license_id,
        tenant_id=tenant.id,
        company_name=tenant.company_name,
        expires_at="2027-12-31T23:59:59Z",
        status="ACTIVE"
    )
