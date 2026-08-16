package com.mist.commerce.domain.payment.application.support;

import com.mist.commerce.domain.payment.dto.PaymentCommand;
import com.mist.commerce.global.util.HashUtils;
import java.util.StringJoiner;
import org.springframework.stereotype.Component;

@Component
public class PaymentFingerprintGenerator {
    
    public String generate(PaymentCommand command) {
        return HashUtils.sha256Hex(canonicalize(command));
    }
    
    private String canonicalize(PaymentCommand command) {
        return new StringJoiner(";")
                .add("orderId=" + command.orderId())
                .toString();
    }
}
