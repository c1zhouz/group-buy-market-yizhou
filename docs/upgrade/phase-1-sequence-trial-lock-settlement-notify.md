# Phase 1 - Sequence Diagram (`trial -> lock -> settlement -> notify`)

```mermaid
sequenceDiagram
    autonumber
    participant UI as C-End UI/BFF
    participant Trigger as trigger.MarketTradeController
    participant Trial as domain.IIndexGroupBuyMarketService
    participant Trade as infrastructure.TradeRepository
    participant Redis as Redis(Lua)
    participant DB as MySQL
    participant Job as trigger.GroupBuyNotifyJob
    participant Callback as Upstream Callback API

    UI->>Trigger: POST /trade/lock_market_pay_order
    Trigger->>Trial: indexMarketTrial(user, source, channel, goods)
    Trial-->>Trigger: TrialBalanceEntity

    alt teamId is not null
        Trigger->>Trade: lockMarketPayOrder(aggregate)
        Trade->>DB: queryGroupBuyTeamByTeamId(teamId)
        Trade->>Redis: eval(lock_market_pay_order.lua)
        Redis-->>Trade: 0 success / 1 idempotent / 2 no-seat
        alt no-seat
            Trade-->>Trigger: throw E0005
            Trigger-->>UI: fail(E0005)
        else success or idempotent
            Trade->>DB: update group_buy_order lock_count + 1
            Trade->>DB: insert group_buy_order_list
            alt insert duplicate or DB error
                Trade->>Redis: eval(release_market_pay_order.lua)
                Trade->>DB: update group_buy_order lock_count - 1
                Trade-->>Trigger: throw INDEX_EXCEPTION/UN_ERROR
            else success
                Trade-->>Trigger: MarketPayOrderEntity
                Trigger-->>UI: success(orderId, deductionPrice)
            end
        end
    else create new team
        Trigger->>Trade: lockMarketPayOrder(aggregate)
        Trade->>DB: insert group_buy_order (new team, lock_count=1)
        Trade->>DB: insert group_buy_order_list
        Trade-->>Trigger: MarketPayOrderEntity
        Trigger-->>UI: success
    end

    UI->>Trigger: POST /trade/settlement_market_pay_order
    Trigger->>Trade: settlementMarketPayOrder
    Trade->>DB: update group_buy_order_list status=COMPLETE
    Trade->>DB: update group_buy_order complete_count + 1
    alt team complete
        Trade->>DB: update group_buy_order status=COMPLETE
        Trade->>DB: insert notify_task(status=INIT)
    end
    Trigger-->>UI: settlement response

    Job->>DB: queryUnExecutedNotifyTaskList
    Job->>Callback: HTTP notify(teamId, outTradeNoList)
    alt callback success
        Job->>DB: update notify_task status=SUCCESS
    else callback fail
        Job->>DB: update notify_task status=RETRY/ERROR
    end
```

## Notes

- `lock_market_pay_order` HTTP contract remains unchanged in this phase.
- Lua is currently used for existing team occupancy only (`teamId != null`).
- DB remains the final source of truth; Redis is used as high-concurrency guard + idempotent fast path.
- If Lua succeeds but DB write fails, compensation path releases Redis occupancy immediately.

