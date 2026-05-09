-- KEYS[1]: team lock hash key
-- KEYS[2]: team idempotent set key
-- ARGV[1]: outTradeNo
-- return: 1 released, 0 no-op

if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 0 then
    return 0
end

redis.call('SREM', KEYS[2], ARGV[1])

local lockCount = redis.call('HGET', KEYS[1], 'lockCount')
if lockCount and tonumber(lockCount) > 0 then
    redis.call('HINCRBY', KEYS[1], 'lockCount', -1)
end

return 1

