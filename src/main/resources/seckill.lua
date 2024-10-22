local voucherId = ARGV[1]
local userId = ARGV[2]

-- 库存key
local stockKey = 'seckill:stock:' .. voucherId
-- 订单key
local orderKey = 'seckill:order:' .. voucherId
-- 库存不足 返回1
if (tonumber(redis.call('get', stockKey)) <= 0) then
    return 1
end
-- 重复下单 返回2
if (redis.call('sismember', orderKey, userId) == 1) then
    return 2
end
-- 扣库存 下单（保存用户） 返回0
redis.call('incrby', stockKey, -1)
redis.call('sadd', orderKey, userId)
return 0