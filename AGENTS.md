# AGENTS.md — working in this repo

This is one of ten identical services in the **neo-bank onboarding** capstone
(superproject: [`neo-00`](https://github.com/Neueda-Learning/neo-00)). Read this before
changing anything.

## What this repo is for

Two things at once, and the tension between them is worth knowing about.

**Proving the plumbing:** repos assembled by submodules, one `docker compose up`, a
request→`202`→callback loop, deployed to AWS by `neo-00`'s pipeline.

**Being the template a team clones.** That is why the layout is
`controller/ service/ repository/ model/ dto/ integrations/` — the same shape as the Week-2 lab
track, so nothing about the structure has to be learned before the work starts. And it is why the
business logic is now **one file, `service/ApplicationService.java`**, which does three placeholder
things — log, write one row, report `ACCEPTED` — so the journey runs end to end before a team has
written a line. Growing it into real logic is the point; **there is nothing here to preserve.** In
particular `demo_showcase` is a placeholder table a team is expected to delete, not a base to build
on.

## The contract is fixed

Owned by `neo-00/api-contract.md`. Do not change, in either direction:

- `POST /api/v1/applications` answers **`202`** with
  `{status:"in-progress", applicationId, serviceId, command}`.
- The outcome goes back as `PUT /api/v1/applications/{applicationId}` with exactly three
  fields: `{serviceId, status, comment}`. The id is in the path, so it is not in the body.
- `status` is one of `ACCEPTED` · `REJECTED` · `REFERRED`, uppercase.
- `serviceId` is this repo's name with the hyphen removed — `neo-03` → `neo03`.
  That mismatch is intentional; don't "fix" it.

`ApplicationControllerTest` pins the JSON shape. If a change makes it fail, the change is
wrong, not the test.

## This service is deployed, not just run

Pushing to `main` publishes `ghcr.io/neueda-learning/neo-03-{backend,frontend}` and **this repo
deploys itself** to dev (`.github/workflows/pipeline.yml`, OIDC — no stored AWS keys); prod is a
manual `promote` that ships the digest dev proved, behind a required reviewer. Feature branches
only build and test. Three consequences worth holding on to:

- **The front end's path prefix is baked at build time.** `APP_BASE_PATH=/neo-03` is a
  Docker build argument, because Vite writes asset URLs into index.html when the image is
  built and an ALB cannot rewrite paths. The pipeline reads it from `infra/env/dev.params`'s
  `PathPrefix`, so don't hard-code it anywhere else or the deployed UI is a blank page with
  404s for `/assets/…`.
- **The contract is what makes the slot swappable.** Anything that changes the shape of the
  `202` or the callback breaks the orchestrator, which is deployed separately and will not
  be rebuilt to match.
- **The deployed schema outlives your branch.** dev's MySQL is not recreated on every deploy, so
  a changeset that was edited after it ran, or that failed halfway, leaves the service
  crash-looping until someone repairs the schema — see the *Database repair* workflow and the
  table in `README.md`.


## Rules

- **Own schema only.** Read and write this service's MySQL schema, never another's.
  Integrate over REST.
- **Liquibase owns the schema**, JPA runs `ddl-auto=validate`. Migrations are append-only:
  add a change set, **never edit an applied one** — on a deployed environment that is a
  checksum mismatch and a crash-loop, repairable only by destroying the schema (*Database
  repair* → `reset`). A `Schema-validation:` error is the opposite problem: your entity is
  ahead of your changelog, so write the changeset rather than repairing anything.
- **`backend/`, `frontend/` and `mock-integration/` stay at the repo root** — the system compose
  builds them by path (`./neo-03/backend`). Moving them breaks the whole stack.
- **`mock-integration/` is a service, not a test fixture.** It is the mocked government identity
  sources this module calls over HTTP, it is built by `docker compose up`, and on AWS it is a
  **third container in the same ECS task** (reached on `127.0.0.1:8081`). Changing its wire
  format means changing `IdVerificationClient` in the same commit — nothing else talks to it.
- **Keep `./mvnw test` green** — 96 in `backend/`, 30 in `mock-integration/`, all on H2 or no
  database at all, no Docker. Real-MySQL tests are `*IT` (Testcontainers) and run on
  `./mvnw verify` — in CI automatically, locally with `-DskipITs=false` and Docker up.
- **Never log `identityDocument.documentId`.** It goes to the identity agencies and appears
  nowhere else — not a log line, not an error message, not the callback.
  `IdVerificationClientTest.documentIdIsNeverLogged` fails the build if it leaks.
- **Everything configurable is an env var.** One image serves as any slot; anything
  hard-coded per-service breaks that.

## Map

| Where | What |
|---|---|
| `backend/.../service/ApplicationService.java` | **the decision** — which confidence means what, and the reason code that explains it |
| `backend/.../service/ProviderGateway.java` | **how hard to try**: 3 attempts on the primary (1s/2s backoff), then one on the fallback |
| `backend/.../integrations/idprovider/` | one HTTP call to one agency, and the circuit breaker |
| `backend/.../service/ReasonCode.java` | the six LOCKED `KYC_*` codes. Do not invent more — ask |
| `mock-integration/` | the mocked agencies. Control panel at `http://localhost:8081` |
| `mock-integration/.../service/ConfidenceBook.java` | documentId → confidence, **deterministic**. Where the pinned fixtures live |
| `backend/.../integrations/orchestrator/` | the wire: 3 records + the client. **Fixed** — see its `package-info.java` |
| `backend/.../controller/ApplicationController.java` | the whole contract HTTP surface |
| `backend/.../integrations/orchestrator/Application.java` | the customer's form as Java — nine nested records. **Read it before writing rules** |
| `backend/.../model/DemoShowcase.java` | ⚠️ the placeholder table. Replace it; do not add columns |
| `backend/.../repository/` · `dto/` | one repository, one view record |
| `backend/src/main/resources/db/changelog/` | schema |
| `backend/src/main/resources/static/` | zero-build status page at `/` |
| `frontend/src/` | the React UI — **one screen**, no tabs, no router |
| `frontend/src/components/RequestsScreen.jsx` | that screen: what this module was asked, and what it answered |
| `db/init/` | schema list + local grants. Creates `sidecar_db`; **runs only on a fresh MySQL volume** |
| `scripts/reset-db.sh` | empty this module's own tables for a clean board |

## The orchestrator, and the sidecar that plays it

This service is only ever *called*. Nothing in this repo sends itself an application, and
nothing should: a UI button that POSTs `/api/v1/applications` is not the contract, it is a
short cut that would let a broken module look finished.

Locally the caller is a **sidecar** — a mock orchestrator at `http://localhost:9000`, built by
compose straight from [`Neueda-Learning/neobank-sidecar`](https://github.com/Neueda-Learning/neobank-sidecar)
as a git build context. It sends the 26-application corpus to the real endpoint with the real
envelope, and it serves `PUT /api/v1/applications/{id}` so the answer has somewhere to land. Rules:

- **No sidecar source lives here, and none should.** Do not vendor it, do not clone it into this
  repo, do not change its `build:` to a local path. The whole point of the remote context is that
  there is exactly one copy, so ten module repos cannot each grow a different mock orchestrator.
  Fixes go to that repo.
- **`ORCHESTRATOR_URL` is the only thing that changes** between the sidecar
  (`http://sidecar:8080`, or `http://localhost:9000` from your IDE) and the real orchestrator
  (`http://orchestrator:8080`). If something else has to change to switch between them, the
  module has grown a dependency on its caller and that is the bug.
- **Never add a back door.** No endpoint that sets a decision directly, no test hook that skips
  the callback. The whole value of the local loop is that it takes the same path production does.
