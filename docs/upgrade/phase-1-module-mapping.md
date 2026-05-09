# Phase 1 - Module Mapping and Boundary Baseline

## Goal
Build a clear module-to-capability map for the current project, so later microservice splitting and UI/API evolution can proceed without repeated refactors.

## Current Project Layer Mapping

| Layer | Module | Core Responsibility | Representative Files |
|---|---|---|---|
| API Contract | `group-buy-market-yizhou-api` | External service contracts and DTOs | `group-buy-market-yizhou-api/src/main/java/cn/bugstack/api/IMarketTradeService.java` |
| Inbound Trigger | `group-buy-market-yizhou-trigger` | HTTP entry points, scheduled tasks, external access | `group-buy-market-yizhou-trigger/src/main/java/cn/bugstack/trigger/http/MarketTradeController.java`, `group-buy-market-yizhou-trigger/src/main/java/cn/bugstack/trigger/http/DCCController.java` |
| Domain | `group-buy-market-yizhou-domain` | Activity trial calculation, lock/settlement domain services | `group-buy-market-yizhou-domain/src/main/java/cn/bugstack/domain/trade/service/ITradeLockOrderService.java`, `group-buy-market-yizhou-domain/src/main/java/cn/bugstack/domain/trade/service/ITradeSettlementOrderService.java` |
| Infrastructure | `group-buy-market-yizhou-infrastructure` | Repository, persistence, cache/message integration | `group-buy-market-yizhou-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/repository/TradeRepository.java` |
| Shared Types/Template | `group-buy-market-yizhou-types` | Generic rule tree/chain templates and common enums/exceptions | `group-buy-market-yizhou-types/src/main/java/cn/bugstack/types/design/framework/tree/AbstractMultiThreadStrategyRouter.java`, `group-buy-market-yizhou-types/src/main/java/cn/bugstack/types/design/framework/link/AbstractLogicLink.java` |
| Boot App | `group-buy-market-yizhou-app` | Runtime assembly and config wiring | `group-buy-market-yizhou-app/src/main/java/cn/bugstack/config/ThreadPoolConfig.java`, `group-buy-market-yizhou-app/src/main/resources/mybatis/mapper/notify_task_mapper.xml` |

## Capability-to-Module Baseline

| Target Capability | Existing Baseline | Gap to Fill in Next Phase |
|---|---|---|
| Rule tree + strategy trial engine | Existing trial flow and rule templates in `domain` + `types` | Make rules configurable and replayable (gray publish + rollback)
| Pre-settlement validation chain | Existing chain model in `types` | Add explicit risk, idempotency, and order-validity chain nodes
| Group seat atomic occupancy | Current lock flow in trade service | Add Redis Lua atomic check/deduct and idempotency key
| Reliable async persistence | Existing notify task table + job polling | Add MQ channel, manual ACK, DLQ, and outbox consistency
| Trusted callback | Existing `notify_task` persistence and retry basis | Build HTTP + MQ dual-channel callback with status machine
| Dynamic config center | Existing DCC API + Redis topic publish | Add config audit, namespace isolation, and rollback controls
| Dynamic thread pool | Existing configurable thread pool bean | Extract starter, expose runtime metrics and online tuning API
| Observability | Log files available in `data/log` | Add ELK indexing and Prometheus metrics dashboards
| Frontend UI | Existing static pages under `docs/ui/html` | Build unified admin + C-end views with consistent design system

## Proposed Future Service Boundaries (Logical First)

1. Growth Strategy Service
   - Responsibility: activity config, crowd tags, trial rule execution.
   - Current source: activity-domain services and trial strategy factories.
2. Trade Fulfillment Service
   - Responsibility: lock order, group progress, settlement, occupancy state.
   - Current source: trade-domain services and repository logic.
3. Callback Notification Service
   - Responsibility: callback persistence, retry workflow, idempotent delivery.
   - Current source: notify task mapper + scheduled job.
4. Config and Governance Service
   - Responsibility: DCC config, thread pool runtime governance, degradation/rate-limit switches.
   - Current source: DCC controller + app config.
5. BFF/Gateway Service
   - Responsibility: aggregate APIs for redesigned UI and multi-business frontends.

## Phase-1 Deliverables Checklist

- [x] Module and capability mapping baseline
- [x] Candidate service boundary draft
- [x] Core table ownership map (`group_buy_*`, `crowd_tags_*`, `notify_task`) -> `docs/upgrade/phase-1-table-ownership.md`
- [x] API ownership map (which service owns each endpoint) -> `docs/upgrade/phase-1-api-ownership.md`
- [x] Sequence diagram for `trial -> lock -> settlement -> notify` -> `docs/upgrade/phase-1-sequence-trial-lock-settlement-notify.md`

## Acceptance Criteria for Phase 1

- Every core endpoint has one clear owner service.
- Every core table has one clear owner service.
- The end-to-end flow `trial -> lock -> settlement -> notify` is documented with clear inputs/outputs.
- Team can start Phase 2 without changing module boundaries again.

## Next Action (Recommended)

Move to Phase 2 with a minimal vertical slice:
- Add Redis Lua atomic occupancy in lock flow.
- Add MQ async persistence for settlement/notify events.
- Keep existing HTTP behavior unchanged to reduce migration risk.
