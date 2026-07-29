# neo-03 — Identity Verification (KYC)

**Module 01 of ten.** One step of the neo-bank customer-onboarding journey, owned by
**Team 03**. The journey is driven by
[`neo-00`](https://github.com/Neueda-Learning/neo-00), the orchestrator — which also owns the AWS
environment this repo deploys itself into. You never call another module, and no module
calls you: only the orchestrator does.

Everything that distinguishes one module from another is an env var — `SERVICE_ID`,
`SERVICE_NAME`, `SERVICE_DOMAIN`, `SERVICE_TEAM` — so all ten repos start as the same
image wearing a different name. What makes yours *yours* is the business rules you write
in `service/ApplicationService.java`.

**This is a skeleton.** It accepts an application from the orchestrator, answers `202`
immediately, and then — off the request thread — does the three smallest things that prove the
whole contract works:

1. prints `Hello world from processApplication`,
2. writes one row to a placeholder table called `demo_showcase`,
3. reports `ACCEPTED` back with `PUT /api/v1/applications/{id}`.

**All three are placeholders, and replacing them is the work.** Start with
`backend/.../service/ApplicationService.java` — it is the only file you have to touch to change
what this module does.

```
controller/     the HTTP surface (contract + health + info + error shape)
service/        ApplicationService  ← YOURS
repository/     one Spring Data interface
model/          DemoShowcase (⚠️ replace) · Decision enum
dto/            what your UI reads
integrations/
  orchestrator/ the wire, and the typed Application. Fixed — your own
                integrations go BESIDE it, not in it
config/         two beans
```

That layout is deliberately the one from the Week-2 lab track
(`controller → service → repository`), so nothing structural has to be learned before the work
starts.

**Read `integrations/orchestrator/Application.java` first.** It is the customer's application form
as Java — nine nested records, every field a module could need — and it is the only place the
domain is written down. Your rules read it: `request.application().finances().annualIncome()`.

**Your table is not `demo_showcase`.** That one exists so the skeleton has something to write and
something to show. Replacing it means a new Liquibase change set (`002-…`), your own entity, and
deleting `DemoShowcase` — never adding columns to it. `DemoShowcase.java` spells out the steps.

## What pushing does

**This repo deploys itself.** Trunk-based:

- push to a **feature branch** → build + test only. Nothing is published, nothing deploys.
- push to **`main`** → build + test → publish two images to ghcr.io pinned by `@sha256` →
  deploy **this service** to dev → smoke it through the load balancer → record the digest
  as the promote source.
- *Run workflow → `promote: true`* on `main` → **prod**, which pauses for a required
  reviewer and then ships the exact digest dev proved. No rebuild.

There are no stored AWS keys: each job assumes this repo's own IAM role via GitHub OIDC, and
that role can only touch this repo's own `neobank-<env>-neo-03` stack. You never hold AWS
credentials yourself — everything that reaches AWS goes through a workflow in this repo.

The front-end image is built with `APP_BASE_PATH=/neo-03`, because in the deployed stack
every UI shares one port and is told apart by its path. Vite bakes asset URLs at build time and
a load balancer cannot rewrite paths, so the prefix has to be a build argument — the pipeline
reads it from `infra/env/dev.params`'s `PathPrefix` so the image and the stack cannot drift.

### Where your module ends up

| | Your module | Your API | The board |
|---|---|---|---|
| **dev** | [`/neo-03/`](http://neobank-dev-571740187.ap-southeast-1.elb.amazonaws.com/neo-03/) | [`/neo-03/health`](http://neobank-dev-571740187.ap-southeast-1.elb.amazonaws.com/neo-03/health) · [`/neo-03/info`](http://neobank-dev-571740187.ap-southeast-1.elb.amazonaws.com/neo-03/info) | [orchestrator](http://neobank-dev-571740187.ap-southeast-1.elb.amazonaws.com/) |
| **prod** | [`/neo-03/`](http://neobank-prod-294820685.ap-southeast-1.elb.amazonaws.com/neo-03/) | [`/neo-03/health`](http://neobank-prod-294820685.ap-southeast-1.elb.amazonaws.com/neo-03/health) · [`/neo-03/info`](http://neobank-prod-294820685.ap-southeast-1.elb.amazonaws.com/neo-03/info) | [orchestrator](http://neobank-prod-294820685.ap-southeast-1.elb.amazonaws.com/) |

**dev is yours** — it moves every time you merge to `main`, so that link is the honest answer
to "is my module working?". **prod is not**: it only ever runs an image dev has already
proven, and only after a human approves the promote. A 404 on prod means your module has not
been promoted yet — not that it is broken.

`/info` is the quickest check that a deploy actually landed: it reports the `serviceId`,
domain, team and mocked-dependency register **this running container** believes in. If that
does not match what you configured, the deploy did not go where you think it did.

Plain HTTP, no DNS name: those hostnames belong to the load balancers and change if one is
ever replaced. The live values are always in SSM
(`/neobank/dev/alb-dns`, `/neobank/prod/alb-dns`), and the instructor can read them out.

## If you break your database

Liquibase owns the schema (`ddl-auto: validate`), and there are a few ways to get stuck. **Read
the symptom before reaching for the destructive fix** — the first row here is the common one and
is not a database problem at all:

| Symptom | What it means | Fix |
|---|---|---|
| `Schema-validation: missing table/column …` | your entity and your changelog disagree | **write a changeset.** Resetting only hides it until the next startup |
| App never starts; log repeats *"waiting for changelog lock"* | a task died mid-migration and left `DATABASECHANGELOGLOCK` held | *Database repair* → **`unlock`**. No data loss |
| You add a changeset and *that* deploy hangs on the lock, though the last one was fine | same stale lock — Liquibase's fast check skips the lock entirely while the schema is up to date, so it stayed hidden until something was actually pending | *Database repair* → **`unlock`**, then re-deploy |
| Crash-loop on a checksum mismatch | you edited a changeset that had already run | *Database repair* → **`reset`** |
| A changeset failed halfway | MySQL DDL isn't transactional, so objects exist with no changelog row | *Database repair* → **`reset`** |
| Local only | — | `docker compose down -v` |

**Never edit a changeset that has already been applied — add a new one.** That is the rule the
checksum row above exists to enforce.

*Database repair* is a workflow in this repo's **Actions** tab (`.github/workflows/db-reset.yml`).
Pick the environment and the action; `reset` also asks you to type your `DbName` from
`infra/env/<env>.params` so you can't fire it by accident. **dev runs straight away. prod pauses
for an approver** — the same reviewer gate that guards a promote.

A `reset` destroys **only this service's schema** (`neo_03`). Every other service and the
orchestrator's own journey data live in separate schemas and are untouched — but the orchestrator
will still remember applications whose module rows you just deleted, so expect stale rows on the
board until it is reset too.

## Run it

Normally you don't: you run the whole system from `neo-00`. To work on this service
alone:

```bash
docker compose up --build
# http://localhost:9000   THE SIDECAR — send applications, watch what comes back
# http://localhost:5173   React UI — what this service has seen and answered
# http://localhost:8081   THE IDENTITY AGENCIES — turn one off, watch the module cope
# http://localhost:8080/  zero-build status page served by Spring Boot
# http://localhost:8080/health · /info · /swagger-ui.html
# http://localhost:8080/api/v1/provider/health   circuit-breaker state per agency
```

### The identity agencies (`mock-integration/`)

This module's topic is *the provider is slow or down*, so it ships one: a **National Identity
Agency** (primary) and a **Tax Agency** (fallback), both in `mock-integration/`. It answers
every call by default — every misbehaviour dial seeds to off — and a document's confidence is a
**pure function of its number**, so a checkpoint reproduces on every machine and every run.

```bash
# Maria Nowak's passport always scores exactly 92 — the accept threshold, on purpose
curl -sX POST localhost:8081/api/v1/agencies/national/verifications \
  -H 'content-type: application/json' \
  -d '{"fullName":"Maria Nowak","dateOfBirth":"1996-04-11",
       "document":{"type":"PASSPORT","documentId":"ZS1234567",
                   "issuingCountry":"PL","expiryDate":"2031-02-28"}}'

# Take the PRIMARY down and send an application: 3 attempts (1s, 2s), then the tax agency
# answers and the applicant still verifies — with KYC_FAILED_OVER_TO_SECONDARY on the callback.
curl -sX PUT localhost:8081/api/v1/admin/config/national \
  -H 'content-type: application/json' \
  -d '{"latencyMs":0,"failureRatePct":0,"killSwitch":true}'

# Take BOTH down and the application is PARKED for a human, never rejected: an outage says
# nothing about the applicant. After 5 consecutive failures the breaker opens and later
# applications cost no network time at all.
curl -sX POST localhost:8081/api/v1/admin/reset      # put it all back, live
```

One document always fails whatever the dials say: **`ZZ0000000`** (scenario SIM-14) — the
corpus's convention for "make your mock provider fail".

**First run after adding the sidecar: `docker compose down -v` once.** MySQL creates the
sidecar's schema from `db/init/*.sql`, and it runs those only on an empty data directory — an
existing volume means the schema was never created. The sidecar's log says so if you forget.

Backend only, for fast iteration:

```bash
docker compose up -d mysql sidecar
cd backend
./mvnw test                                              # 19 tests, H2, no Docker
DB_URL=jdbc:mysql://localhost:3307/neo_03 ./mvnw spring-boot:run
```

Run this way and callbacks still land: the module's default `ORCHESTRATOR_URL` is
`http://localhost:9000`, which is exactly where the sidecar is. Set the sidecar's **module base
URL** field to `http://host.docker.internal:8080` so it can reach back into your IDE.

## Sending applications: the sidecar

The orchestrator is not running on your laptop, and waiting for it is not a development loop —
so a **sidecar** plays it at **http://localhost:9000**. It ships **26 applications** covering the
happy path, both sides of every boundary the rules care about, the integration failure modes,
and one envelope that must be rejected.

It matters that it works both ways. It sends to your real `POST /api/v1/applications`, *and* it
serves `PUT /api/v1/applications/{id}` — so your answer has somewhere to land and you can see the half
of the contract curl cannot show you.

```bash
open http://localhost:9000          # pick a scenario, Send, watch Ack then Callback

# or, if you prefer curl:
curl -s  localhost:9000/api/v1/scenarios  | jq '.scenarios[].id'
curl -sX POST localhost:9000/api/v1/dispatch \
     -H 'Content-Type: application/json' -d '{"scenarioId":"SIM-01"}'
curl -s  localhost:9000/api/v1/dispatches | jq '.[0]'
```

The sidecar lives in **its own repo** ([`Neueda-Learning/neobank-sidecar`](https://github.com/Neueda-Learning/neobank-sidecar))
and compose builds it straight from there — there is no sidecar source in this repo and nothing
for you to maintain. **The first build takes a few minutes**; after that it is cached.
`docker compose up --build sidecar` picks up a new version. Its full guide, the corpus table and
the planted failure triggers are in [its README](https://github.com/Neueda-Learning/neobank-sidecar#readme).

`./scripts/reset-db.sh` empties **this module's** tables for a clean board; the sidecar's own log
is cleared from its page.

## The contract

Full detail in [`api-contract.md`](https://github.com/Neueda-Learning/neo-00/blob/main/api-contract.md).
In short:

**Two endpoints, and only the first is the contract.**

| Endpoint | Purpose |
|---|---|
| `POST /api/v1/applications` | the orchestrator sends an application → `202 {status:"in-progress", applicationId, serviceId, command}` |
| `GET /api/v1/applications` | the `demo_showcase` rows — read by *your* UI, never by the orchestrator |
| `GET /health` · `GET /info` | DB-backed health · identity, BIAN domain, and what is mocked |

Add whatever else your operator screen needs — a search, a detail lookup, a manual override. Those
are yours; the `POST` is not.

Once the work is done — off the request thread, so within milliseconds unless you call something
slow — it PUTs `${ORCHESTRATOR_URL}/api/v1/applications/{applicationId}`:

```json
{ "serviceId": "neo03", "status": "ACCEPTED",
  "comment": "hello world from processApplication" }
```

**Three fields: the application id is in the URL, not the body.** This is an update to an
application the orchestrator already owns, so the id identifies the resource. The `comment` is your
reason — write it for the bank employee who has to explain the outcome to a customer: which rule
fired, and with what value.

## Configuration

Every knob is an env var, which is how one image serves as any slot:

| Env | Default | What |
|---|---|---|
| `SERVICE_ID` | `neo03` | the id sent on callbacks — note **no `-a`**, unlike the repo name |
| `SERVICE_NAME` | `Identity Verification (KYC)` | display name |
| `SERVICE_DOMAIN` | `unassigned` | the BIAN domain you own, reported on `/info` |
| `ORCHESTRATOR_URL` | `http://localhost:9000` | where callbacks go — see the three targets below |
| `MOCKED_DEPENDENCIES` | `national-identity-agency,tax-agency` | comma-separated systems you fake — the register, served live |
| `WORKER_POOL_SIZE` | `8` | threads available to run your rules |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | see compose | this service's own schema |
| `ID_PROVIDER_BASE_URL` | `http://localhost:8081` | the identity agencies — `http://mock-integration:8081` in compose, `http://127.0.0.1:8081` on Fargate (same task) |
| `ID_PROVIDER_TIMEOUT_MS` | `2000` | how long **one** attempt may take |
| `ID_PROVIDER_MAX_ATTEMPTS` / `ID_PROVIDER_BACKOFF_MS` | `3` / `1000,2000` | the ladder against the **primary**; the fallback always gets exactly one |
| `ID_PROVIDER_ACCEPT_THRESHOLD` / `ID_PROVIDER_REJECT_THRESHOLD` | `92` / `60` | `>=` verifies, `<=` fails, strictly between parks for a human |
| `ID_PROVIDER_BREAKER_THRESHOLD` / `_COOLDOWN_MS` | `5` / `30000` | consecutive failures before we stop calling, and for how long |

**There are no decision knobs.** What this module answers comes from your rules and from what the
identity agencies report — not from a weight, a seed or a delay. Those env vars existed when the
decision was a seeded coin flip; the coin flip is gone.

**The thresholds are not knobs either — they are compliance policy.** They are configuration so
that moving accept from 92 to 95 is a decision the risk team can make, not a code change, a
review and a deploy. Startup fails if reject is not below accept: inverted thresholds do not
throw at decision time, they silently empty the review band so no borderline case ever reaches a
human.
| `SIDECAR_PORT` / `SIDECAR_REF` / `MODULE_URL` | `9000` / `v1` / `http://backend:8080` | the mock orchestrator (`SIDECAR_REF` is the git ref compose builds) |

### The three things `ORCHESTRATOR_URL` can point at

Only this value changes between them. The module's code does not.

| Set it to | When |
|---|---|
| `http://sidecar:8080` | the mock orchestrator, from inside this repo's compose — **the default there** |
| `http://localhost:9000` | that same sidecar, from a module you run in your IDE — **the default in `application.yml`**, so this needs no configuration at all |
| `http://orchestrator:8080` | the **real** orchestrator, in the `neo-00` system stack — which sets it for you |

## Tests

```bash
cd backend
./mvnw test                       # 19: unit + web-slice + full-context H2
./mvnw verify -DskipITs=false     # + RequestRepositoryIT against real MySQL 8 (needs Docker)
```

`*Test` runs Docker-free. `*IT` needs Docker and is skipped locally unless you ask for it;
CI sets `CI=true`, which activates the `integration` profile and runs it for real.

## Conventions

- **Own schema only.** This service reads and writes its own MySQL schema, never another's.
- **Liquibase owns the schema**; JPA runs `ddl-auto=validate`. Migrations are append-only —
  add a change set, never edit an applied one.
- `backend/` and `frontend/` **stay at the repo root** — the system compose builds
  `./neo-03/backend` and `./neo-03/frontend` by path.
