#!/usr/bin/env bash
# Repair THIS service's own schema on an environment. Two actions:
#
#   ./infra/db-reset.sh <env> unlock              release a stuck changelog lock (no data loss)
#   ./infra/db-reset.sh <env> reset [--yes]       drop the schema; Liquibase rebuilds it
#
# Normally you do NOT run this from a laptop — teams have no AWS credentials. Use the
# "Database repair" workflow in this repo's Actions tab, which runs exactly this script
# under the repo's own OIDC deploy role. dev runs immediately; prod pauses for approval.
#
# WHICH ACTION:
#   * app hangs at startup, log repeats "waiting for changelog lock"  -> unlock
#     (a task killed mid-migration left DATABASECHANGELOGLOCK held; the row is just stale)
#     NOTE, verified against liquibase 4.27 on this stack: a stale lock only bites when there
#     is a PENDING changeset. With the schema already up to date, Liquibase's fast check
#     ("Database is up to date, no changesets to execute") returns before it ever asks for the
#     lock, so a held row sits there invisibly and the app starts fine — right up until the
#     next push that adds a changeset, which then hangs. So if you find a stale lock on a
#     healthy service, clear it anyway: you are defusing the next deploy, not fixing this one.
#   * checksum mismatch — an already-applied changeset was edited     -> reset
#   * a changeset failed halfway (MySQL DDL is not transactional, so objects exist with
#     no DATABASECHANGELOG row)                                       -> reset
#   * `Schema-validation: missing table/column ...` from Hibernate    -> NEITHER. The entity
#     and the changelog disagree: add a changeset. That is a code fix, not a broken database.
#
# HOW IT WORKS: SQL runs as the RDS master through the shared db-init task definition — the
# same mechanism db-init-schema.sh uses, so no credential ever touches this shell or a log.
# Both actions PARK the service (desiredCount 0) before touching the database and unpark
# after. That is deliberate, and it is the whole trick:
#   * a task hung on the changelog lock is inside Liquibase's retry loop and will simply
#     RE-ACQUIRE the lock seconds after the UPDATE lands, so clearing it under a running
#     task fixes nothing;
#   * Liquibase runs ONLY at application startup, and scaling back up IS a startup. Note
#     that re-running deploy-service.sh would NOT work here: identical parameters give
#     CloudFormation an empty changeset, no task rolls, and the script appears to do nothing.
#
# The schema name comes from THIS repo's infra/env/<env>.params and is never an argument,
# so this script cannot be pointed at another team's schema.
set -euo pipefail

ENV="${1:?usage: db-reset.sh <env> <unlock|reset> [--yes]}"
ACTION="${2:?usage: db-reset.sh <env> <unlock|reset> [--yes]}"
CONFIRM="${3:-}"
HERE="$(cd "$(dirname "$0")" && pwd)"
PARAMS_FILE="$HERE/env/$ENV.params"

case "$ACTION" in unlock|reset) ;; *) echo "unknown action: $ACTION (unlock|reset)" >&2; exit 2 ;; esac
[ -f "$PARAMS_FILE" ] || { echo "no such environment: $PARAMS_FILE" >&2; exit 2; }

param() { grep -Ev '^[[:space:]]*(#|$)' "$PARAMS_FILE" | sed -n "s/^$1=//p"; }
DBNAME="$(param DbName)";       : "${DBNAME:?DbName= missing from $PARAMS_FILE}"
SERVICE="$(param ServiceName)"; : "${SERVICE:?ServiceName= missing from $PARAMS_FILE}"

# prod is promoted, reviewed and digest-pinned. Wiping it is not a routine action, so the
# destructive action needs saying twice. (In the workflow the real gate is the prod
# environment's required reviewer; this guard is for laptop use.)
if [ "$ENV" = "prod" ] && [ "$ACTION" = "reset" ] && [ "$CONFIRM" != "--yes" ]; then
  echo "refusing to drop $DBNAME on PROD without --yes:" >&2
  echo "    ./infra/db-reset.sh prod reset --yes" >&2
  exit 2
fi

exp() { aws cloudformation list-exports --query "Exports[?Name=='neobank-$ENV-$1'].Value" --output text; }
CLUSTER="$(exp ClusterArn)"; TASKDEF="$(exp DbInitTaskDef)"
SUBNETS="$(exp SubnetIds)"; SG="$(exp TaskSgId)"
[ -n "$CLUSTER" ] && [ "$CLUSTER" != "None" ] || { echo "no neobank-$ENV platform stack in this account/region" >&2; exit 1; }
NET="awsvpcConfiguration={subnets=[$SUBNETS],securityGroups=[$SG],assignPublicIp=ENABLED}"

# Run SQL as the RDS master in a one-shot Fargate task. $1 = a label for the log, $2 = SQL.
run_sql() {
  # Pipe the SQL from a file, NOT `mysql -e "..."`: backtick identifier quotes inside a
  # double-quoted -e string get command-substituted by the container shell, blanking the
  # schema name. A quoted heredoc writes the SQL verbatim. And the task definition's
  # EntryPoint is already [sh, -c], so the override is the script ALONE — wrapping it in
  # another ["sh","-c",…] runs `sh -c sh -c <script>`, an empty shell that exits 0.
  local cmd overrides arn code
  cmd="set -e
cat > /tmp/q.sql <<'EOSQL'
$2
EOSQL
mysql -h \"\$DB_HOST\" -u \"\$MASTER_USER\" -p\"\$MASTER_PASSWORD\" < /tmp/q.sql"
  overrides="$(jq -nc --arg c "$cmd" '{containerOverrides:[{name:"db-init",command:[$c]}]}')"
  arn="$(aws ecs run-task --cluster "$CLUSTER" --task-definition "$TASKDEF" --launch-type FARGATE \
    --network-configuration "$NET" --overrides "$overrides" --query 'tasks[0].taskArn' --output text)"
  aws ecs wait tasks-stopped --cluster "$CLUSTER" --tasks "$arn"
  code="$(aws ecs describe-tasks --cluster "$CLUSTER" --tasks "$arn" \
    --query 'tasks[0].containers[0].exitCode' --output text)"
  echo "    $1 exit=$code   (output: aws logs tail /neobank/$ENV --since 10m)"
  test "$code" = "0"
}

