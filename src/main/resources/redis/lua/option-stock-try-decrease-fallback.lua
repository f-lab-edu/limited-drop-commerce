local cur = redis.call('GET', KEYS[1])
if not cur then redis.call('SET', KEYS[1], ARGV[2]); cur = ARGV[2] end
cur = tonumber(cur)
local q = tonumber(ARGV[1])
if cur >= q then return redis.call('DECRBY', KEYS[1], q) else return -1 end
