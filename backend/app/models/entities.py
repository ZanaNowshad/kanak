import uuid
from datetime import datetime
from sqlalchemy import (
    Column, String, Integer, Numeric, Boolean, DateTime, ForeignKey, Index, Text, UniqueConstraint
)
from sqlalchemy.orm import relationship
from backend.app.database import Base

def generate_uuid():
    return str(uuid.uuid4())

class Tenant(Base):
    __tablename__ = "tenants"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    company_name = Column(String(255), nullable=False)
    cr_number = Column(String(64), nullable=True)  # Bahrain Commercial Registration Number
    trn_number = Column(String(64), nullable=True) # Bahrain NBR Tax Registration Number (15 digits)
    phone = Column(String(32), nullable=True)
    address = Column(Text, nullable=True)
    is_active = Column(Boolean, default=True, nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)

    users = relationship("User", back_populates="tenant", cascade="all, delete-orphan")
    vans = relationship("Van", back_populates="tenant", cascade="all, delete-orphan")
    products = relationship("Product", back_populates="tenant", cascade="all, delete-orphan")
    customers = relationship("Customer", back_populates="tenant", cascade="all, delete-orphan")
    invoices = relationship("Invoice", back_populates="tenant", cascade="all, delete-orphan")

class User(Base):
    __tablename__ = "users"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    tenant_id = Column(String(36), ForeignKey("tenants.id", ondelete="CASCADE"), nullable=False, index=True)
    email = Column(String(255), unique=True, nullable=False, index=True)
    full_name = Column(String(255), nullable=False)
    role = Column(String(32), default="VAN_REP", nullable=False) # SUPER_ADMIN, COMPANY_ADMIN, VAN_REP, ACCOUNTANT
    assigned_van_id = Column(String(36), ForeignKey("vans.id", ondelete="SET NULL"), nullable=True)
    is_active = Column(Boolean, default=True, nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)

    tenant = relationship("Tenant", back_populates="users")
    van = relationship("Van", foreign_keys=[assigned_van_id])

class Van(Base):
    __tablename__ = "vans"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    tenant_id = Column(String(36), ForeignKey("tenants.id", ondelete="CASCADE"), nullable=False, index=True)
    van_code = Column(String(32), nullable=False) # e.g. "VAN-01", "VAN-02"
    plate_number = Column(String(32), nullable=False)
    model = Column(String(128), nullable=True)
    driver_id = Column(String(36), ForeignKey("users.id", ondelete="SET NULL"), nullable=True)
    is_active = Column(Boolean, default=True, nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)

    tenant = relationship("Tenant", back_populates="vans")
    stock_allowances = relationship("VanStockAllowance", back_populates="van", cascade="all, delete-orphan")

    __table_args__ = (
        UniqueConstraint("tenant_id", "van_code", name="uq_tenant_van_code"),
    )

class Product(Base):
    __tablename__ = "products"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    tenant_id = Column(String(36), ForeignKey("tenants.id", ondelete="CASCADE"), nullable=False, index=True)
    barcode = Column(String(64), nullable=False, index=True)
    name = Column(String(255), nullable=False)
    category = Column(String(64), default="General", nullable=False)
    unit = Column(String(32), default="PCS", nullable=False)
    unit_cost = Column(Numeric(12, 3), default=0.000, nullable=False)
    unit_price = Column(Numeric(12, 3), nullable=False) # Standard VAT-inclusive selling price (3 decimals)
    vat_rate = Column(Numeric(5, 3), default=0.100, nullable=False) # 0.100 (10%) or 0.000
    warehouse_stock = Column(Numeric(12, 3), default=0.000, nullable=False)
    image_url = Column(Text, nullable=True)
    is_active = Column(Boolean, default=True, nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)

    tenant = relationship("Tenant", back_populates="products")
    van_allowances = relationship("VanStockAllowance", back_populates="product", cascade="all, delete-orphan")

    __table_args__ = (
        UniqueConstraint("tenant_id", "barcode", name="uq_tenant_product_barcode"),
    )

class VanStockAllowance(Base):
    __tablename__ = "van_stock_allowances"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    tenant_id = Column(String(36), ForeignKey("tenants.id", ondelete="CASCADE"), nullable=False, index=True)
    van_id = Column(String(36), ForeignKey("vans.id", ondelete="CASCADE"), nullable=False, index=True)
    product_id = Column(String(36), ForeignKey("products.id", ondelete="CASCADE"), nullable=False, index=True)
    assigned_quantity = Column(Numeric(12, 3), default=0.000, nullable=False)
    current_quantity = Column(Numeric(12, 3), default=0.000, nullable=False)
    last_replenished_at = Column(DateTime, default=datetime.utcnow, nullable=False)

    van = relationship("Van", back_populates="stock_allowances")
    product = relationship("Product", back_populates="van_allowances")

    __table_args__ = (
        UniqueConstraint("van_id", "product_id", name="uq_van_product_allowance"),
    )

