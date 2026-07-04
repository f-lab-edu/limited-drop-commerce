package com.mist.commerce.common.idempotency;

import com.mist.commerce.common.idempotency.exception.IdempotencyKeyReusedException;
import com.mist.commerce.common.idempotency.exception.InProgressException;
import com.mist.commerce.common.json.JsonSerializer;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IdempotencyClaimResolver {

    private final JsonSerializer jsonSerializer;

    public <T> Optional<T> resolve(ClaimResult claimResult, Class<T> resultType) {
        if (claimResult.status() == ClaimStatus.COMPLETED) {
            T t = jsonSerializer.deserializeResult(claimResult.resultPayload(), resultType);
            return Optional.of(t);
        }
        if (claimResult.status() == ClaimStatus.MISMATCH) {
            throw new IdempotencyKeyReusedException();
        }
        if (claimResult.status() == ClaimStatus.IN_PROGRESS) {
            throw new InProgressException();
        }
        return Optional.empty();
    }
}
