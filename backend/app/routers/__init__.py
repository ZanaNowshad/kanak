from backend.app.routers.auth import router as auth_router
from backend.app.routers.products import router as products_router
from backend.app.routers.invoices import router as invoices_router
from backend.app.routers.fleet import router as fleet_router
from backend.app.routers.customers import router as customers_router
from backend.app.routers.suppliers import router as suppliers_router
from backend.app.routers.inventory import router as inventory_router
from backend.app.routers.sync import router as sync_router
from backend.app.routers.admin import router as admin_router

__all__ = [
    "auth_router",
    "products_router",
    "invoices_router",
    "fleet_router",
    "customers_router",
    "suppliers_router",
    "inventory_router",
    "sync_router",
    "admin_router"
]
