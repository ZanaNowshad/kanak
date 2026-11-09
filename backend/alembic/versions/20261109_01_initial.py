"""Initial authoritative schema baseline.

Revision ID: 20261109_01
Revises:
Create Date: 2026-11-09

This baseline is for a new, empty PostgreSQL database. Existing tenant
databases require a separately reviewed, backup-first adoption migration.
"""
from alembic import op
from sqlalchemy import inspect
from backend.app.database import Base
import backend.app.models.entities  # noqa: F401

revision = "20261109_01"
down_revision = None
branch_labels = None
depends_on = None

def upgrade():
    connection = op.get_bind()
    existing = set(inspect(connection).get_table_names()) - {"alembic_version"}
    if existing:
        raise RuntimeError(
            "Refusing to stamp or overwrite a pre-existing unversioned database: "
            "back up and complete a reviewed Alembic baseline migration."
        )
    Base.metadata.create_all(bind=connection, checkfirst=False)

def downgrade():
    raise RuntimeError("Downgrades are disabled until a backup/restore plan is verified")
