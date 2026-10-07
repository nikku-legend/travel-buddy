# Production Readiness Runbook

This application is not production-ready until the deployment-specific
items below are completed and verified in staging. The CI workflow runs
the backend test suite and frontend lint/build; it does not deploy, run a
real payment, or prove production-database locking.

## Required configuration

Configure secrets in the deployment platform, not in source control or
container images:

| Variable | Requirement |
| --- | --- |
| `DB_URL` | Dedicated MySQL 8 production database over TLS; do not use the local default. |
| `DB_USERNAME`, `DB_PASSWORD` | Least-privilege application account; keep migration privileges controlled by deployment policy. |
| `JWT_SECRET` | High-entropy secret generated per environment; never reuse the test value. |
| `CORS_ALLOWED_ORIGINS` | Comma-separated exact HTTPS origins for the deployed web clients. Wildcards are rejected. |
| `RAZORPAY_ENABLED` | Set `true` only after real environment-specific keys and callback configuration have been verified. |
| `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET` | Store as platform secrets; never log or expose the secret to the frontend. |
| `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD` | Use only for controlled first-admin bootstrap, then remove/disable bootstrap credentials. |
| `KYC_STORAGE_PATH` | Private persistent storage outside the web root; restrict access and configure retention/encryption. |

Do not enable `MOCK_PAYMENT_ENABLED` outside an isolated development or
test environment. The mock route is disabled by default.

For local trip-checkout acceptance without Razorpay credentials, enable
`MOCK_PAYMENT_ENABLED=true` only on the local backend and set
`VITE_MOCK_PAYMENTS=true` for the local Vite process. The UI then offers
explicit simulated success and decline outcomes; the backend independently
rejects the mock endpoint unless its flag is enabled. Keep both settings
unset in staging and production.

## Local traveler acceptance

With the backend connected to a disposable database and local development
accounts/listings available:

1. Start the backend with `MOCK_PAYMENT_ENABLED=true`.
2. Start the frontend with `VITE_MOCK_PAYMENTS=true`.
3. From Home, open the planner, save a route and places, add available trip
   selections, and open the saved trip from **My trips**.
4. Review the checkout total, accept it, then simulate both a decline and a
   successful payment. A decline must allow a new checkout; success must
   confirm the saved trip.
5. Reload the trip after success. The persisted checkout status must remain
   visible and the app must not offer another payment. For a pending checkout,
   the app should resume the existing checkout instead of creating a new one.

These simulated outcomes validate application behavior only. They do not
prove Razorpay signature handling, gateway callbacks, MySQL locking, or
production deployment behavior.

Refund approvals are also bookkeeping decisions, not disbursements. The
finance workflow records an approved refund as `APPROVED`; only a confirmed
gateway operation may set it to `COMPLETED` and populate the processed time
and provider reference. Razorpay refund integration and provider callback
reconciliation remain deployment work.

## Network and TLS

- Terminate TLS at a managed load balancer or reverse proxy and redirect
  HTTP to HTTPS. Forward only required headers and restrict direct access
  to the application port.
- Configure database TLS and firewall rules so only application and
  controlled migration hosts can connect.
- Set `CORS_ALLOWED_ORIGINS` to exact production origins. Keep credentialed
  CORS; never use `*`.
- Confirm JWTs, payment credentials, uploaded KYC files and database
  credentials are not present in browser assets, logs or public storage.

## Database deployment and restore

1. Create a dedicated database and least-privilege runtime account. Take a
   restorable backup before applying migrations.
2. Deploy against that database with Flyway enabled and Hibernate
   `ddl-auto=validate`; review migration output and stop on any failed or
   unexpected migration.
3. Schedule encrypted, access-controlled backups and define retention.
   Periodically restore a backup into an isolated database and verify
   schema, critical booking rows and application startup.
4. Do not use `flyway clean` against any shared or production database.
   Rehearse rollback/forward-fix procedures using a disposable staging copy.

Example MySQL backup/restore commands must be run by an operator with
environment-specific credentials and destination verification:

```powershell
mysqldump --single-transaction --routines --triggers --databases travel_buddy > backup.sql
mysql --host <restore-host> --user <restore-user> --password < backup.sql
```

Protect the dump as sensitive data, encrypt it at rest and in transit, and
verify the target host/database before restoring.

## Staging smoke checks

After every release candidate:

1. Verify the service starts with the staging database and Flyway reaches
   the latest migration.
2. Exercise sign-in, public discovery, planner save/reload, inventory
   revalidation, booking confirmation, cancellation and admin audit routes
   using dedicated staging accounts.
3. In Razorpay test mode, cover captured, declined, duplicate callback and
   payment-success/booking-failure recovery paths. Never use the browser
   response alone as proof of payment.
4. Confirm a non-admin cannot use admin APIs, a traveller cannot access
   another traveller's trip, and CORS rejects unlisted origins.
5. Check application/database logs, latency, error rates, disk/storage
   capacity and backup freshness; record the release and smoke-test result.

The project currently has opt-in real-MySQL migration and partner
contention tests. Run them only against a disposable dedicated database:

```powershell
Set-Location backend
.\mvnw.cmd test "-Dtravelbuddy.mysql.e2e=true" "-Dtest=MySqlSchemaE2ETest,PartnerMySqlE2ETest"
```

The standard H2 suite does not prove MySQL migration compatibility or
InnoDB locking. No production load test, disaster-recovery drill or
deployment is implied by a passing CI build.
