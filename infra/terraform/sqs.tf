locals {
  queues = {
    "workflow-task-queue" = {},
    "recovery-queue"      = {},
    "compensation-queue"  = {},
    "workflow-audit-queue"= {},
    "notification-queue"  = {},
    "manual-review-queue" = {},
  }
}

resource "aws_sqs_queue" "dlqs" {
  for_each                   = local.queues
  name                       = "${each.key}-dlq"
  message_retention_seconds  = 1209600  # 14 days
  tags = { Project = "FlowRescue" }
}

resource "aws_sqs_queue" "queues" {
  for_each                   = local.queues
  name                       = each.key
  visibility_timeout_seconds = 60       # 6x Lambda timeout of 10s
  message_retention_seconds  = 86400
  receive_wait_time_seconds  = 20       # Long polling

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.dlqs[each.key].arn
    maxReceiveCount     = 5
  })

  tags = { Project = "FlowRescue" }
}
