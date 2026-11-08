from datetime import datetime
from decimal import Decimal
from typing import List, Optional
from pydantic import BaseModel, Field, field_validator

# --- Financial Helper ---
def quantize_bhd(val: Decimal) -> Decimal:
    return val.quantize(Decimal("0.001"))

# --- Tenant Schemas ---
class TenantBase(BaseModel):
    company_name: str
    cr_number: Optional[str] = None
    trn_number: Optional[str] = None
    phone: Optional[str] = None
    address: Optional[str] = None

class TenantCreate(TenantBase):
    pass

class TenantResponse(TenantBase):
    id: str
    is_active: bool
    created_at: datetime

    class Config:
        from_attributes = True

# --- User Schemas ---
class UserBase(BaseModel):
    email: str
    full_name: str
    role: str = "VAN_REP"
    assigned_van_id: Optional[str] = None

class UserCreate(UserBase):
    tenant_id: str

class UserResponse(UserBase):
    id: str
    tenant_id: str
    is_active: bool
    created_at: datetime

    class Config:
        from_attributes = True

# --- Van Schemas ---
class VanBase(BaseModel):
    van_code: str
    plate_number: str
    model: Optional[str] = None
    driver_id: Optional[str] = None

class VanCreate(VanBase):
    tenant_id: str

class VanResponse(VanBase):
    id: str
    tenant_id: str
    is_active: bool
    created_at: datetime

    class Config:
        from_attributes = True

# --- Product Schemas ---
class ProductBase(BaseModel):
    barcode: str
    name: str
    category: str = "General"
    unit: str = "PCS"
    unit_cost: Decimal = Decimal("0.000")
    unit_price: Decimal  # VAT-inclusive selling price (BHD)
    vat_rate: Decimal = Decimal("0.100")
    image_url: Optional[str] = None

    @field_validator("unit_price", "unit_cost", "vat_rate", mode="before")
    def validate_decimal_3(cls, v):
        return quantize_bhd(Decimal(str(v)))

class ProductCreate(ProductBase):
    tenant_id: str
    warehouse_stock: Decimal = Decimal("0.000")

class ProductResponse(ProductBase):
    id: str
    tenant_id: str
    warehouse_stock: Decimal
    is_active: bool
    created_at: datetime

    class Config:
        from_attributes = True

# --- Customer Schemas ---
class CustomerBase(BaseModel):
    name: str
    phone: Optional[str] = None
    address: Optional[str] = None
    cr_number: Optional[str] = None
    trn_number: Optional[str] = None
    credit_limit: Decimal = Decimal("0.000")
    payment_terms_days: int = 0

    @field_validator("credit_limit", mode="before")
    def validate_credit(cls, v):
        return quantize_bhd(Decimal(str(v)))

class CustomerCreate(CustomerBase):
    tenant_id: str

class CustomerResponse(CustomerBase):
    id: str
    tenant_id: str
    outstanding_balance: Decimal
    is_active: bool
    created_at: datetime

    class Config:
        from_attributes = True

# --- Supplier Schemas ---
class SupplierBase(BaseModel):
    name: str
    contact_person: Optional[str] = None
    phone: Optional[str] = None
    email: Optional[str] = None
    address: Optional[str] = None
    cr_number: Optional[str] = None
    trn_number: Optional[str] = None

class SupplierCreate(SupplierBase):
    tenant_id: str

class SupplierResponse(SupplierBase):
    id: str
    tenant_id: str
    is_active: bool
    created_at: datetime

    class Config:
        from_attributes = True

# --- Invoice Line Schemas ---
class InvoiceLineCreate(BaseModel):
    product_id: str
    product_name: str
    quantity: Decimal
    unit_price: Decimal  # VAT-inclusive price
    vat_rate: Decimal = Decimal("0.100")

    @field_validator("quantity", "unit_price", "vat_rate", mode="before")
    def validate_decimals(cls, v):
        return quantize_bhd(Decimal(str(v)))

class InvoiceLineResponse(BaseModel):
    id: str
    product_id: str
    product_name: str
    quantity: Decimal
    unit_price: Decimal
    vat_rate: Decimal
    vat_amount: Decimal
    line_total: Decimal

    class Config:
        from_attributes = True

# --- Invoice Schemas ---
class InvoiceCreate(BaseModel):
    tenant_id: str
    invoice_number: str
    van_id: Optional[str] = None
    customer_id: str
    customer_name: str
    customer_trn: Optional[str] = None
    payment_mode: str = "CASH" # CASH, BENEFIT_PAY, CARD, CREDIT
    lines: List[InvoiceLineCreate]
    created_by: str

class InvoiceResponse(BaseModel):
    id: str
    tenant_id: str
    invoice_number: str
    van_id: Optional[str]
    customer_id: str
    customer_name: str
    customer_trn: Optional[str]
    date: datetime
    subtotal: Decimal
    vat_total: Decimal
    total: Decimal
    payment_mode: str
    status: str
    created_by: str
    created_at: datetime
    lines: List[InvoiceLineResponse]

    class Config:
        from_attributes = True

# --- Van Stock Transfer & Allowance Schemas ---
class VanStockTransferRequest(BaseModel):
    tenant_id: str
    van_id: str
    product_id: str
    quantity: Decimal
    transfer_type: str  # LOAD_TO_VAN, UNLOAD_TO_WAREHOUSE
    performed_by: str

# --- Sync Schemas ---
class OutboxItem(BaseModel):
    operation: str  # INSERT_INVOICE, UPDATE_CUSTOMER, ADJUST_STOCK
    entity_id: str
    payload: dict
    timestamp: str

class OutboxBatchSyncRequest(BaseModel):
    tenant_id: str
    device_id: str
    van_id: Optional[str] = None
    items: List[OutboxItem]

class OutboxBatchSyncResponse(BaseModel):
    success: bool
    processed_count: int
    rejected_count: int
    errors: List[str] = []
    server_timestamp: datetime
