from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from pydantic import BaseModel
from typing import Optional
from backend.app.database import get_db
from backend.app.models.entities import User, Tenant
from backend.app.security import verify_firebase_identity

router = APIRouter(prefix="/auth", tags=["Authentication"])

class TokenVerifyRequest(BaseModel):
    id_token: str
    email: Optional[str] = None
    display_name: Optional[str] = None

class UserAuthResponse(BaseModel):
    user_id: str
    email: str
    full_name: str
    role: str
    tenant_id: str
    company_name: str
    cr_number: Optional[str] = None
    trn_number: Optional[str] = None
    assigned_van_id: Optional[str] = None

@router.post("/verify", response_model=UserAuthResponse)
def verify_user_and_tenant(req: TokenVerifyRequest, db: Session = Depends(get_db)):
    """Resolve an already-provisioned membership from a verified Firebase identity.

    The endpoint deliberately does NOT auto-join a tenant, grant admin privileges by
    email, or manufacture companies and tax registration numbers.
    """
    claims = verify_firebase_identity(req.id_token)
    email = claims["email"].strip().lower()
    if req.email and req.email.strip().lower() != email:
        raise HTTPException(status_code=403, detail="Requested email differs from verified identity")
    user = db.query(User).filter(User.email == email, User.is_active.is_(True)).first()
    if user is None:
        raise HTTPException(status_code=403, detail="An administrator must approve company membership")
    tenant = db.query(Tenant).filter(Tenant.id == user.tenant_id, Tenant.is_active.is_(True)).first()
    if tenant is None:
        raise HTTPException(status_code=403, detail="Company membership is inactive")

    return UserAuthResponse(
        user_id=user.id,
        email=user.email,
        full_name=user.full_name,
        role=user.role,
        tenant_id=user.tenant_id,
        company_name=tenant.company_name,
        cr_number=tenant.cr_number,
        trn_number=tenant.trn_number,
        assigned_van_id=user.assigned_van_id,
    )
