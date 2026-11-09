from fastapi import APIRouter, Depends, HTTPException, status, Header
from sqlalchemy.orm import Session
from pydantic import BaseModel
from typing import Optional
from backend.app.database import get_db
from backend.app.models.entities import User, Tenant, Van
from backend.app.security import verify_firebase_identity
from backend.app.schemas.schemas import TenantCreate
from sqlalchemy.exc import IntegrityError

router = APIRouter(prefix="/auth", tags=["Authentication"])

class TokenVerifyRequest(BaseModel):
    id_token: str
    email: Optional[str] = None
    display_name: Optional[str] = None

class CompanyRegistrationRequest(TenantCreate):
    initial_van_code: Optional[str] = None
    initial_van_plate: Optional[str] = None
    initial_van_name: Optional[str] = None


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
    initial_van_id: Optional[str] = None

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
    subject = claims.get("uid") or claims.get("sub")
    user = db.query(User).filter(
        User.firebase_uid == subject, User.email == email, User.is_active.is_(True)
    ).first()
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


@router.post("/companies", response_model=UserAuthResponse, status_code=201)
def register_company(
    req: CompanyRegistrationRequest,
    authorization: str = Header(default=""),
    db: Session = Depends(get_db),
):
    """Create one authoritative company and its owner from a verified Firebase UID.

    No client-provided role, tenant_id, email or arbitrary audit actor is accepted.
    Existing accounts must use the reviewed membership/invitation flow.
    """
    kind, sep, bearer = authorization.strip().partition(" ")
    if not sep or kind.lower() != "bearer" or not bearer.strip():
        raise HTTPException(status_code=401, detail="Bearer authentication required")
    claims = verify_firebase_identity(bearer.strip())
    email = claims["email"].strip().lower()
    subject = claims.get("uid") or claims.get("sub")
    if not req.company_name.strip():
        raise HTTPException(status_code=422, detail="Company name is required")
    existing_owner = db.query(User).filter(
        (User.email == email) | (User.firebase_uid == subject)
    ).first()
    if existing_owner:
        linked_tenant = db.query(Tenant).filter(Tenant.id == existing_owner.tenant_id).first()
        if (existing_owner.firebase_uid == subject and existing_owner.email == email
                and existing_owner.role == "COMPANY_ADMIN" and linked_tenant
                and linked_tenant.company_name == req.company_name.strip()
                and (linked_tenant.cr_number or "") == (req.cr_number or "").strip()):
            same_van = db.query(Van).filter(Van.tenant_id == linked_tenant.id,
                Van.van_code == (req.initial_van_code or "").strip()).first() if req.initial_van_code else None
            return UserAuthResponse(
                user_id=existing_owner.id, email=email, full_name=existing_owner.full_name,
                role=existing_owner.role, tenant_id=linked_tenant.id,
                company_name=linked_tenant.company_name,
                cr_number=linked_tenant.cr_number, trn_number=linked_tenant.trn_number,
                assigned_van_id=existing_owner.assigned_van_id,
                initial_van_id=same_van.id if same_van else None
            )
        raise HTTPException(status_code=409, detail="Account already belongs to another company")
    cr_number = (req.cr_number or "").strip()
    if cr_number and db.query(Tenant).filter(Tenant.cr_number == cr_number).first():
        raise HTTPException(status_code=409, detail="Commercial registration already registered")

    if req.initial_van_code and not (req.initial_van_plate or "").strip():
        raise HTTPException(status_code=422, detail="Initial van plate is required")

    tenant = Tenant(
        company_name=req.company_name.strip(),
        cr_number=cr_number or None,
        trn_number=(req.trn_number or "").strip() or None,
        phone=(req.phone or "").strip() or None,
        address=(req.address or "").strip() or None,
        is_active=True,
    )
    try:
        db.add(tenant)
        db.flush()
        owner = User(
            tenant_id=tenant.id,
            firebase_uid=subject,
            email=email,
            full_name=str(claims.get("name") or email.split("@")[0])[:255],
            role="COMPANY_ADMIN",
            is_active=True,
        )
        db.add(owner)
        initial_van = None
        if req.initial_van_code:
            initial_van = Van(
                tenant_id=tenant.id, van_code=req.initial_van_code.strip(),
                plate_number=req.initial_van_plate.strip(),
                model=(req.initial_van_name or "").strip() or None,
                is_active=True,
            )
            db.add(initial_van)
        db.commit()
        db.refresh(owner)
    except IntegrityError as error:
        db.rollback()
        raise HTTPException(status_code=409, detail="Company or account already registered") from error

    return UserAuthResponse(
        user_id=owner.id,
        email=owner.email,
        full_name=owner.full_name,
        role=owner.role,
        tenant_id=tenant.id,
        company_name=tenant.company_name,
        cr_number=tenant.cr_number,
        trn_number=tenant.trn_number,
        assigned_van_id=None,
        initial_van_id=initial_van.id if initial_van else None,
    )
