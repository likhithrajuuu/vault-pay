# VaultPay Requirements and Scope

Supporting detail for the [initial system design proposal](HLD.md). Requirements and sizing assumptions remain provisional until reviewed; the proposal records design choices, alternatives, and open decisions. Update both documents when review changes an assumption.

VaultPay is a distributed digital wallet and payment processing platform for user authentication, wallet management, simulated transfers, transaction history, notifications, and financial reporting. It demonstrates production-oriented backend engineering practices, including concurrency, distributed communication, consistency, replication, fault tolerance, observability, and horizontal scalability.

All funds are simulated and have no real monetary value. This document defines requirements, scope, and provisional capacity and deployment assumptions. These assumptions guide later architecture decisions and do not represent measured system capabilities.

## 1. Scope

### 1.1 In scope

- User registration, authentication, session management, and authorization.
- One wallet per user in a single platform currency for the initial release.
- Wallet creation, balance retrieval, and controlled simulated funding for testing.
- Internal payments through transfers between registered users' wallets.
- Transaction lifecycle tracking, transaction history, and transaction details.
- Notifications about funding and transfer outcomes through at least one delivery channel.
- User financial summaries and restricted platform reporting and reconciliation.
- Backend APIs and the operational capabilities needed to evaluate correctness, security, resilience, and scalability.
- Testing concurrent requests, retries, service interruptions, replication behavior, and recovery using simulated funds.

### 1.2 Out of scope

- Real money, bank accounts, cards, payment gateways, and external payment networks.
- Real deposits, withdrawals, payouts, clearing, and settlement.
- Production identity verification, KYC/AML screening, and regulated financial operations.
- Foreign exchange, multiple currencies, and cross-border payments.
- Merchant checkout, subscriptions, recurring payments, and scheduled transfers.
- Loans, credit, interest, investments, and cryptocurrency.
- Formal chargeback and payment dispute workflows; automated reversal of failed internal transfers remains in scope where needed to preserve balances.
- Advanced fraud detection and risk scoring beyond basic authorization, validation, and abuse controls.
- Production multi-region active-active operation and geographically distributed settlement.
- A polished customer-facing web or mobile application; API clients or a minimal interface may be used for demonstrations.

## 2. Functional Requirements

| ID | Requirement |
| --- | --- |
| FR-01 | Users shall be able to register with a unique login identifier, authenticate, and log out. Invalid credentials and expired or revoked sessions shall be rejected. |
| FR-02 | The platform shall authorize every protected operation. Users shall access only their own wallet, transactions, notifications, and reports; platform-wide reporting and test-funding controls shall require explicitly authorized access. |
| FR-03 | The platform shall create one wallet for each registered user and expose its identifier, currency, status, and current spendable balance. Duplicate creation requests shall not create additional wallets. |
| FR-04 | Authorized users or test tools shall be able to add simulated funds through a controlled funding operation. Each successful funding operation shall create an auditable transaction record. |
| FR-05 | Users shall be able to transfer a positive amount from their wallet to another registered user's active wallet. The platform shall reject self-transfers, unsupported currencies, invalid amounts, unavailable wallets, and insufficient funds. |
| FR-06 | A completed transfer shall debit the sender and credit the recipient exactly once. Failed transfers shall not leave a permanent partial debit or credit. Pending transfers shall reserve or otherwise protect funds from being spent again. |
| FR-07 | Funding and transfer requests shall support idempotency keys. Repeating a request with the same key and payload shall return the existing operation without applying another balance change; reuse with a different payload shall be rejected. |
| FR-08 | Each transaction shall have a unique identifier, type, amount, currency, relevant wallet identifiers, timestamps, and a status such as pending, completed, or failed. Users shall be able to retrieve its current status and any safe-to-expose failure reason. |
| FR-09 | Users shall be able to view paginated transaction history and transaction details for their wallet, with filters for date range, transaction type, and status. |
| FR-10 | The platform shall record an append-only history of monetary postings and transaction state changes. Completed monetary records shall be corrected through linked compensating entries rather than edits or deletion. |
| FR-11 | The platform shall notify affected users of completed funding and completed or failed transfers. Notification failures shall be retried without changing the financial outcome or creating duplicate user-visible notifications for the same event. |
| FR-12 | Users shall be able to obtain summaries of completed incoming and outgoing transactions, funding, and net balance changes for a selected period. Reports shall identify their reporting period and data freshness. |
| FR-13 | Authorized operators shall be able to view aggregate transaction volumes, amounts, outcomes, and outstanding pending transactions. Reconciliation shall identify discrepancies between wallet balances and monetary records and flag transactions requiring recovery. |
| FR-14 | The platform shall identify and recover interrupted or stalled operations so they eventually reach a completed or failed state. Recovery actions shall preserve idempotency and produce auditable records. |

