#!/usr/bin/env bash
#
# Put your LOCAL database back to empty, so you can work from a clean board. Two
# settings, deliberately:
#
#   ./scripts/reset-db.sh            empty the tables   (seconds, keeps the schema)
#   ./scripts/reset-db.sh --hard     destroy the volume (docker compose down -v)
#   ./scripts/reset-db.sh --counts   just show me the row counts
#
# This empties THIS MODULE's tables. The sidecar's own log is separate — clear that from
# its page, or with: curl -X DELETE localhost:9000/api/v1/dispatches
#
# Which one you want:
#
#   You re-sent an application and want a clean row      → the default
#   Liquibase will not start: checksum mismatch, or a
#   changeset that failed halfway                        → --hard
#   Schema-validation: missing table/column               → NEITHER. Your entity and
#     your changelog disagree; write a changeset. A reset only hides it until the
#     next startup. (README, "If you break your database".)
#
# This touches nothing but this module's own schema on your laptop. The deployed
# environments have their own path — the "Database repair" workflow in the Actions
# tab, which is the only route to them, since you hold no AWS credentials.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

# Same defaults as docker-compose.yml; a .env in the repo root overrides them there,
# so read it here too and stay in step.
if [ -f .env ]; then
  # shellcheck disable=SC1091
  set -a; . ./.env; set +a
fi
DB_NAME="${MYSQL_DATABASE:-neo_03}"
DB_USERNAME="${DB_USERNAME:-appuser}"
DB_PASSWORD="${DB_PASSWORD:-apppass}"

# The tables to empty, children first — that ordering is the habit that survives someone
# adding a foreign key later.
#
# ⚠️ KEEP THIS IN STEP WITH YOUR SCHEMA. When you replace demo_showcase with your own tables
# (see model/DemoShowcase.java), list them here. A name left behind here fails the truncate with
# "table doesn't exist", which is a confusing way to find out your cleanup script went stale.
TABLES="demo_showcase"

die() { echo "error: $*" >&2; exit 1; }

mysql_do() {
  # MYSQL_PWD rather than -p: same result without the "using a password on the command
  # line is insecure" warning on every call.
  docker compose exec -T -e MYSQL_PWD="$DB_PASSWORD" mysql \
    mysql -u"$DB_USERNAME" "$DB_NAME" -N -B -e "$1"
}

running() {
  [ -n "$(docker compose ps -q mysql 2>/dev/null)" ]
}

counts() {
  local t
  for t in $TABLES; do
    printf '  %-16s %s\n' "$t" "$(mysql_do "SELECT COUNT(*) FROM $t;" 2>/dev/null || echo '?')"
  done
}

case "${1:-}" in
  --hard)
    echo "Destroying the local MySQL volume. Liquibase rebuilds the schema on next start."
    docker compose down -v
    echo
    echo "Now: docker compose up --build"
    ;;

  --counts)
    running || die "mysql is not running — start it with: docker compose up -d mysql"
    echo "Rows in $DB_NAME:"
    counts
    ;;

  "")
    running || die "mysql is not running — start it with: docker compose up -d mysql"
    echo "Before:"
    counts
    # TRUNCATE, not DELETE: it also resets AUTO_INCREMENT, so the next run starts at
    # id 1 and screenshots match the walkthrough.
    # TRUNCATE every table in $TABLES, built from the list above so there is one place to
    # edit when your schema changes rather than two that can disagree.
    sql="SET FOREIGN_KEY_CHECKS = 0;"
    for t in $TABLES; do sql="$sql TRUNCATE TABLE \`$t\`;"; done
    mysql_do "$sql SET FOREIGN_KEY_CHECKS = 1;"
    echo "After:"
    counts
    echo
    echo "Send applications again from the sidecar at http://localhost:9000"
    ;;

  *)
    awk 'NR == 1 { next } /^#/ { sub(/^# ?/, ""); print; next } { exit }' "${BASH_SOURCE[0]}"
    ;;
esac
