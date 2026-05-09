# Phase 1 - Table Ownership Map

## Goal
Define a single owner for each core table before microservice splitting, to avoid write-conflicts and unclear responsibilities.

## Ownership Principles

- One table has one primary write owner service.
- Other services read through API/event, not direct cross-service writes.
- Cross-service synchronization uses event payloads (future MQ) and idempotent keys.

## Table Ownership (Proposed)

| Table | Current Source | Proposed Owner Service | Access Rule |
|---|---|---|---|
| `group_buy_activity` | `group_buy_activity_mapper.xml` | Growth Strategy Service | Write by strategy service only; trade service read via API/cache snapshot |
| `group_buy_discount` | `group_buy_discount_mapper.xml` | Growth Strategy Service | Write by strategy service only |
| `sc_sku_activity` | `sc_sku_activity_mapper.xml` | Growth Strategy Service | Maintain SKU-activity mapping in strategy domain |
| `crowd_tags` | `crowd_tags_mapper.xml` | Growth Strategy Service | Tag statistics centralized in strategy service |
| `crowd_tags_detail` | `crowd_tags_detail_mapper.xml` | Growth Strategy Service | User-tag write in strategy service; query via API |
| `crowd_tags_job` | `crowd_tags_job_mapper.xml` | Growth Strategy Service | Batch tag rules and execution window managed centrally |
| `sku` | `sku_mapper.xml` | Growth Strategy Service (or Product Base Service) | If later product service exists, migrate ownership there |
| `group_buy_order` | `group_buy_order_mapper.xml` | Trade Fulfillment Service | Team seat lock/complete counters and status are trade-owned |
| `group_buy_order_list` | `group_buy_order_list_mapper.xml` | Trade Fulfillment Service | User order lifecycle and payment status are trade-owned |
| `notify_task` | `notify_task_mapper.xml` | Callback Notification Service | Notification state machine write only by callback service |

## Key Risks and Constraints

- `group_buy_order` and `group_buy_order_list` currently co-evolve in one transaction path; splitting must preserve consistency through event choreography.
- `notify_task` currently depends on trade settlement result; enforce outbox/event contract to avoid missing notifications.
- `sku` table ownership may shift if a dedicated commodity center appears later.

## Migration Notes for Phase 2

1. Keep physical DB unchanged first; enforce logical ownership in code.
2. Add event contracts:
   - `TradeOrderSettledEvent`
   - `GroupTeamCompletedEvent`
3. Callback service consumes settlement event and writes `notify_task` idempotently.
4. After traffic stabilizes, consider physical database split.

## Acceptance Criteria

- Every core table has one owner service and one write entry.
- No direct cross-service table writes in new code.
- Settlement-to-notify path can be traced by `teamId` and `outTradeNo`.
