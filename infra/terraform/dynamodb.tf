resource "aws_dynamodb_table" "workflow_execution" {
  name         = "WorkflowExecution"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "workflowId"

  attribute {
    name = "workflowId"
    type = "S"
  }

  ttl {
    attribute_name = "ttl"
    enabled        = true
  }

  tags = { Project = "FlowRescue", Environment = var.environment }
}

resource "aws_dynamodb_table" "idempotency_record" {
  name         = "IdempotencyRecord"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "idempotencyKey"

  attribute {
    name = "idempotencyKey"
    type = "S"
  }

  ttl {
    attribute_name = "expiryEpoch"
    enabled        = true
  }

  tags = { Project = "FlowRescue", Environment = var.environment }
}

resource "aws_dynamodb_table" "recovery_schedule" {
  name         = "RecoverySchedule"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "workflowId"

  attribute {
    name = "workflowId"
    type = "S"
  }

  tags = { Project = "FlowRescue", Environment = var.environment }
}

resource "aws_dynamodb_table" "audit_trail" {
  name         = "AuditTrail"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "workflowId"
  range_key    = "eventTime"

  attribute {
    name = "workflowId"
    type = "S"
  }

  attribute {
    name = "eventTime"
    type = "S"
  }

  tags = { Project = "FlowRescue", Environment = var.environment }
}
