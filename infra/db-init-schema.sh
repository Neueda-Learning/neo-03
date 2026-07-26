#!/usr/bin/env bash
# Create THIS service's own schema and grant appuser on it, as the RDS master. Idempotent —
# safe to run on every first deploy. No secret touches this script: the shared db-init task
# definition already injects MASTER_USER/MASTER_PASSWORD from Secrets Manager; here we only
# supply DDL + a GRANT (appuser cannot grant to itself, so it must run as master).
#
#   ./infra/db-init-schema.sh <env>
#
# The schema name is this repo's DbName from infra/env/<env>.params. The shared wiring
# (cluster, subnets, security group, the db-init task def) comes from platform Exports.
set -euo pipefail

ENV="${1:?usage: db-init-schema.sh <env>}"
HERE="$(cd "$(dirname "$0")" && pwd)"
DBNAME="$(grep -Ev '^[[:space:]]*(#|$)' "$HERE/env/$ENV.params" | sed -n 's/^DbName=//p')"
: "${DBNAME:?DbName= missing from infra/env/$ENV.params}"

exp() { aws cloudformation list-exports --query "Exports[?Name=='neobank-$ENV-$1'].Value" --output text; }
CLUSTER="$(exp ClusterArn)"; TASKDEF="$(exp DbInitTaskDef)"
SUBNETS="$(exp SubnetIds)"; SG="$(exp TaskSgId)"

SQL="CREATE DATABASE IF NOT EXISTS \`$DBNAME\`;
GRANT ALL PRIVILEGES ON \`$DBNAME\`.* TO 'appuser'@'%';
FLUSH PRIVILEGES;"
# Pipe the SQL from a file, NOT `mysql -e "..."`: the backtick identifier quotes would be
# command-substituted by the container shell inside a double-quoted -e string, blanking the
# schema name. A quoted heredoc writes the SQL verbatim, with no shell interpretation.
# And the EntryPoint is already [sh, -c], so the override is the script ALONE — wrapping it
# in another ["sh","-c",…] runs `sh -c sh -c <script>`, an empty shell that silently exits 0.
CMD="set -e
cat > /tmp/s.sql <<'EOSQL'
$SQL
EOSQL
mysql -h \"\$DB_HOST\" -u \"\$MASTER_USER\" -p\"\$MASTER_PASSWORD\" < /tmp/s.sql"
OVERRIDES="$(jq -nc --arg c "$CMD" '{containerOverrides:[{name:"db-init",command:[$c]}]}')"
NET="awsvpcConfiguration={subnets=[$SUBNETS],securityGroups=[$SG],assignPublicIp=ENABLED}"

echo "==> creating schema $DBNAME on neobank-$ENV"
ARN="$(aws ecs run-task --cluster "$CLUSTER" --task-definition "$TASKDEF" --launch-type FARGATE \
  --network-configuration "$NET" --overrides "$OVERRIDES" --query 'tasks[0].taskArn' --output text)"
aws ecs wait tasks-stopped --cluster "$CLUSTER" --tasks "$ARN"
CODE="$(aws ecs describe-tasks --cluster "$CLUSTER" --tasks "$ARN" \
  --query 'tasks[0].containers[0].exitCode' --output text)"
echo "db-init schema $DBNAME exit=$CODE"
test "$CODE" = "0"
