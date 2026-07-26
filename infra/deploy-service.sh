#!/usr/bin/env bash
# Deploy THIS repo's one service to an environment, pinned to the given image digests.
#
#   ./infra/deploy-service.sh <env> <backend-image> <frontend-image>
#
# Reads the per-repo config from infra/env/<env>.params (ServiceName, PathPrefix, priority,
# schema, role, …). Resolves the shared wiring the service template can't carry itself — the
# RDS endpoint and ALB DNS — from the platform stack's SSM parameters. Everything else
# (cluster, subnets, roles, listener, secrets) the template pulls from platform Exports.
#
# First deploy of a service parks at DesiredCount=0, creates its schema, then scales to 1:
# a task started before its schema exists crash-loops and CloudFormation waits on it for
# hours. The template creates no IAM, so no --capabilities is needed.
set -euo pipefail

ENV="${1:?usage: deploy-service.sh <env> <backend-image> <frontend-image>}"
BACKEND_IMAGE="${2:?backend image required}"
FRONTEND_IMAGE="${3:?frontend image required}"
REGION="${AWS_REGION:?AWS_REGION must be set}"
HERE="$(cd "$(dirname "$0")" && pwd)"

ACCOUNT="$(aws sts get-caller-identity --query Account --output text)"
BUCKET="neobank-cfn-$ACCOUNT-$REGION"
aws s3api head-bucket --bucket "$BUCKET" 2>/dev/null || aws s3 mb "s3://$BUCKET" --region "$REGION"

PARAMS_FILE="$HERE/env/$ENV.params"
# Portable read (macOS ships bash 3.2, which has no `mapfile`): skip blanks and comments.
PARAMS=()
while IFS= read -r line; do
  case "$line" in ''|'#'*) continue ;; esac
  PARAMS+=("$line")
done < "$PARAMS_FILE"
SERVICE="$(printf '%s\n' "${PARAMS[@]}" | sed -n 's/^ServiceName=//p')"
: "${SERVICE:?ServiceName= missing from $PARAMS_FILE}"
STACK="neobank-$ENV-$SERVICE"

# The two replaceable platform values, from SSM (no cross-stack import lock).
DB_ENDPOINT="$(aws ssm get-parameter --name "/neobank/$ENV/db-endpoint" --query Parameter.Value --output text)"
ALB_DNS="$(aws ssm get-parameter --name "/neobank/$ENV/alb-dns" --query Parameter.Value --output text)"

deploy() {  # $1 = desired count
  aws cloudformation deploy \
    --template-file "$HERE/service.yaml" \
    --stack-name "$STACK" \
    --no-fail-on-empty-changeset \
    --parameter-overrides "${PARAMS[@]}" \
      EnvironmentName="$ENV" \
      BackendImage="$BACKEND_IMAGE" \
      FrontendImage="$FRONTEND_IMAGE" \
      DbEndpoint="$DB_ENDPOINT" \
      AlbDnsName="$ALB_DNS" \
      DesiredCount="$1"
}

# Park a BRAND-NEW stack at zero tasks first: a task that starts before its schema exists
# crash-loops, and CloudFormation then waits on it for a very long time.
if aws cloudformation describe-stacks --stack-name "$STACK" --region "$REGION" >/dev/null 2>&1; then
  echo "==> updating $STACK"
else
  echo "==> first deploy of $STACK: park(0) -> schema -> scale(1)"
  deploy 0
fi

# ALWAYS, not just on a first deploy. This used to sit in the `else` branch, keyed on whether
# the STACK existed — which silently assumed "stack exists" implies "schema exists". It does
# not: if the stack is created and db-init then fails (it did, for three modules at once, when
# eleven services exhausted the RDS connection limit), every later deploy takes the "updating"
# path, never creates the schema, and the task crash-loops on `Unknown database` forever. The
# only way out was deleting the stack by hand.
#
# The script is idempotent — CREATE DATABASE IF NOT EXISTS plus a GRANT — so running it every
# time costs one short ECS task and removes a state the pipeline could not recover from.
bash "$HERE/db-init-schema.sh" "$ENV"

deploy 1

echo "==> $STACK deployed"
aws cloudformation describe-stacks --stack-name "$STACK" \
  --query "Stacks[0].Outputs[?OutputKey=='Url'].OutputValue" --output text
