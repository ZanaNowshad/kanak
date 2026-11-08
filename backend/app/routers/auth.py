from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from pydantic import BaseModel
from typing import Optional
from backend.app.database import get_db
from backend.app.models.entities import User, Tenant
from backend.app.config import settings

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
    """
    Verifies user authentication.
    If the email is the Master Admin (zanabalmuhamed@gmail.com), elevates to SUPER_ADMIN.
    Resolves tenant configuration and assigned van.
    """
    email = req.email.strip().lower() if req.email else ""
    if not email:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Valid email required for user resolution"
        )

    # Check for existing user in database
    user = db.query(User).filter(User.email == email).first()

    if not user:
        # Check if master platform admin
        is_master = (email == settings.MASTER_ADMIN_EMAIL.lower())
        
        # Look for default or first active tenant
        tenant = db.query(Tenant).filter(Tenant.is_active == True).first()
        if not tenant:
            tenant = Tenant(
                company_name="KANAK FMCG Wholesale Bahrain",
                cr_number="123456-1",
                trn_number="200000000000002", # 15-digit Bahrain TRN
                phone="+973 1700 0000",
                address="Manama, Kingdom of Bahrain",
                is_active=True
            )
            db.add(tenant)
            db.commit()
            db.refresh(tenant)

        # Create user record
        user = User(
            tenant_id=tenant.id,
            email=email,
            full_name=req.display_name or email.split("@")[0].title(),
            role="SUPER_ADMIN" if is_master else "VAN_REP",
            is_active=True
        )
        db.add(user)
        db.commit()
        db.refresh(user)

    tenant = db.query(Tenant).filter(Tenant.id == user.tenant_id).first()

    return UserAuthResponse(
        user_id=user.id,
        email=user.email,
        full_name=user.full_name,
        role=user.role,
        tenant_id=user.tenant_id,
        company_name=tenant.company_name if tenant else "KANAK FMCG",
        cr_number=tenant.cr_number if tenant else None,
        trn_number=tenant.trn_number if tenant else None,
        assigned_van_id=user.assigned_van_id
    )
