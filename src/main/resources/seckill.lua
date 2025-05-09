

---秒杀券id
local voucherId = ARGV[1]
--用户id
local userId = ARGV[2]
--订单id
local id = ARGV[3]

--库存key
local stockKey = 'seckill:stock:' .. voucherId
--订单key
local orderKey = 'seckill:order:' .. voucherId
--缓存key
local cacheKeyPrefix = 'cache:voucher:order:user:'

--库存是否充足
--库存不足
if (tonumber(redis.call('get', stockKey)) <= 0) then
    return 1
end

--判断用户是否下单
--存在用户 禁止重复下单
if (tonumber(redis.call('sismember', orderKey, userId)) == 1) then
    return 2
end

--扣减库存
redis.call('incrby',stockKey,-1)
--下单（保存用户）
redis.call('sadd',orderKey,userId)

--删除相关缓存，确保缓存一致性
--1. 删除用户-优惠券缓存
local userVoucherCacheKey = cacheKeyPrefix .. userId .. ':voucher:' .. voucherId
redis.call('del', userVoucherCacheKey)

--2. 删除优惠券相关缓存
local voucherCacheKey = 'cache:voucher:' .. voucherId
redis.call('del', voucherCacheKey)

--不再使用Redis Stream发送消息，改为通过RabbitMQ发送
return 0