## 3. Non-Functional Requirements

### 3.1 Correctness and consistency

- Monetary values shall use exact representations, such as integer minor units or fixed-precision decimals, with a defined currency precision. Floating-point rounding shall not affect balances.
- Concurrent operations shall never produce a negative spendable balance, spend the same funds twice, or lose a balance update.
- Internal transfers shall conserve total simulated funds. Only explicit, recorded funding operations shall increase the platform's total funds in the initial release.
- A completed transaction status shall be exposed only after its financial outcome is durably committed. An ambiguous response or timeout shall be resolvable by querying or retrying with the original idempotency key.
- Balance checks and spending decisions shall use authoritative state that includes committed changes and pending reservations. Stale replicas shall not authorize spending.
- Transaction history, notifications, and reports may update asynchronously, but shall converge to the committed financial outcome and make processing delays or freshness visible where relevant.

### 3.2 Reliability, fault tolerance, and recovery

- Acknowledged monetary operations shall survive application restarts and supported database failover scenarios without duplication or loss.
- Service failures, timeouts, and repeated or out-of-order messages shall not violate financial invariants. Retryable work shall use bounded retries and provide a way to inspect and recover exhausted failures.
- Notification and reporting outages shall not prevent otherwise valid transfers from completing.
- Replication, backup, restore, and failover behavior shall be documented and exercised. Recovery shall reconcile balances, transaction records, and pending work before normal processing resumes.
- Availability, recovery time objective (RTO), and recovery point objective (RPO) shall be validated through failure and restore exercises against the provisional targets in Section 7.

### 3.3 Performance and scalability

- APIs shall provide bounded pagination and request limits so history and reporting workloads do not grow without control.
- The platform shall support horizontal scaling of request processing and background work while preserving authorization, idempotency, and financial correctness across instances.
- Load tests shall cover ordinary traffic, contention on a single wallet, duplicate requests, and growing transaction history. Results shall report throughput, error rates, and p50, p95, and p99 latency.
- Performance acceptance shall use the provisional workload, storage, latency, and availability targets in Sections 5–7, revising them when measurements or product requirements change. These are validation targets, not established production guarantees.

### 3.4 Security and privacy

- Credentials shall be securely hashed, secrets shall remain outside source control, and network communication shall use encryption in deployed environments.
- Sessions shall expire and support revocation. Sensitive endpoints shall enforce authorization, input validation, and rate limits.
- Databases, background workers, and operational tools shall use least-privilege access. Test-funding capabilities shall be explicitly restricted.
- Logs, traces, notifications, and error responses shall not expose passwords, access tokens, secrets, or unnecessary personal information.
- Authentication events, restricted operations, and monetary state changes shall have auditable records with controlled access.

### 3.5 Observability and operability

- Structured logs and distributed traces shall correlate API requests, transaction identifiers, and background processing across service boundaries.
- Metrics shall cover request latency and failures, transfer outcomes, pending transaction age, queue lag, retries, notification failures, replication lag where applicable, and reconciliation discrepancies.
- Services shall expose health and readiness signals. Alerts and recovery procedures shall cover critical failures and financial invariant violations.
- Configuration changes, deployments, and graceful shutdowns shall preserve in-flight work or leave it recoverable.

### 3.6 Maintainability and testability

- APIs, transaction states, error responses, and distributed message contracts shall be documented and versioned when compatibility requires it.
- Automated validation shall cover financial invariants, authorization, idempotency, concurrent spending, partial failures, duplicate message delivery, and recovery.
- Development and test environments shall be reproducible and use simulated funds and test identities.
- Data migrations shall preserve monetary history and consistency, with documented deployment and recovery procedures.

## 4. Consistency and the CAP Tradeoff

