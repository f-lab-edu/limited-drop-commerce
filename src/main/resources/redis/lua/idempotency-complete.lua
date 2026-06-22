local cur = redis.call('GET', KEYS[1])
if not cur then
    return 0
end

local first = string.find(cur, '|', 1, true)
local second = nil
if first then
    second = string.find(cur, '|', first + 1, true)
end
if not first or not second then
    return 0
end

local storedStatus = string.sub(cur, 1, first - 1)
if storedStatus ~= 'PENDING' then
    return 0
end

local fingerprintLength = tonumber(string.sub(cur, first + 1, second - 1))
if not fingerprintLength then
    return 0
end

local fingerprintStart = second + 1
local fingerprintEnd = fingerprintStart + fingerprintLength - 1
local storedFingerprint = string.sub(cur, fingerprintStart, fingerprintEnd)
if storedFingerprint ~= ARGV[1] then
    return 0
end

redis.call(
    'SET',
    KEYS[1],
    'DONE|' .. fingerprintLength .. '|' .. storedFingerprint .. ARGV[2],
    'KEEPTTL'
)
return 1
