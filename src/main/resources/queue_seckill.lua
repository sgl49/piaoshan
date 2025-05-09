-- 库存队列key
local queueKey = KEYS[1]
-- 用户订单记录key
local orderKey = KEYS[2]
-- 用户ID
local userId = ARGV[1]
-- 订单ID
local orderId = ARGV[2]
-- 优惠券ID
local voucherId = ARGV[3]

-- 1. 判断用户是否重复下单
if (redis.call('sismember', orderKey, userId) == 1) then
    -- 存在重复下单
    return 2
end

-- 2. 从库存队列中取出一个库存
local result = redis.call('lpop', queueKey)
if not result then
    -- 库存队列中没有库存，表示已经卖完
    return 1
end

-- 3. 添加用户订单记录
redis.call('sadd', orderKey, userId)

-- 4. 不再使用Redis Stream发送消息，而是由应用程序处理
-- 删除相关缓存，确保缓存一致性
local userVoucherCacheKey = 'cache:voucher:order:user:' .. userId .. ':voucher:' .. voucherId
redis.call('del', userVoucherCacheKey)

-- 删除优惠券相关缓存
local voucherCacheKey = 'cache:voucher:' .. voucherId
redis.call('del', voucherCacheKey)

-- 返回成功
return 0 