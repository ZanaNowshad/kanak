from fastapi import HTTPException

from backend.app.security import enforce_tenant_match


def test_same_tenant_is_allowed():
    assert enforce_tenant_match("tenant-a", "tenant-a") is None


def test_missing_client_tenant_is_not_authority():
    assert enforce_tenant_match("tenant-a", None) is None


def test_cross_tenant_is_forbidden():
    try:
        enforce_tenant_match("tenant-a", "tenant-b")
    except HTTPException as error:
        assert error.status_code == 403
    else:
        raise AssertionError("Cross-tenant request was not rejected")


from types import SimpleNamespace
from fastapi.testclient import TestClient
from backend.app.main import app
from backend.app.security import get_authenticated_user

def test_anonymous_cannot_read_company_products():
    response = TestClient(app).get("/api/v1/products?tenant_id=tenant-a")
    assert response.status_code == 401

def test_fake_token_does_not_bypass_authentication():
    response = TestClient(app).get(
        "/api/v1/customers?tenant_id=tenant-a",
        headers={"Authorization": "Bearer fabricated-token"},
    )
    assert response.status_code in {401, 503}

def test_unverified_auth_endpoint_denies_untrusted_identity():
    response = TestClient(app).post(
        "/api/v1/auth/verify",
        json={"id_token": "", "email": "admin@sample.test"},
    )
    assert response.status_code == 401

def test_cross_tenant_query_is_rejected_before_database_access():
    app.dependency_overrides[get_authenticated_user] = lambda: SimpleNamespace(
        tenant_id="tenant-a", role="COMPANY_ADMIN"
    )
    try:
        response = TestClient(app).get("/api/v1/products?tenant_id=tenant-b")
        assert response.status_code == 403
    finally:
        app.dependency_overrides.pop(get_authenticated_user, None)

def test_cross_tenant_body_is_rejected_before_write():
    app.dependency_overrides[get_authenticated_user] = lambda: SimpleNamespace(
        tenant_id="tenant-a", role="COMPANY_ADMIN"
    )
    try:
        response = TestClient(app).post(
            "/api/v1/products",
            json={"tenant_id": "tenant-b", "barcode": "12345678",
                  "name": "Rejected", "unit_price": "1.000"},
        )
        assert response.status_code == 403
    finally:
        app.dependency_overrides.pop(get_authenticated_user, None)

def test_salesperson_cannot_create_master_product():
    app.dependency_overrides[get_authenticated_user] = lambda: SimpleNamespace(
        tenant_id="tenant-a", role="VAN_REP"
    )
    try:
        response = TestClient(app).post(
            "/api/v1/products",
            json={"tenant_id": "tenant-a", "barcode": "12345678",
                  "name": "Denied", "unit_price": "1.000"},
        )
        assert response.status_code == 403
    finally:
        app.dependency_overrides.pop(get_authenticated_user, None)
