#!/bin/bash
# FlowRescue — Demo script: send test workflows and inject failures

BASE_URL="http://localhost:8080/api/v1"
TASK_URL="http://localhost:8083/api/v1/inject"

echo "🚀 FlowRescue Demo Runner"
echo "========================="

# ── Demo 1: Happy Path ────────────────────────────────────────────────────────

echo ""
echo "Demo 1: Happy Path (all steps succeed)"
echo "--------------------------------------"
RESP=$(curl -s -X POST "$BASE_URL/workflows/claims" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-happy-$(date +%s)" \
  -d '{
    "claimId": "CLM-10001",
    "memberId": "MBR-771",
    "providerId": "P-892",
    "amount": 245.12,
    "diagnosisCode": "X12",
    "serviceDate": "2026-04-15"
  }')
echo "$RESP" | python3 -m json.tool 2>/dev/null || echo "$RESP"
HAPPY_ID=$(echo "$RESP" | grep -o '"workflowId":"[^"]*"' | cut -d'"' -f4)
echo "→ workflowId: $HAPPY_ID"

sleep 3

echo ""
echo "Status check:"
curl -s "$BASE_URL/workflows/$HAPPY_ID" | python3 -m json.tool 2>/dev/null

# ── Demo 2: Inject Failure → Auto Retry ──────────────────────────────────────

echo ""
echo "Demo 2: Inject failure on CLAIM_PERSISTENCE → auto retry"
echo "----------------------------------------------------------"
curl -s -X POST "$TASK_URL/PERSIST_CLAIM_DECISION" \
  -H "Content-Type: application/json" \
  -d '{"fail": true}' | python3 -m json.tool 2>/dev/null

RESP=$(curl -s -X POST "$BASE_URL/workflows/claims" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-retry-$(date +%s)" \
  -d '{
    "claimId": "CLM-10002",
    "memberId": "MBR-772",
    "providerId": "P-893",
    "amount": 890.00,
    "diagnosisCode": "Y44",
    "serviceDate": "2026-04-16"
  }')
RETRY_ID=$(echo "$RESP" | grep -o '"workflowId":"[^"]*"' | cut -d'"' -f4)
echo "→ workflowId: $RETRY_ID"

sleep 3
echo "Status (should be FAILED or RETRY_SCHEDULED):"
curl -s "$BASE_URL/workflows/$RETRY_ID" | python3 -m json.tool 2>/dev/null

# Clear the failure flag
curl -s -X POST "$TASK_URL/PERSIST_CLAIM_DECISION" \
  -H "Content-Type: application/json" \
  -d '{"fail": false}' > /dev/null
echo "✅ Failure flag cleared — retry will succeed"

# ── Demo 3: Compensation Flow ─────────────────────────────────────────────────

echo ""
echo "Demo 3: Max retries → COMPENSATE flow"
echo "--------------------------------------"
curl -s -X POST "$TASK_URL/PERSIST_CLAIM_DECISION" \
  -H "Content-Type: application/json" \
  -d '{"fail": true}' > /dev/null

RESP=$(curl -s -X POST "$BASE_URL/workflows/claims" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-comp-$(date +%s)" \
  -d '{
    "claimId": "CLM-10003",
    "memberId": "MBR-773",
    "providerId": "P-894",
    "amount": 1200.00,
    "diagnosisCode": "Z99",
    "serviceDate": "2026-04-17"
  }')
COMP_ID=$(echo "$RESP" | grep -o '"workflowId":"[^"]*"' | cut -d'"' -f4)
echo "→ workflowId: $COMP_ID"
echo "Waiting 10s for recovery/compensation cycle..."
sleep 10

echo "Final status:"
curl -s "$BASE_URL/workflows/$COMP_ID" | python3 -m json.tool 2>/dev/null

curl -s -X POST "$TASK_URL/PERSIST_CLAIM_DECISION" \
  -H "Content-Type: application/json" \
  -d '{"fail": false}' > /dev/null

echo ""
echo "✅ Demo complete! Open http://localhost:5173 for the live dashboard"
