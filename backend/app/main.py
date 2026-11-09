from fastapi import FastAPI, Depends
from backend.app.security import enforce_tenant_scope
from fastapi.middleware.cors import CORSMiddleware
from backend.app.config import settings
from backend.app.routers import (
    auth_router,
    products_router,
    invoices_router,
    fleet_router,
    customers_router,
    suppliers_router,
    inventory_router,
    sync_router
)

# Database schema must be migrated through Alembic, not auto-created at import.

app = FastAPI(
    title=settings.APP_NAME,
    version="1.0.0",
    description="Authoritative cloud backend for KANAK FMCG Wholesale & Multi-Van Distribution (Kingdom of Bahrain)",
    docs_url="/docs",
    redoc_url="/redoc"
)

# Cross-Origin Resource Sharing
app.add_middleware(
    CORSMiddleware,
    allow_origins=[x.strip() for x in settings.CORS_ALLOW_ORIGINS.split(",") if x.strip()],
    allow_credentials=bool(settings.CORS_ALLOW_ORIGINS.strip()),
    allow_methods=["*"],
    allow_headers=["*"],
)

# Register routers
app.include_router(auth_router, prefix=settings.API_V1_STR)
app.include_router(products_router, prefix=settings.API_V1_STR,
                   dependencies=[Depends(enforce_tenant_scope)])
app.include_router(invoices_router, prefix=settings.API_V1_STR,
                   dependencies=[Depends(enforce_tenant_scope)])
app.include_router(fleet_router, prefix=settings.API_V1_STR,
                   dependencies=[Depends(enforce_tenant_scope)])
app.include_router(customers_router, prefix=settings.API_V1_STR,
                   dependencies=[Depends(enforce_tenant_scope)])
app.include_router(suppliers_router, prefix=settings.API_V1_STR,
                   dependencies=[Depends(enforce_tenant_scope)])
app.include_router(inventory_router, prefix=settings.API_V1_STR,
                   dependencies=[Depends(enforce_tenant_scope)])
app.include_router(sync_router, prefix=settings.API_V1_STR,
                   dependencies=[Depends(enforce_tenant_scope)])

@app.get("/health", tags=["Health"])
def health_check():
    return {
        "status": "HEALTHY",
        "service": settings.APP_NAME,
        "market": "Bahrain (BHD 10% VAT)",
        "version": "1.0.0"
    }

@app.get("/", tags=["Root"])
def root():
    return {
        "app": settings.APP_NAME,
        "docs": "/docs",
        "health": "/health",
        "api_v1": settings.API_V1_STR
    }
