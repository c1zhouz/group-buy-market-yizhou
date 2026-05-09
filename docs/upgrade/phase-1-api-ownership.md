# Phase 1 - API Ownership Map

## Goal
Map existing HTTP APIs to future service owners and define migration-safe API contracts for frontend/UI upgrade.

## Current API Inventory

| API Path | Method | Current Controller | Current Module |
|---|---|---|---|
| `/api/v1/gbm/index/query_group_buy_market_config` | `POST` | `MarketIndexController` | `group-buy-market-yizhou-trigger` |
| `/api/v1/gbm/trade/lock_market_pay_order` | `POST` | `MarketTradeController` | `group-buy-market-yizhou-trigger` |
| `/api/v1/gbm/trade/settlement_market_pay_order` | `POST` | `MarketTradeController` | `group-buy-market-yizhou-trigger` |
| `/api/v1/gbm/dcc/update_config` | `GET` | `DCCController` | `group-buy-market-yizhou-trigger` |
| `/api/v1/test/group_buy_notify` | `POST` | `TestApiClientController` | `group-buy-market-yizhou-trigger` |
| `/api/v1/test/direct_buy_notify` | `POST` | `TestApiClientController` | `group-buy-market-yizhou-trigger` |

## Proposed Owner Service (Future)

| API Path | Proposed Owner Service | Notes |
|---|---|---|
| `/api/v1/gbm/index/query_group_buy_market_config` | BFF/Gateway + Growth Strategy Service | BFF aggregates trial + team data for UI; strategy service owns trial logic |
| `/api/v1/gbm/trade/lock_market_pay_order` | Trade Fulfillment Service | Add Redis Lua atomic occupancy and idempotency control |
| `/api/v1/gbm/trade/settlement_market_pay_order` | Trade Fulfillment Service | Publish settlement event to callback service |
| `/api/v1/gbm/dcc/update_config` | Config and Governance Service | Add auth, audit log, namespace isolation, rollback |
| `/api/v1/test/group_buy_notify` | Test-only Mock Callback (non-prod) | Keep only in non-production profile |
| `/api/v1/test/direct_buy_notify` | Test-only Mock Callback (non-prod) | Keep only in non-production profile |

## Contract Stabilization Suggestions

1. Keep existing endpoint paths unchanged during Phase 2 to protect frontend compatibility.
2. Introduce a shared request/response envelope with stable `code/info/data` semantics.
3. Add idempotency fields:
   - lock: `outTradeNo`, `userId`, `activityId`
   - settlement: `outTradeNo`, `outTradeTime`
4. Add trace fields for observability:
   - `traceId`, `spanId`, `source`, `channel`.
5. Mark test callbacks under explicit profile switch (for example `dev,test`) and isolate from production routing.

## API Evolution for UI Revamp

- Keep old APIs as compatibility layer.
- Add BFF read APIs for new UI pages:
  - `GET /api/v1/bff/dashboard/overview`
  - `GET /api/v1/bff/activity/{activityId}/funnel`
  - `GET /api/v1/bff/team/{teamId}/progress`
  - `GET /api/v1/bff/order/{outTradeNo}`
- BFF aggregates data from strategy/trade/notify domains and returns view models.

## Acceptance Criteria

- Every public API has exactly one owner service.
- Test endpoints are isolated by environment profile.
- Existing C-end flow remains compatible while BFF APIs are added for new UI.