# --- find the service ------------------------------------------------------------------
# describe-services does NOT error on an unknown service: it returns an empty services[]
# and a failures[] entry. Left unchecked, a broken lookup would look exactly like "nothing
# to restart" and the script would exit 0 having done nothing visible.
DESC="$(aws ecs describe-services --cluster "$CLUSTER" --services "$SERVICE" --output json)"
COUNT="$(printf '%s' "$DESC" | jq -r '.services[0].desiredCount // empty')"
FAILURE="$(printf '%s' "$DESC" | jq -r '.failures[0].reason // empty')"
DEPLOYED=yes
if [ -z "$COUNT" ]; then
  if [ "$FAILURE" = "MISSING" ]; then
    DEPLOYED=no
    [ "$ACTION" = "reset" ] || { echo "service $SERVICE is not deployed on $ENV — nothing to unlock" >&2; exit 2; }
  else
    echo "could not read service '$SERVICE' on cluster $CLUSTER (failure: ${FAILURE:-unknown})" >&2
    exit 1
  fi
fi

echo "==> $ACTION · schema $DBNAME · service $SERVICE · neobank-$ENV"

# A repair that fails between park and unpark must not leave the service switched off — that
# would turn "the migration is broken" into "the service is gone". Restore the count on any
# non-zero exit; the operator still sees the failure, just not an outage on top of it.
PARKED=no
restore_on_failure() {
  local code=$?
  # `set -e` off inside the handler: a failing test here must not abandon the restore below.
  # (An `[ … ] && echo …` chain returns 1 when the test is false, which under -e would kill
  # this function before it ever scaled the service back up.)
  set +e
  if [ "$code" != "0" ] && [ "$PARKED" = yes ]; then
    echo "!! $ACTION failed (exit $code) — restoring $SERVICE to desiredCount=$COUNT so it is not left down" >&2
    if [ "$ACTION" = reset ]; then
      echo "!! if the drop succeeded and the recreate did not, the schema is gone and the service" >&2
      echo "!! will crash-loop on 'Unknown database': ./infra/db-init-schema.sh $ENV" >&2
    fi
    # Deliberately no `wait services-stable` here: this run has already failed, and blocking
    # for up to ~10 more minutes would hide the error that matters. Asking for the tasks back
    # is the part that counts; watch it come up with `aws ecs describe-services`.
    aws ecs update-service --cluster "$CLUSTER" --service "$SERVICE" --desired-count "$COUNT" >/dev/null
    echo "!! $SERVICE asked back to desiredCount=$COUNT (not waited on). The repair did NOT happen —" >&2
    echo "!! fix the cause above and re-run." >&2
  fi
  exit "$code"
}
trap restore_on_failure EXIT

# --- park ------------------------------------------------------------------------------
if [ "$DEPLOYED" = yes ] && [ "$COUNT" != "0" ]; then
  echo "==> parking $SERVICE (was desiredCount=$COUNT) so nothing holds the schema"
  aws ecs update-service --cluster "$CLUSTER" --service "$SERVICE" --desired-count 0 >/dev/null
  aws ecs wait services-stable --cluster "$CLUSTER" --services "$SERVICE"
  PARKED=yes
fi

# --- the database work -------------------------------------------------------------------
case "$ACTION" in
  unlock)
    # ROW_COUNT() reports what was actually cleared, so "it did nothing" is visible in the log.
    run_sql "clear lock" "UPDATE \`$DBNAME\`.DATABASECHANGELOGLOCK
  SET LOCKED = 0, LOCKEDBY = NULL, LOCKGRANTED = NULL
  WHERE LOCKED = 1;
SELECT CONCAT('lock rows cleared: ', ROW_COUNT()) AS result;" || {
      echo "unlock failed. If the log says DATABASECHANGELOGLOCK doesn't exist, this schema has" >&2
      echo "never been migrated — the problem is elsewhere, or you want 'reset'." >&2
      exit 1
    }
    ;;
  reset)
    run_sql "drop $DBNAME" "DROP DATABASE IF EXISTS \`$DBNAME\`;"
    # Recreate + grant by delegating, so that SQL exists in exactly ONE place and stays
    # byte-identical with what a first deploy does.
    bash "$HERE/db-init-schema.sh" "$ENV"
    ;;
esac

# --- unpark ------------------------------------------------------------------------------
if [ "$DEPLOYED" = no ]; then
  echo "==> $DBNAME is empty; $SERVICE is not deployed yet, so its next deploy will build the schema"
  exit 0
fi
if [ "$COUNT" = "0" ]; then
  echo "==> $SERVICE is parked (desiredCount=0), so nothing was restarted."
  echo "    Liquibase runs at startup: ./infra/services.sh start $ENV (or the Power workflow) rebuilds it."
  exit 0
fi

echo "==> restarting $SERVICE (desiredCount=$COUNT) — Liquibase runs on startup"
aws ecs update-service --cluster "$CLUSTER" --service "$SERVICE" --desired-count "$COUNT" >/dev/null
aws ecs wait services-stable --cluster "$CLUSTER" --services "$SERVICE"
echo "==> done. $SERVICE is stable on neobank-$ENV."
echo "    check the migration: aws logs tail /neobank/$ENV --since 10m | grep -i liquibase"
