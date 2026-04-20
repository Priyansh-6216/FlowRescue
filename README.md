# FlowRescue ⚡
### Distributed Workflow Failure Recovery Engine

> A self-healing workflow orchestration system that detects failed distributed steps, retries safely, and executes compensation logic to restore consistency across services.

---

## 1. Problem Statement

In distributed systems, a business process rarely happens in a single database transaction.

A healthcare claim touches multiple services: validation → duplicate check → pricing → payment instruction → persistence → notification. If step 4 succeeds and step 5 fails, the system is in a **partially completed, inconsistent state**.

FlowRescue solves this with four capabilities:
1. **Workflow tracking** — every step's state is durably persisted
2. **Failure classification** — transient vs. terminal vs. business failure
3. **Smart retry** — immediate retry, or scheduled delayed retry via EventBridge Scheduler
4. **Saga compensation** — reverse completed side-effect steps in reverse order

---

## 2. Architecture

```
Client
  └── POST /api/v1/workflows/claims
        └── workflow-api (Spring Boot, :8080)
              ├── DynamoDB: creates WorkflowExecution record
              ├── SQS: publishes to workflow-task-queue
              └── WebSocket: broadcasts to dashboard

task-runner (Spring Boot, :8083)
  └── polls workflow-task-queue
        └── WorkflowOrchestrator (Java state machine, mirrors ASL)
              ├── runs 6 steps in order
              ├── updates completedSteps in DynamoDB on each success
              └── publishes to recovery-queue on failure

recovery-service (Spring Boot, :8081)
  └── polls recovery-queue
        └── FailureClassifier
              ├── RETRY_NOW     → re-publishes to workflow-task-queue immediately
              ├── RETRY_LATER   → saves to RecoverySchedule, @Scheduled fires later
              ├── COMPENSATE    → publishes to compensation-queue
              └── MANUAL_REVIEW → publishes to manual-review-queue

compensation-service (Spring Boot, :8082)
  └── polls compensation-queue
        └── CompensationService
              ├── iterates stepsToCompensate in reverse order
              ├── calls per-step compensation handlers
              └── marks workflow COMPENSATED in DynamoDB

dashboard (React + Vite, :5173)
  └── WebSocket STOMP subscription to /topic/workflows/list
        └── live table, step timeline, failure injection, action buttons
```

---

## 3. Tech Stack

| Layer | Technology |
|---|---|
| API | Spring Boot 3.2, Java 21 |
| Workflow Engine | Java state machine (mirrors AWS Step Functions ASL) |
| Message Queue | Amazon SQS (LocalStack in dev) |
| State Store | Amazon DynamoDB (LocalStack in dev) |
| Delayed Retry | EventBridge Scheduler simulation via @Scheduled |
| Dashboard | React 18 + Vite, WebSocket STOMP |
| Infrastructure | Terraform, Docker Compose |
| Cloud Target | AWS Step Functions Standard + Lambda |

---

## 4. Services

| Service | Port | Responsibility |
|---|---|---|
| `workflow-api` | 8080 | REST API, idempotency, WebSocket |
| `task-runner` | 8083 | Step execution, failure injection |
| `recovery-service` | 8081 | Failure classification, retry scheduling |
| `compensation-service` | 8082 | Reverse saga compensation |
| `localstack` | 4566 | AWS emulation (DynamoDB, SQS) |
| `dashboard` | 5173 | Live monitoring UI |

---

## 5. Data Model

### WorkflowExecution (DynamoDB)
```
PK: workflowId
─────────────────────────────────────────────────────────
status:          CREATED|RUNNING|FAILED|RETRY_SCHEDULED|
                 COMPENSATING|COMPENSATED|SUCCESS|MANUAL_REVIEW
currentStep:     <step name>
completedSteps:  ["VALIDATE_MEMBER", "CHECK_DUPLICATE_CLAIM", ...]
retryCount:      3
nextRetryAt:     2026-04-20T18:00:00Z
lastErrorCode:   DB_WRITE_FAILED
failedStep:      PERSIST_CLAIM_DECISION
version:         4
```

### IdempotencyRecord (DynamoDB)
```
PK: idempotencyKey (from Idempotency-Key header)
────────────────────────────────────────────────
requestHash:  SHA-256 of normalized request body
workflowId:   wf_abc123...
status:       IN_PROGRESS | COMPLETED
expiryEpoch:  TTL (7 days)
```

### AuditTrail (DynamoDB)
```
PK: workflowId  SK: eventTime
──────────────────────────────
eventType:  WORKFLOW_STARTED | STEP_COMPLETED | COMPENSATION_STEP_SUCCESS | ...
stepName:   <step that changed>
fromStatus: RUNNING
toStatus:   COMPENSATING
message:    Human-readable description
actor:      workflow-api | recovery-service | compensation-service
```

---

## 6. Idempotency

Every workflow request requires an `Idempotency-Key` header.

