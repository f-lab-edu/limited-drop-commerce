package com.mist.commerce.domain.event.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mist.commerce.global.exception.BusinessException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EventItemPurchaseLimitTest {

    @Test
    @DisplayName("TC-EIPL-001: 5-인자 create가 1인 구매 한도를 보존한다")
    void create_withMaxPurchasePerCustomer_preservesLimit() {
        EventItem item = item(5);

        assertThat(item.getMaxPurchasePerCustomer()).isEqualTo(5);
    }

    @Test
    @DisplayName("TC-EIPL-002: 기존 구매 수량과 요청 수량의 합이 한도 이내이면 통과한다")
    void verifyPurchasableQuantity_whenTotalWithinLimit_doesNotThrowException() {
        EventItem item = item(5);

        assertThatNoException().isThrownBy(() -> item.verifyPurchasableQuantity(3, 2));
    }

    @Test
    @DisplayName("TC-EIPL-003: 요청 수량이 한도 경계와 정확히 같으면 통과한다")
    void verifyPurchasableQuantity_whenTotalEqualsLimit_doesNotThrowException() {
        EventItem item = item(5);

        assertThatNoException().isThrownBy(() -> item.verifyPurchasableQuantity(5, 0));
    }

    @Test
    @DisplayName("TC-EIPL-004: 기존 구매 수량과 요청 수량의 합이 한도를 초과하면 PURCHASE_LIMIT_EXCEEDED 예외를 던진다")
    void verifyPurchasableQuantity_whenTotalExceedsLimit_throwsPurchaseLimitExceeded() {
        EventItem item = item(5);

        assertThatThrownBy(() -> item.verifyPurchasableQuantity(3, 3))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("PURCHASE_LIMIT_EXCEEDED");
    }

    @Test
    @DisplayName("TC-EIPL-005: 요청 수량이 0이면 INVALID_RESERVATION_QUANTITY 예외를 던진다")
    void verifyPurchasableQuantity_whenRequestedQuantityIsZero_throwsInvalidReservationQuantity() {
        EventItem item = item(5);

        assertThatThrownBy(() -> item.verifyPurchasableQuantity(0, 0))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("INVALID_RESERVATION_QUANTITY");
    }

    @Test
    @DisplayName("TC-EIPL-006: 요청 수량이 음수이면 INVALID_RESERVATION_QUANTITY 예외를 던진다")
    void verifyPurchasableQuantity_whenRequestedQuantityIsNegative_throwsInvalidReservationQuantity() {
        EventItem item = item(5);

        assertThatThrownBy(() -> item.verifyPurchasableQuantity(-1, 0))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("INVALID_RESERVATION_QUANTITY");
    }

    @Test
    @DisplayName("TC-EIPL-007: 요청 수량 검증은 구매 한도 검증보다 먼저 수행된다")
    void verifyPurchasableQuantity_whenQuantityInvalidAndAlreadyPurchasedExceedsLimit_throwsInvalidReservationQuantity() {
        EventItem item = item(5);

        assertThatThrownBy(() -> item.verifyPurchasableQuantity(0, 10))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("INVALID_RESERVATION_QUANTITY");
    }

    private EventItem item(int maxPurchasePerCustomer) {
        return EventItem.create(
                10L,
                new BigDecimal("150000"),
                100,
                maxPurchasePerCustomer,
                List.of()
        );
    }
}
