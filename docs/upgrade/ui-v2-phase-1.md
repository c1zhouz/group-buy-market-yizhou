# UI v2 Phase 1 - Dashboard Revamp

## Scope

First version focuses on operation visibility and troubleshooting efficiency:

1. Activity overview KPI cards
2. Conversion funnel visualization
3. Callback status summary and recent task table

## Delivered Files

- `docs/ui/html/index.html` (C-end mall main entry)
- `docs/ui/html/ops-dashboard.html` (ops dashboard auxiliary entry)
- `docs/ui/html/css/tokens.css`
- `docs/ui/html/css/dashboard.css`
- `docs/ui/html/js/api.js`
- `docs/ui/html/js/dashboard.js`
- `docs/ui/README.md`

## Information Architecture

- `Topbar`: product title, current user, refresh action
- `Overview Panel`: key metrics (`曝光/试算/锁单/成团`)
- `Funnel Panel`: stage count + conversion rate + progress bar
- `Alert Panel`: core risk and queue backlog hints
- `Callback Panel`: status summary + latest callback task list

## API Placeholder Mapping

Current `docs/ui/html/js/api.js` uses mock data and should be replaced by BFF APIs:

- `fetchOverview` -> `/api/v1/bff/dashboard/overview`
- `fetchFunnel` -> `/api/v1/bff/activity/{activityId}/funnel`
- `fetchAlerts` -> `/api/v1/bff/dashboard/alerts`
- `fetchCallbackStatus` -> `/api/v1/bff/callback/status`

## Acceptance Criteria

- Page renders all three business modules without JS errors
- Refresh button triggers full data re-render
- Callback statuses are color-coded and readable
- Layout works on desktop and mobile width (<=900px)

## Next Iteration Suggestions

1. Add date-range and channel filters (`source/channel/activityId`)
2. Replace mock API with real BFF and loading/empty/error states
3. Add charts (line/trend) using lightweight library (for example ECharts)
4. Add callback detail drawer for `teamId` tracing

