"""Alembic environment for the authoritative PostgreSQL schema."""
from logging.config import fileConfig
from alembic import context
from sqlalchemy import create_engine, pool

from backend.app.config import settings
from backend.app.database import Base
import backend.app.models.entities  # noqa: F401  register all mapped tables

config = context.config
if config.config_file_name:
    fileConfig(config.config_file_name)
metadata = Base.metadata

def database_url():
    value = config.get_main_option("sqlalchemy.url") or settings.DATABASE_URL
    if not value:
        raise RuntimeError("DATABASE_URL is mandatory for migrations")
    return value

def run_migrations_offline():
    context.configure(url=database_url(), target_metadata=metadata,
                      literal_binds=True, compare_type=True)
    with context.begin_transaction():
        context.run_migrations()

def run_migrations_online():
    connectable = create_engine(database_url(), poolclass=pool.NullPool)
    with connectable.connect() as connection:
        context.configure(connection=connection, target_metadata=metadata, compare_type=True)
        with context.begin_transaction():
            context.run_migrations()
    connectable.dispose()

if context.is_offline_mode():
    run_migrations_offline()
else:
    run_migrations_online()
