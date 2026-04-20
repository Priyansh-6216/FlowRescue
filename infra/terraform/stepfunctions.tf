resource "aws_sfn_state_machine" "claim_workflow" {
  name       = "flowrescue-claim-workflow"
  role_arn   = aws_iam_role.step_functions_role.arn
  type       = "STANDARD"

  definition = file("${path.module}/../statemachine/claim_workflow.asl.json")

  logging_configuration {
    log_destination        = "${aws_cloudwatch_log_group.sfn_logs.arn}:*"
    include_execution_data = true
    level                  = "ALL"
  }

  tags = { Project = "FlowRescue" }
}

resource "aws_cloudwatch_log_group" "sfn_logs" {
  name              = "/aws/states/flowrescue-claim-workflow"
  retention_in_days = 30
}

variable "environment" {
  default = "dev"
}
