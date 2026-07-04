package com.mist.commerce.domain.reservation.support;

import com.mist.commerce.domain.reservation.dto.ReserveCommand;
import com.mist.commerce.global.util.HashUtils;
import java.util.StringJoiner;
import org.springframework.stereotype.Component;

@Component
public class ReserveFingerprintGenerator {
    
    public String generate(ReserveCommand command) {
        return HashUtils.sha256Hex(canonicalize(command));
    }
    
    private String canonicalize(ReserveCommand command) {
        return new StringJoiner(";")
                .add("eventId=" + command.eventId())
                .add("eventItemId=" + command.eventItemId())
                .add("eventItemOptionStockId=" + command.eventItemOptionStockId())
                .add("quantity=" + command.quantity())
                .toString();

    }
}
