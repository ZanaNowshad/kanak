# Safe schema bootstrap

For a **new, empty PostgreSQL database**, the API container applies
`alembic -c /srv/backend/alembic.ini upgrade head` before starting Uvicorn.

Set `KANAK_DB_PASSWORD` in a **private, URL-safe** environment configuration
before running `docker compose up -d --build`. Never commit it.

For an **existing tenant database**, first take and restore-test a backup.
The baseline migration deliberately refuses to silently adopt existing
unversioned tables; a reviewed migration plan is required. Never run
`Base.metadata.create_all()` at normal API startup.

Firebase rules and credentials must be deployed separately, and offline
invoice replay must pass PostgreSQL integration/rollback tests before real
financial transactions are enabled.
