-- KEYS[1]: team lock hash key
-- KEYS[2]: team idempotent set key
-- ARGV[1]: outTradeNo
-- ARGV[2]: targetCount
-- ARGV[3]: ttlSeconds
-- ARGV[4]: initLockCount (from DB snapshot)
-- return: 0 success, 1 idempotent hit, 2 no seat

if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
    return 1
end

local lockCount = redis.call('HGET', KEYS[1], 'lockCount')
if not lockCount then
    redis.call('HSET', KEYS[1], 'lockCount', ARGV[4])
    redis.call('HSET', KEYS[1], 'targetCount', ARGV[2])
    lockCount = ARGV[4]
end

local targetCount = redis.call('HGET', KEYS[1], 'targetCount')
if not targetCount then
    targetCount = ARGV[2]
    redis.call('HSET', KEYS[1], 'targetCount', targetCount)
end

if tonumber(lockCount) >= tonumber(targetCount) then
    return 2
end

redis.call('HINCRBY', KEYS[1], 'lockCount', 1)
redis.call('SADD', KEYS[2], ARGV[1])
redis.call('EXPIRE', KEYS[1], tonumber(ARGV[3]))
redis.call('EXPIRE', KEYS[2], tonumber(ARGV[3]))

return 0

