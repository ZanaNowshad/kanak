"""Authenticated tenant boundary for the KANAK API.

Firebase ID tokens are validated against the configured Firebase project.
Membership and the active tenant are loaded from the authoritative database.
Request-supplied tenant identifiers must never choose the authenticated tenant.
"""
from typing import Optional

import firebase_admin
from firebase_admin import auth as firebase_auth
from fastapi import Depends, Header, HTTPException, Request
from sqlalchemy.orm import Session

from backend.app.config import settings
from backend.app.database import get_db
from backend.app.models.entities import User, Tenant


def enforce_tenant_match(authenticated_tenant: str, requested_tenant: Optional[str]) -> None:
    if requested_tenant is not None and requested_tenant != authenticated_tenant:
        raise HTTPException(status_code=403, detail="Tenant boundary violation")


def verify_firebase_identity(raw_token: str) -> dict:
    if not raw_token:
        raise HTTPException(status_code=401, detail="Missing Firebase ID token",
                            headers={"WWW-Authenticate": "Bearer"})
    try:
        firebase_admin.get_app()
    except ValueError:
        try:
            firebase_admin.initialize_app(options={"projectId": settings.FIREBASE_PROJECT_ID})
        except Exception as error:
            raise HTTPException(status_code=503, detail="Firebase verification is not configured") from error
    try:
        decoded = firebase_auth.verify_id_token(raw_token, check_revoked=True)
    except Exception as error:
        raise HTTPException(status_code=401, detail="Invalid or expired Firebase ID token",
                            headers={"WWW-Authenticate": "Bearer"}) from error
    email = decoded.get("email")
    subject = decoded.get("uid") or decoded.get("sub")
    if not isinstance(email, str) or not email.strip() or not subject or decoded.get("email_verified") is not True:
        raise HTTPException(status_code=401, detail="Verified account email is required")
    return decoded


def get_authenticated_user(
    authorization: str = Header(default=""),
    db: Session = Depends(get_db),
) -> User:
    kind, sep, token = authorization.strip().partition(" ")
    if not sep or kind.lower() != "bearer" or not token.strip():
        raise HTTPException(status_code=401, detail="Bearer authentication required",
                            headers={"WWW-Authenticate": "Bearer"})
    decoded = verify_firebase_identity(token.strip())
    email = decoded["email"].strip().lower()
    user = db.query(User).filter(User.email == email, User.is_active.is_(True)).first()
    if user is None:
        raise HTTPException(status_code=403, detail="No active company membership")
    tenant = db.query(Tenant).filter(Tenant.id == user.tenant_id, Tenant.is_active.is_(True)).first()
    if tenant is None:
        raise HTTPException(status_code=403, detail="Company is inactive or unavailable")
    return user


ADMIN_ROLES = frozenset({"SUPER_ADMIN", "COMPANY_ADMIN", "OWNER", "MANAGER", "ADMIN", "PLATFORM_ADMIN"})


async def enforce_tenant_scope(
    request: Request,
    user: User = Depends(get_authenticated_user),
) -> None:
    enforce_tenant_match(user.tenant_id, request.query_params.get("tenant_id"))
    enforce_tenant_match(user.tenant_id, request.path_params.get("tenant_id"))
    if request.method in {"POST", "PUT", "PATCH", "DELETE"}:
        if request.headers.get("content-type", "").startswith("application/json"):
            try:
                body = await request.json()
            except ValueError as error:
                raise HTTPException(status_code=400, detail="Invalid JSON body") from error
            if isinstance(body, dict):
                enforce_tenant_match(user.tenant_id, body.get("tenant_id"))
        protected_roots = (
            "/api/v1/products",
            "/api/v1/fleet",
            "/api/v1/inventory",
            "/api/v1/suppliers",
        )
        if request.url.path.startswith(protected_roots) and user.role.upper() not in ADMIN_ROLES:
            raise HTTPException(status_code=403, detail="Administrator permissions required")