CAP concerns consistency, availability, and network partitions. When a partition prevents nodes from communicating, a distributed system cannot guarantee both linearizable consistency and availability for every request. The choice during a partition is between consistency and availability; assuming partitions never occur does not make the system partition tolerant. See the [Gilbert–Lynch CAP theorem paper](https://www.comp.nus.edu.sg/~gilbert/pubs/BrewersConjecture-SigAct.pdf).

For VaultPay's monetary operations, the proposed policy is **consistency over availability during partitions (CP)**. Choosing availability on both isolated sides would require relaxing the current strong consistency requirement. For example, two isolated nodes must not each approve a transfer of 80 from the same wallet with a balance of 100.

| Operation | Consistency expectation | Behavior during a partition |
| --- | --- | --- |
| Funding, transfers, and authoritative balances | Completed operations appear in a single order that respects completion time; transfers also preserve atomicity across both wallets. | Process only where authoritative state and safe commit are available. Otherwise return a retryable unavailable response or time out without claiming completion. |
| Transfer status used to resolve an uncertain result | Authoritative status; absence from a stale replica is not proof of failure. | Query authoritative state or report that the result cannot yet be determined. Retry with the original idempotency key. |
| History and reports | Eventually consistent projections with visible freshness. | Serve available projections if authorization can still be enforced; delay updates when dependencies are unreachable. |
| Notifications | Asynchronous delivery of committed outcomes. | Delay delivery and retry after connectivity returns. |

CAP consistency refers to linearizability, which is distinct from the consistency property in ACID. VaultPay also needs atomic transfers and appropriate transaction isolation to preserve financial invariants. Selecting a database with ACID transactions alone does not establish the required behavior across replicas and failover.

CP does not require a total outage during every partition: a safely elected leader with the required replicas may continue serving reachable clients. Isolated nodes must not independently accept monetary writes. Returning an unavailable error protects correctness but does not satisfy CAP availability for that operation. A practical uptime target such as 99.9% is a separate operational objective.

## 5. User and Traffic Assumptions

These estimates describe a steady-state planning workload for a demonstration deployment, not observed usage. A day has 86,400 seconds; calculations use decimal KB and GB. Monetary requests are included in total API traffic, not added to it.

| Assumption | Initial value |
| --- | --- |
| Registered users and wallets | 100,000 each |
| Daily active users (DAU) | 10,000, or 10% of registered users |
| API requests per active user per day | 20 |
| Read/write request split | 80% reads, 20% writes |
| Transfers initiated per active user per day | 2; count a transfer once, not once per participant |
| Simulated funding operations per day | 2,000 |
| Peak-to-average traffic multiplier | 10× |
| Planning period | One year at constant traffic and user count |

| Calculation | Result |
| --- | --- |
| Daily API requests = 10,000 × 20 | 200,000 requests/day |
| Average API throughput = 200,000 / 86,400 | Approximately 2.31 requests/second (RPS) |
| Estimated peak throughput = 2.31 × 10 | Approximately 23.15 RPS, using unrounded inputs |
| Estimated peak reads / writes = peak RPS × 80% / 20% | Approximately 18.52 read RPS / 4.63 write RPS |
| Daily transfers = 10,000 × 2 | 20,000 transfers/day |
| Daily monetary operations = 20,000 + 2,000 | 22,000 operations/day |
| Average / peak monetary throughput | Approximately 0.255 / 2.55 operations/second |

The 40,000 daily write requests include 22,000 monetary requests and an assumed 18,000 authentication, profile, and other state-changing requests. Database writes and background messages will exceed API write counts because one monetary operation produces several records. Retries, incident backlogs, and unusual bursts require separate load scenarios.

Use **50 sustained API RPS** with the same traffic mix as an initial load-test target, providing approximately 2× estimated peak headroom. Under normal healthy conditions, target p95 latency of at most **200 ms for ordinary reads** and **500 ms for funding and transfers**, excluding asynchronous notification and report completion. These targets require measurement and do not apply as response-time guarantees during outages.

At 50 RPS and an assumed mean response time of 0.5 seconds, Little's law gives approximately `50 × 0.5 = 25` requests in flight. This is an illustrative concurrency estimate, not a concurrent logged-in user count; measure the actual mean latency and session behavior during testing.

## 6. Storage and Deployment Assumptions

### 6.1 Storage baseline

- Use a transactional relational store as the proposed source of truth for users, wallets, monetary records, and idempotency records. Keep both sides of an initial internal transfer within one atomic transaction boundary.
- Assume three full database copies in one region: one active primary and two replicas across separate failure domains. This is a capacity assumption; the replication, leader election, and fencing design must still prove the consistency requirements in Section 4.
- Acknowledged monetary commits must be recoverable after any supported single-node or single-failure-domain loss. Replication acknowledgement and promotion rules must prevent promotion of a node missing acknowledged commits. An arbitrary asynchronous replica is not sufficient for this guarantee.
- Use caches and reporting projections only as derived data. They must not become the authority for spending decisions.
- Retain one year of financial records online for this exercise. No automatic financial-history deletion is authorized by this sizing assumption; later retention and archival policy must address longer operation.

### 6.2 Database capacity calculation

Assume an average **2 KB of raw persistent data per monetary operation**, covering the transaction, monetary postings, associated audit/state records, and amortized idempotency and event metadata. This is an aggregate record budget, not a single-row size. Assume **2 KB per registered user including their wallet**. Validate both against the implemented schema; failed requests, extra state transitions, and long-lived delivery records may increase them.

| Calculation | Estimated storage |
| --- | --- |
| Annual monetary operations = 22,000 × 365 | 8,030,000 operations |
| Annual monetary data = 8,030,000 × 2 KB | 16.06 GB |
| User and wallet data = 100,000 × 2 KB | 0.20 GB |
| Total raw database data | 16.26 GB |
| Data plus indexes and database overhead, assuming 2× raw size | 32.52 GB |
| Add 30% operational headroom = 32.52 × 1.30 | 42.28 GB per database copy |
| Three full copies = 42.276 × 3 | 126.83 GB across database nodes |

Provision at least **50 GB per database node (150 GB total)** for this baseline data estimate. This is not an all-inclusive disk recommendation: transaction logs, temporary queries, maintenance, and replication backlog require additional measured capacity. Do not count replicas as extra logical storage capacity.

### 6.3 Backups and operational data

- Budget separately for backups. Seven uncompressed daily full backups at an assumed 50 GB each require approximately **350 GB**, excluding point-in-time recovery logs and longer-term archives. Actual backup size depends on used data, compression, and backup strategy.
- At one 1 KB access-log record per API request, access logs alone consume approximately `200,000 × 1 KB = 0.20 GB/day`, or **6 GB for 30 days** before compression or replication. Application logs, security events, metrics, and traces are additional.
- Notification retention, event-broker storage, caches, and reporting databases are separate capacity budgets. Size any broker backlog as `events/second × bytes/event × retention seconds × replication factor`, allowing for peak traffic and delayed consumers.
- These estimates exclude attachments and identity documents, which are outside the initial feature set. Recalculate storage if traffic grows, retention changes, or financial history is retained beyond the first year.

## 7. Availability and Recovery Assumptions

### 7.1 Initial availability objective

Set a provisional **99.9% monthly availability objective** for the core wallet APIs, measured at the external API boundary. This is an engineering SLO, not an achieved result or contractual SLA. Partition-related failures still consume its error budget even though rejecting unsafe writes is the correct financial behavior.

For request-based measurement:

`Availability = good eligible requests / total eligible requests × 100`

An eligible request is a well-formed, authenticated request within published usage limits. A good response is a correct success or expected business rejection, such as insufficient funds, returned within a provisional 2-second availability deadline. Server errors, unexpected rate limiting within supported load, timeouts, and unavailable responses are bad outcomes. Malformed requests, invalid credentials, and requests exceeding published limits are excluded. Track core monetary endpoints separately so successful history reads do not hide transfer failures. The p95 performance targets in Section 5 are stricter, separate latency objectives.

At 200,000 eligible requests/day over 30 days, the illustrative monthly volume is **6,000,000 requests**. A 99.9% request-based objective permits at most `6,000,000 × 0.001 = 6,000` bad requests. Actual eligibility and traffic distribution determine the measured budget.

For time-based comparison:

`Allowed unavailability = observation period × (1 − availability fraction)`

| Availability target | Unavailability per 30-day month | Unavailability per 365-day year |
| --- | --- | --- |
| 99.9% — initial target | 43.2 minutes | 8.76 hours |
| 99.95% — comparison | 21.6 minutes | 4.38 hours |
| 99.99% — comparison | 4.32 minutes | 52.56 minutes |

These time budgets assume a time-based definition and are not interchangeable with request budgets under uneven traffic. Planned maintenance counts against the initial SLO. Synthetic probes are needed to detect outages during periods with no real requests.

### 7.2 Dependency budget and recovery

For an illustrative synchronous request path with four independently available required components, each at 99.99%, serial-path availability is `0.9999^4 ≈ 99.9600%`. If each is only 99.9%, the result is `0.999^4 ≈ 99.6006%`, below the proposed target. These are simplified budget calculations, not predictions: shared network failures, correlated outages, failover delays, and overload invalidate the independence assumption. Redundant replicas do not automatically establish an availability percentage.

- Assume redundant application instances and database failure domains in one region. Notification delivery and report generation remain outside the synchronous financial commit path.
- Target an **RTO of at most 5 minutes** and **RPO of zero acknowledged monetary operations** for a tested single database-node or failure-domain loss. A five-minute full outage would consume approximately 11.6% of the 43.2-minute monthly time budget. Verify safe failover and replay through drills.
- For complete regional loss, use a separate provisional disaster-recovery target of **RTO at most 24 hours** and **RPO at most 15 minutes**, dependent on independently stored backups and recovery-log shipping. This scenario is outside the zero-loss single-failure guarantee and can breach the monthly availability objective. Do not claim these targets until backup restore tests establish them.
- If required replicas cannot safely communicate, preserve monetary correctness and suspend affected operations. RTO targets do not authorize unsafe writes or promotion of stale state.