```http
POST /api/v1/workflows/claims
Idempotency-Key: 4e26-8fbc-...

{
  "claimId": "CLM-10001",
  "memberId": "MBR-771",
  ...
}
```

**Flow:**
1. SHA-256 hash the normalized request body
2. Attempt conditional insert into `IdempotencyRecord`
3. If key exists with same hash → return existing workflow response (idempotent replay)
4. If key exists with different hash → reject (idempotency key misuse)
5. If new → reserve key and proceed

---

## 7. Failure Classification

```
FailureClassifier rules (priority order):
────────────────────────────────────────
1. INVALID_MEMBER_ID, DUPLICATE_CLAIM_DETECTED → FAIL_FINAL
2. retryable + retryCount < 3                   → RETRY_NOW
3. retryable + retryCount < 5                   → RETRY_LATER (15min/1hr/6hr backoff)
4. max retries + side-effect steps completed    → COMPENSATE
5. max retries + no side effects                → MANUAL_REVIEW
```

---

## 8. Compensation

When a workflow fails after side-effect steps have completed, compensation runs **in reverse**:

```
Forward:  → CreatePaymentInstruction → PersistClaimDecision → SendNotification →
Reverse:  ← CancelPaymentInstruction ← RevertClaimDecision ← SendRetraction ←
```

Only steps that have side effects are compensated. Pure read/calculate steps have no compensation needed.

---

## 9. API Reference

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/workflows/claims` | Start claim workflow (requires `Idempotency-Key`) |
| `GET` | `/api/v1/workflows` | List all workflows |
| `GET` | `/api/v1/workflows/{id}` | Get workflow status |
| `POST` | `/api/v1/workflows/{id}/recover` | Force recovery |
| `POST` | `/api/v1/workflows/{id}/compensate` | Manual compensate |
| `POST` | `/api/v1/workflows/{id}/replay` | Replay from step |
| `GET` | `/api/v1/inject` | Get failure injection flags |
| `POST` | `/api/v1/inject/{step}` | Toggle step failure |
| `DELETE` | `/api/v1/inject` | Reset all flags |

---

## 10. Local Run Instructions

### Prerequisites
- Docker Desktop (with at least 4GB RAM)
- Java 21 + Maven 3.9
- Node.js 20+

### Quick start

```bash
# 1. Build all Java services
mvn clean package -DskipTests

# 2. Start everything
docker compose up --build

# 3. Open dashboard
open http://localhost:5173

# 4. Run demo flows
bash scripts/demo.sh
```

### Dev mode (no Docker)

```bash
# Start LocalStack only
docker compose up localstack

# Run services individually
cd workflow-api      && mvn spring-boot:run &
cd task-runner       && mvn spring-boot:run &
cd recovery-service  && mvn spring-boot:run &
cd compensation-service && mvn spring-boot:run &
cd dashboard         && npm run dev
```

---

## 11. Failure Scenarios Tested

| Scenario | Trigger | Expected Result |
|---|---|---|
| Transient DB failure | `FAIL_CLAIM_PERSISTENCE=true` | Auto-retry 3x → SUCCESS |
| Max retries exhausted | `FAIL_CLAIM_PERSISTENCE=true` + 3+ attempts | COMPENSATE → COMPENSATED |
| Duplicate claim | `claimId = CLM-DUP-xxx` | FAIL_FINAL (no retry) |
| Invalid member ID | `memberId = "BAD"` | FAIL_FINAL immediately |
| Manual recovery | Dashboard "Force Retry" | Re-runs from failed step |
| Manual compensate | Dashboard "Compensate" | Triggers full compensation chain |

---

## 12. Why Step Functions Standard (Not Express)?

- Standard Workflows are **durable** — execution history survives restarts
- They can run for **up to 1 year** — appropriate for delayed human review
- Native **execution history** in the AWS console — no CloudWatch required
- Express Workflows (max 5 min, event sourced) are wrong for this use case

---

## 13. Resume Bullets

- Built a distributed workflow recovery engine using Spring Boot, DynamoDB, SQS, and EventBridge Scheduler to orchestrate multi-step claim processing with automatic failure recovery
- Implemented saga compensation to reverse side-effecting steps (payment instruction, claim persistence) in reverse order after max retries are exhausted
- Designed a deterministic failure classifier distinguishing transient errors (retry now), dependency outages (delayed retry), and terminal business failures (manual review or fail-final)
- Added idempotency guards using SHA-256 hashed request bodies and conditional DynamoDB writes, preventing duplicate workflows during concurrent retries
- Delivered real-time workflow monitoring via STOMP WebSocket with a React dashboard showing live step progression, retry history, and compensation flows

---

## 14. Future Improvements

- [ ] AI incident summarizer — LLM generating structured root cause + recommended action
- [ ] Human approval wait state for high-value claims
- [ ] Transactional outbox pattern using DynamoDB Streams
- [ ] Chaos testing with automated failure injection sequences
- [ ] Prometheus + Grafana observability layer
#   F l o w R e s c u e  
 #   F l o w R e s c u e  
 