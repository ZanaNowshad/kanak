"""KANAK release-gate adversarial API tests. Stock and role regressions must pass.

These tests are intentionally isolated to SQLite; they never contact customer data
or a live server. All tests protect production invariants.
"""
from decimal import Decimal

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from backend.app.database import Base, get_db
from backend.app.main import app
from backend.app.models.entities import (
    Customer, Product, Tenant, User, Van, VanStockAllowance,
)
from backend.app.security import get_authenticated_user


@pytest.fixture
def retail_client():
    engine = create_engine(
        "sqlite+pysqlite://",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    Base.metadata.create_all(engine)
    session_factory = sessionmaker(bind=engine, expire_on_commit=False)
    db = session_factory()
    tenant = Tenant(id="tenant-a", company_name="Company A")
    owner_van = Van(id="van-own", tenant_id="tenant-a", van_code="OWN",
                    plate_number="BHR-01")
    other_van = Van(id="van-other", tenant_id="tenant-a", van_code="OTHER",
                    plate_number="BHR-02")
    user = User(id="salesperson-a", tenant_id="tenant-a", email="driver@example.test",
                full_name="QA Van Driver", role="VAN_REP", assigned_van_id="van-own")
    product = Product(id="prod-a", tenant_id="tenant-a", barcode="QA-001",
                      name="Test Product", unit_price=Decimal("1.100"),
                      unit_cost=Decimal("0.750"), vat_rate=Decimal("0.100"),
                      warehouse_stock=Decimal("10.000"))
    customer = Customer(id="cust-a", tenant_id="tenant-a", name="Test Customer",
                        credit_limit=Decimal("25.000"), outstanding_balance=Decimal("0.000"))
    other_stock = VanStockAllowance(id="allowance-other", tenant_id="tenant-a",
                                     van_id="van-other", product_id="prod-a",
                                     assigned_quantity=Decimal("5.000"),
                                     current_quantity=Decimal("5.000"))
    db.add_all([tenant, owner_van, other_van, user, product, customer, other_stock])
    db.commit()

    def override_db():
        yield db

    app.dependency_overrides[get_db] = override_db
    app.dependency_overrides[get_authenticated_user] = lambda: user
    try:
        with TestClient(app) as client:
            yield client, db
    finally:
        app.dependency_overrides.pop(get_db, None)
        app.dependency_overrides.pop(get_authenticated_user, None)
        db.close()
        Base.metadata.drop_all(engine)
        engine.dispose()


def test_negative_van_load_must_be_rejected(retail_client):
    client, db = retail_client
    administrator = db.query(User).filter(User.id == "salesperson-a").one()
    administrator.role = "COMPANY_ADMIN"
    db.commit()
    resp = client.post("/api/v1/fleet/vans/transfer", json={
        "tenant_id": "tenant-a", "van_id": "van-own", "product_id": "prod-a",
        "quantity": "-2.000", "transfer_type": "LOAD_TO_VAN",
        "performed_by": "QA"
    })
    assert resp.status_code in (400, 422), resp.text
    assert db.query(Product).filter(Product.id == "prod-a").one().warehouse_stock == Decimal("10.000")


def test_negative_stock_adjustment_must_be_rejected(retail_client):
    client, db = retail_client
    administrator = db.query(User).filter(User.id == "salesperson-a").one()
    administrator.role = "COMPANY_ADMIN"
    db.commit()
    resp = client.post("/api/v1/inventory/adjust", json={
        "tenant_id": "tenant-a", "product_id": "prod-a",
        "new_quantity": "-5.000", "reason": "QA negative inventory",
        "performed_by": "QA"
    })
    assert resp.status_code in (400, 422), resp.text


def test_salesperson_cannot_sell_from_unassigned_van(retail_client):
    client, db = retail_client
    resp = client.post("/api/v1/invoices", json={
        "tenant_id": "tenant-a", "invoice_number": "QA-INV-001",
        "van_id": "van-other", "customer_id": "cust-a",
        "customer_name": "Test Customer", "payment_mode": "CASH",
        "created_by": "forged_actor",
        "lines": [{"product_id": "prod-a", "product_name": "Test Product",
                   "quantity": "1.000", "unit_price": "1.100",
                   "vat_rate": "0.100"}],
    })
    assert resp.status_code == 403, resp.text


def test_anonymous_transfer_denied():
    with TestClient(app) as client:
        resp = client.post("/api/v1/fleet/vans/transfer", json={
            "tenant_id": "tenant-a", "van_id": "van-own", "product_id": "prod-a",
            "quantity": "2.000", "transfer_type": "LOAD_TO_VAN",
            "performed_by": "QA",
        })
    assert resp.status_code == 401


def _outbox_request(operation_id, *, qty="1.000", van_id="van-own", operation="INSERT_INVOICE"):
    return {
        "tenant_id": "tenant-a",
        "device_id": "qa-android-device",
        "van_id": van_id,
        "items": [{
            "operation": operation,
            "entity_id": operation_id,
            "timestamp": "2026-11-09T10:00:00+03:00",
            "payload": {
                "invoice_id": operation_id,
                "invoice_number": "INV-QA-001",
                "customer_id": "cust-a",
                "customer_name": "Test Customer",
                "payment_type": "CASH",
                "van_id": van_id,
                "lines": [{
                    "product_id": "prod-a",
                    "product_name": "Test Product",
                    "qty": qty,
                    "unit_price": "1.100",
                    "vat_rate": "0.100",
                }],
            },
        }],
    }


def test_outbox_replay_is_idempotent_and_stock_conserved(retail_client):
    from uuid import uuid4
    from backend.app.models.entities import Invoice, StockLedger
    client, db = retail_client
    db.add(VanStockAllowance(
        tenant_id="tenant-a", van_id="van-own", product_id="prod-a",
        assigned_quantity=Decimal("5.000"), current_quantity=Decimal("5.000")
    ))
    db.commit()
    operation_id = str(uuid4())
    req = _outbox_request(operation_id)
    first = client.post("/api/v1/sync/outbox/flush", json=req)
    assert first.status_code == 200, first.text
    assert first.json()["acknowledged_ids"] == [operation_id]
    assert db.query(Invoice).count() == 1
    remaining = db.query(VanStockAllowance).filter(VanStockAllowance.van_id == "van-own").one()
    assert remaining.current_quantity == Decimal("4.000")

    replay = client.post("/api/v1/sync/outbox/flush", json=req)
    assert replay.status_code == 200, replay.text
    assert replay.json()["acknowledged_ids"] == [operation_id]
    db.refresh(remaining)
    assert remaining.current_quantity == Decimal("4.000")
    assert db.query(Invoice).count() == 1
    assert db.query(StockLedger).filter(StockLedger.movement_type == "VAN_SALE").count() == 1

    changed = client.post(
        "/api/v1/sync/outbox/flush",
        json=_outbox_request(operation_id, qty="2.000"),
    )
    assert changed.status_code == 200
    assert changed.json()["rejected_ids"] == [operation_id]
    assert db.query(Invoice).count() == 1
    db.refresh(remaining)
    assert remaining.current_quantity == Decimal("4.000")


def test_outbox_rejects_unassigned_van_and_unsupported_mutations(retail_client):
    from uuid import uuid4
    from backend.app.models.entities import Invoice
    client, db = retail_client
    alien = str(uuid4())
    resp = client.post(
        "/api/v1/sync/outbox/flush", json=_outbox_request(alien, van_id="van-other")
    )
    assert resp.status_code == 200, resp.text
    assert resp.json()["rejected_ids"] == [alien]
    assert db.query(Invoice).count() == 0

    unsupported = str(uuid4())
    req = _outbox_request(unsupported, operation="ADJUST_STOCK")
    response = client.post("/api/v1/sync/outbox/flush", json=req)
    assert response.status_code == 200
    assert response.json()["rejected_ids"] == [unsupported]
    assert db.query(Invoice).count() == 0


def test_anonymous_outbox_cannot_submit_financial_records():
    from uuid import uuid4
    request = _outbox_request(str(uuid4()))
    with TestClient(app) as client:
        response = client.post("/api/v1/sync/outbox/flush", json=request)
    assert response.status_code == 401
