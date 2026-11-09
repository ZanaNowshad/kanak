import os
from pydantic_settings import BaseSettings
from decimal import Decimal

class Settings(BaseSettings):
    APP_NAME: str = "KANAK Authoritative Backend"
    API_V1_STR: str = "/api/v1"
    SECRET_KEY: str = os.getenv("JWT_SECRET_KEY", "")
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 60 * 24 * 7  # 7 days

    # PostgreSQL Database URL
    DATABASE_URL: str = os.getenv("DATABASE_URL", "postgresql://kanak_user:kanak_pass@localhost:5432/kanak_db")

    # Bahrain Financial Constants
    BAHRAIN_VAT_RATE: Decimal = Decimal("0.100")
    CURRENCY_CODE: str = "BHD"
    CURRENCY_DECIMALS: int = 3

    # Firebase Admin Configuration
    FIREBASE_PROJECT_ID: str = os.getenv("FIREBASE_PROJECT_ID", "kanak-fmcg-bh")
    CORS_ALLOW_ORIGINS: str = os.getenv("CORS_ALLOW_ORIGINS", "")
    FIREBASE_CREDENTIALS_JSON: str = os.getenv("FIREBASE_CREDENTIALS_JSON", "")

    # Master Platform Administrator
    MASTER_ADMIN_EMAIL: str = os.getenv("MASTER_ADMIN_EMAIL", "")

    # Google Sheets API Gateway
    GOOGLE_SHEETS_SPREADSHEET_ID: str = os.getenv("GOOGLE_SHEETS_SPREADSHEET_ID", "")

    class Config:
        env_file = ".env"
        case_sensitive = True

settings = Settings()
