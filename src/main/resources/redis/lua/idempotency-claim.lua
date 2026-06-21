local cur = redis.call('GET', KEYS[1])
local fingerprint = ARGV[1]
local ttlMillis = tonumber(ARGV[2])

if not cur then
    redis.call('SET', KEYS[1], 'PENDING|' .. string.len(fingerprint) .. '|' .. fingerprint, 'PX', ttlMillis)
    return 'CLAIMED'
end

local first = string.find(cur, '|', 1, true)
local second = nil
if first then
    second = string.find(cur, '|', first + 1, true)
end
if not first or not second then
    return 'MISMATCH'
end

local storedStatus = string.sub(cur, 1, first - 1)
local fingerprintLength = tonumber(string.sub(cur, first + 1, second - 1))
if not fingerprintLength then
    return 'MISMATCH'
end

local fingerprintStart = second + 1
local fingerprintEnd = fingerprintStart + fingerprintLength - 1
local storedFingerprint = string.sub(cur, fingerprintStart, fingerprintEnd)
if storedFingerprint ~= fingerprint then
    return 'MISMATCH'
end

if storedStatus == 'PENDING' then
    return 'IN_PROGRESS'
end
if storedStatus == 'DONE' then
    return 'COMPLETED|' .. string.sub(cur, fingerprintEnd + 1)
end
return 'MISMATCH'
