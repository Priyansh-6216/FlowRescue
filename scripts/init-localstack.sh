#!/bin/bash
set -e

echo "========================================="
echo "  FlowRescue — LocalStack Initialization"
echo "========================================="

ENDPOINT="http://localhost:4566"
REGION="us-east-1"

aws_cmd() {
    aws --endpoint-url=$ENDPOINT --region=$REGION "$@"
}

# ── DynamoDB Tables ────────────────────────────────────────────────────────────

echo "→ Creating DynamoDB: WorkflowExecution"
aws_cmd dynamodb create-table \
  --table-name WorkflowExecution \
  --attribute-definitions AttributeName=workflowId,AttributeType=S \
  --key-schema AttributeName=workflowId,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --no-cli-pager || echo "  (already exists)"

echo "→ Creating DynamoDB: IdempotencyRecord"
aws_cmd dynamodb create-table \
  --table-name IdempotencyRecord \
  --attribute-definitions AttributeName=idempotencyKey,AttributeType=S \
  --key-schema AttributeName=idempotencyKey,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --no-cli-pager || echo "  (already exists)"

echo "→ Creating DynamoDB: RecoverySchedule"
aws_cmd dynamodb create-table \
  --table-name RecoverySchedule \
  --attribute-definitions AttributeName=workflowId,AttributeType=S \
  --key-schema AttributeName=workflowId,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --no-cli-pager || echo "  (already exists)"

echo "→ Creating DynamoDB: AuditTrail"
aws_cmd dynamodb create-table \
  --table-name AuditTrail \
  --attribute-definitions \
      AttributeName=workflowId,AttributeType=S \
      AttributeName=eventTime,AttributeType=S \
  --key-schema \
      AttributeName=workflowId,KeyType=HASH \
      AttributeName=eventTime,KeyType=RANGE \
  --billing-mode PAY_PER_REQUEST \
  --no-cli-pager || echo "  (already exists)"

# ── SQS Queues + DLQs ──────────────────────────────────────────────────────────

create_queue_with_dlq() {
    local name=$1
    local dlq_name="${name}-dlq"

    echo "→ Creating SQS DLQ: $dlq_name"
    DLQ_URL=$(aws_cmd sqs create-queue \
      --queue-name $dlq_name \
      --output text --query 'QueueUrl' \
      --no-cli-pager 2>/dev/null || echo "exists")

    DLQ_ARN=$(aws_cmd sqs get-queue-attributes \
      --queue-url "http://localhost:4566/000000000000/${dlq_name}" \
      --attribute-names QueueArn \
      --output text --query 'Attributes.QueueArn' \
      --no-cli-pager 2>/dev/null || echo "arn:aws:sqs:us-east-1:000000000000:${dlq_name}")

    echo "→ Creating SQS Queue: $name (with DLQ)"
    aws_cmd sqs create-queue \
      --queue-name $name \
      --attributes "{\"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":\\\"${DLQ_ARN}\\\",\\\"maxReceiveCount\\\":\\\"5\\\"}\"}" \
      --no-cli-pager || echo "  (already exists)"
}

create_queue_with_dlq "workflow-task-queue"
create_queue_with_dlq "recovery-queue"
create_queue_with_dlq "compensation-queue"
create_queue_with_dlq "workflow-audit-queue"
create_queue_with_dlq "notification-queue"
create_queue_with_dlq "manual-review-queue"

echo ""
echo "✅ LocalStack initialization complete!"
echo ""
echo "DynamoDB tables:"
aws_cmd dynamodb list-tables --no-cli-pager
echo ""
echo "SQS queues:"
aws_cmd sqs list-queues --no-cli-pager