class StockLedger(Base):
    """
    Append-only, immutable inventory ledger recording every stock movement.
    """
    __tablename__ = "stock_ledger"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    tenant_id = Column(String(36), ForeignKey("tenants.id", ondelete="CASCADE"), nullable=False, index=True)
    movement_id = Column(String(64), unique=True, nullable=False, index=True)
    timestamp = Column(DateTime, default=datetime.utcnow, nullable=False, index=True)
    movement_type = Column(String(32), nullable=False) # PURCHASE_INTAKE, VAN_LOAD, VAN_SALE, VAN_UNLOAD, AUDIT_ADJUSTMENT
    product_id = Column(String(36), ForeignKey("products.id", ondelete="CASCADE"), nullable=False, index=True)
    source_location = Column(String(64), nullable=False) # e.g. "WAREHOUSE_MAIN", "VAN:VAN-01", "SUPPLIER:SUP-001"
    destination_location = Column(String(64), nullable=False) # e.g. "VAN:VAN-01", "CUSTOMER:CUST-001", "WAREHOUSE_MAIN"
    quantity = Column(Numeric(12, 3), nullable=False)
    unit_cost = Column(Numeric(12, 3), default=0.000, nullable=False)
    total_value = Column(Numeric(12, 3), default=0.000, nullable=False)
    reference_document = Column(String(64), nullable=True) # Invoice number or PO number
    created_by = Column(String(64), nullable=False)

class Customer(Base):
    __tablename__ = "customers"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    tenant_id = Column(String(36), ForeignKey("tenants.id", ondelete="CASCADE"), nullable=False, index=True)
    name = Column(String(255), nullable=False)
    phone = Column(String(32), nullable=True)
    address = Column(Text, nullable=True)
    cr_number = Column(String(64), nullable=True)
    trn_number = Column(String(64), nullable=True) # Bahrain NBR 15-digit VAT number for tax invoices
    credit_limit = Column(Numeric(12, 3), default=0.000, nullable=False)
    outstanding_balance = Column(Numeric(12, 3), default=0.000, nullable=False)
    payment_terms_days = Column(Integer, default=0, nullable=False)
    is_active = Column(Boolean, default=True, nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)

    tenant = relationship("Tenant", back_populates="customers")

class Supplier(Base):
    __tablename__ = "suppliers"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    tenant_id = Column(String(36), ForeignKey("tenants.id", ondelete="CASCADE"), nullable=False, index=True)
    name = Column(String(255), nullable=False)
    contact_person = Column(String(255), nullable=True)
    phone = Column(String(32), nullable=True)
    email = Column(String(255), nullable=True)
    address = Column(Text, nullable=True)
    cr_number = Column(String(64), nullable=True)
    trn_number = Column(String(64), nullable=True)
    is_active = Column(Boolean, default=True, nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)

class Invoice(Base):
    __tablename__ = "invoices"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    tenant_id = Column(String(36), ForeignKey("tenants.id", ondelete="CASCADE"), nullable=False, index=True)
    invoice_number = Column(String(64), unique=True, nullable=False, index=True)
    van_id = Column(String(36), ForeignKey("vans.id", ondelete="SET NULL"), nullable=True, index=True)
    customer_id = Column(String(36), ForeignKey("customers.id", ondelete="CASCADE"), nullable=False, index=True)
    customer_name = Column(String(255), nullable=False)
    customer_trn = Column(String(64), nullable=True)
    date = Column(DateTime, default=datetime.utcnow, nullable=False, index=True)
    subtotal = Column(Numeric(12, 3), nullable=False)
    vat_total = Column(Numeric(12, 3), nullable=False)
    total = Column(Numeric(12, 3), nullable=False)
    payment_mode = Column(String(32), default="CASH", nullable=False) # CASH, BENEFIT_PAY, CARD, CREDIT
    status = Column(String(32), default="POSTED", nullable=False) # POSTED, VOIDED
    created_by = Column(String(64), nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)

    tenant = relationship("Tenant", back_populates="invoices")
    lines = relationship("InvoiceLine", back_populates="invoice", cascade="all, delete-orphan")

class InvoiceLine(Base):
    __tablename__ = "invoice_lines"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    invoice_id = Column(String(36), ForeignKey("invoices.id", ondelete="CASCADE"), nullable=False, index=True)
    product_id = Column(String(36), ForeignKey("products.id", ondelete="CASCADE"), nullable=False)
    product_name = Column(String(255), nullable=False)
    quantity = Column(Numeric(12, 3), nullable=False)
    unit_price = Column(Numeric(12, 3), nullable=False)
    vat_rate = Column(Numeric(5, 3), default=0.100, nullable=False)
    vat_amount = Column(Numeric(12, 3), nullable=False)
    line_total = Column(Numeric(12, 3), nullable=False)

    invoice = relationship("Invoice", back_populates="lines")

class SyncLog(Base):
    __tablename__ = "sync_logs"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    tenant_id = Column(String(36), ForeignKey("tenants.id", ondelete="CASCADE"), nullable=False, index=True)
    device_id = Column(String(128), nullable=False, index=True)
    van_id = Column(String(36), nullable=True)
    sync_type = Column(String(32), nullable=False) # OUTBOX_FLUSH, CATALOG_PULL, FULL_SYNC
    records_pushed = Column(Integer, default=0, nullable=False)
    records_pulled = Column(Integer, default=0, nullable=False)
    status = Column(String(32), default="SUCCESS", nullable=False)
    error_details = Column(Text, nullable=True)
    synced_at = Column(DateTime, default=datetime.utcnow, nullable=False, index=True)
