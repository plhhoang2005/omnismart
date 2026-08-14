# Local infrastructure

The foundation release requires PostgreSQL only. Run from the repository root:

```powershell
docker compose --env-file .env -f infra/compose.yaml up -d
docker compose --env-file .env -f infra/compose.yaml ps
```

Stop services without deleting data:

```powershell
docker compose --env-file .env -f infra/compose.yaml down
```

Deleting the named volume removes the local database and is intentionally not part of the normal workflow.
