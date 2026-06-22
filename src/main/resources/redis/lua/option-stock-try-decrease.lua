local cur = redis.call('GET', KEYS[1])
if not cur then return -1 end
cur = tonumber(cur)
local q = tonumber(ARGV[1])
if cur >= q then return redis.call('DECRBY', KEYS[1], q) else return -1 end
