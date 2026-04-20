# FlowRescue ⚡
### Distributed Workflow Failure Recovery Engine

> A self-healing workflow orchestration system that detects failed distributed steps, retries safely, and executes saga compensation logic to restore consistency across services — built for healthcare claim processing.

![Java](https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2-brightgreen?style=flat-square&logo=springboot)
![React](https://img.shields.io/badge/React-18-61DAFB?style=flat-square&logo=react)
![AWS](https://img.shields.io/badge/AWS-LocalStack-FF9900?style=flat-square&logo=amazonaws)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square&logo=docker)

---

## Problem Statement

In distributed systems, a business process rarely fits in a single database transaction.

A healthcare claim touches multiple services: **validation → duplicate check → pricing → payment instruction → persistence → notification**. If step 4 succeeds and step 5 fails, the system is left in a **partially completed, inconsistent state**.

FlowRescue solves this with four capabilities:

1. **Workflow tracking** — every step's state is durably persisted in DynamoDB
2. **Failure classification** — transient vs. terminal vs. business failure
3. **Smart retry** — immediate retry, or scheduled delayed retry with exponential backoff
4. **Saga compensation** — reverse completed side-effect steps in reverse order

---

## Architecture

```
Client
  └── POST /api/v1/workflows/claims
        └── workflow-api  (Spring Boot :8080)
              ├── DynamoDB → creates WorkflowExecution record
              ├── SQS     → publishes to workflow-task-queue
              └── WS      → broadcasts to dashboard

task-runner  (Spring Boot :8083)
  └── polls workflow-task-queue
        └── WorkflowOrchestrator (Java state machine)
              ├── runs 6 steps sequentially
              ├── writes completedSteps[] to DynamoDB per success
              └── publishes FailureEvent to recovery-queue on error

recovery-service  (Spring Boot :8081)
  └── polls recovery-queue
        └── FailureClassifier
              ├── RETRY_NOW     → re-enqueues to workflow-task-queue
              ├── RETRY_LATER   → saves RetrySchedule, @Scheduled re-fires
              ├── COMPENSATE    → publishes to compensation-queue
              └── MANUAL_REVIEW → publishes to manual-review-queue

compensation-service  (Spring Boot :8082)
  └── polls compensation-queue
        └── CompensationService
              ├── iterates completedSteps[] in reverse order
              ├── invokes per-step compensation handlers
              └── marks workflow COMPENSATED in DynamoDB

dashboard  (React + Vite :5173)
  └── WebSocket STOMP /topic/workflows/list
        └── live table · step timeline · failure injection · action buttons
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| API Services | Spring Boot 3.2, Java 21, Lombok |
| State Store | Amazon DynamoDB (LocalStack in dev) |
| Message Queue | Amazon SQS (LocalStack in dev) |
| Workflow Engine | Java state machine (mirrors AWS Step Functions ASL) |
| Delayed Retry | EventBridge Scheduler simulation via `@Scheduled` |
| Dashboard | React 18 + Vite 5, WebSocket STOMP, Recharts |
| Infrastructure | Terraform, Docker Compose |
| Cloud Target | AWS Step Functions Standard + ECS/Lambda |

---

## Services

| Service | Port | Responsibility |
|---|---|---|
| `workflow-api` | 8080 | REST API, idempotency guard, WebSocket broadcaster |
| `task-runner` | 8083 | 6-step orchestrator, configurable failure injection |
| `recovery-service` | 8081 | Failure classification, retry + compensation scheduling |
| `compensation-service` | 8082 | Reverse-order saga compensation planner |
| `localstack` | 4566 | AWS emulation (DynamoDB + SQS) |
| `dashboard` | 5173 | Real-time monitoring UI |

---

## Data Model

### WorkflowExecution (DynamoDB)

| Field | Type | Description |
|---|---|---|
| `workflowId` | String (PK) | Unique workflow ID |
| `status` | Enum | `CREATED \| RUNNING \| FAILED \| RETRY_SCHEDULED \| COMPENSATING \| COMPENSATED \| SUCCESS \| MANUAL_REVIEW` |
| `currentStep` | String | Currently executing step name |
| `completedSteps` | List\<String\> | Steps completed so far |
| `retryCount` | Number | Total retry attempts |
| `nextRetryAt` | String | ISO-8601 scheduled retry time |
| `lastErrorCode` | String | e.g. `DB_WRITE_FAILED` |
| `failedStep` | String | Step that triggered failure |
| `version` | Number | Optimistic locking counter |

---

## Idempotency

Every workflow request requires an `Idempotency-Key` header.

```http
POST /api/v1/workflows/claims
Idempotency-Key: 4e26-8fbc-a1b2-...
Content-Type: application/json

{
  "claimId": "CLM-10001",
  "memberId": "MBR-771",
  "providerId": "PRV-100",
  "amount": 1250.00
}
```

**Flow:**
1. SHA-256 hash the normalized request body
2. Conditional insert into `IdempotencyRecord` table
3. Key exists + same hash → return existing workflow (idempotent replay)
4. Key exists + different hash → `409 Conflict` (key misuse)
5. New key → reserve and proceed

---

## Failure Classification

```
FailureClassifier  (priority order)
────────────────────────────────────────────────────────────
1. INVALID_MEMBER_ID, DUPLICATE_CLAIM_DETECTED  →  FAIL_FINAL
2. retryable  +  retryCount < 3                 →  RETRY_NOW
3. retryable  +  retryCount < 5                 →  RETRY_LATER  (15 min → 1 hr → 6 hr)
4. max retries  +  side-effect steps completed  →  COMPENSATE
5. max retries  +  no side effects              →  MANUAL_REVIEW
```

---

## Saga Compensation

When a workflow fails after side-effect steps completed, compensation runs **in reverse**:

```
Forward:   ValidateMember → DuplicateCheck → Pricing → CreatePaymentInstruction → PersistClaim → SendNotification
Reverse:                                              ← CancelPaymentInstruction ← RevertClaim ← SendRetraction
```

Only steps with side effects are compensated. Read-only / calculate-only steps are skipped.

---

## API Reference

### Workflow API (port 8080)

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/workflows/claims` | Start claim workflow _(requires `Idempotency-Key`)_ |
| `GET` | `/api/v1/workflows` | List all workflows |
| `GET` | `/api/v1/workflows/{id}` | Get workflow status |
| `POST` | `/api/v1/workflows/{id}/recover` | Force recovery / retry |
| `POST` | `/api/v1/workflows/{id}/compensate` | Manual compensate |
| `POST` | `/api/v1/workflows/{id}/replay` | Replay from a step |

### Failure Injection API (port 8083)

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/inject` | Get all injection flags |
| `POST` | `/api/v1/inject/{step}` | Toggle step failure on/off |
| `DELETE` | `/api/v1/inject` | Reset all flags |

---

## Quick Start

### Prerequisites
- **Docker Desktop** (4 GB RAM minimum)
- **Node.js 20+** (for dashboard dev mode only)

### Run the full stack

```bash
# Clone
git clone https://github.com/Priyansh-6216/FlowRescue.git
cd FlowRescue

# Start everything (multi-stage Docker build — no local Maven needed)
docker compose up --build

# Open dashboard
open http://localhost:5173

# Run demo flows (happy path, failure, compensation)
bash scripts/demo.sh
```

### Dashboard dev mode (hot reload)

```bash
cd dashboard
npm install
npm run dev
# → http://localhost:5173
```

---

## Demo Scenarios

| Scenario | How to Trigger | Expected Outcome |
|---|---|---|
| ✅ Happy path | Submit claim via UI | All 6 steps complete → `SUCCESS` |
| 🔁 Transient failure | Toggle `FAIL_CLAIM_PERSISTENCE` on | Auto-retry 3× → `SUCCESS` |
| ↩️ Compensation | Toggle failure + wait for max retries | `COMPENSATING` → `COMPENSATED` |
| 🚫 Duplicate claim | Submit same `claimId` twice | `FAIL_FINAL` immediately |
| 🔥 Manual inject | Dashboard → Inject Failure panel | Real-time step failure visible |

---

## Key Engineering Decisions

**Why Step Functions Standard (not Express)?**
Standard Workflows are durable (up to 1 year), survive restarts, and maintain full execution history. Express Workflows (5-min max, event-sourced) are wrong for long-running claim review processes.

**Why DynamoDB for workflow state (not RDS)?**
Single-table design gives sub-millisecond writes per step with no connection pooling overhead. `completedSteps[]` as a DynamoDB List is the natural container for the saga's step log.

**Why SQS (not Kafka) for inter-service messaging?**
SQS dead-letter queues, at-least-once delivery, and visibility timeout map perfectly to retry semantics. Kafka adds ordering and consumer-group complexity that this use case doesn't need.

---

## Resume Bullets

- Built a **distributed workflow recovery engine** using Spring Boot, DynamoDB, and SQS to orchestrate 6-step healthcare claim processing with automatic failure detection and recovery
- Implemented **saga compensation** that reverses side-effecting steps (payment instruction, claim persistence) in reverse order after max retries are exhausted
- Designed a **deterministic failure classifier** distinguishing transient errors (retry now), dependency outages (delayed retry with backoff), and terminal business failures (manual review)
- Added **idempotency guards** using SHA-256 hashed request bodies and conditional DynamoDB writes, preventing duplicate workflows during concurrent retries
- Delivered **real-time workflow monitoring** via STOMP WebSocket with a React dashboard showing live step progression, retry history, and compensation flows with amber/dark premium UI

---

## Future Improvements

- [ ] AI incident summarizer — LLM generating structured root cause + recommended action
- [ ] Human-in-the-loop wait state for high-value claims requiring approval
- [ ] Transactional outbox pattern using DynamoDB Streams → EventBridge
- [ ] Chaos testing with automated failure injection sequences
- [ ] Prometheus + Grafana observability layer