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
