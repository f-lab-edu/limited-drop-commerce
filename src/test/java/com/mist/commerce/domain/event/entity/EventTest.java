package com.mist.commerce.domain.event.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class EventTest {

    private static final Instant START_AT = Instant.parse("2026-06-01T10:00:00Z");
    private static final Instant END_AT = Instant.parse("2026-06-01T12:00:00Z");

    @Test
    @DisplayName("유효한 입력으로 Event를 생성하면 입력값이 보존되고 READY 상태가 기본값이다")
    void create_withValidInputs_preservesInputsAndDefaultsReady() {
        EventItem item = item(10L, "150000", 100, optionStock(5L, 40));

        Event event = Event.create(1L, "한정 스니커즈 드롭", START_AT, END_AT, List.of(item));

        assertThat(event.getId()).isNull();
        assertThat(event.getBrandId()).isEqualTo(1L);
        assertThat(event.getTitle()).isEqualTo("한정 스니커즈 드롭");
        assertThat(event.getStartAt()).isEqualTo(START_AT);
        assertThat(event.getEndAt()).isEqualTo(END_AT);
        assertThat(event.getItems()).containsExactly(item);
        assertThat(event.getStatus()).isEqualTo(EventStatus.READY);
        assertThat(event.getEventType()).isEqualTo(EventType.LIMITED_DROP);
    }

    @Test
    @DisplayName("여러 item과 optionStock으로 Event를 생성하면 중첩 컬렉션이 보존된다")
    void create_withMultipleItemsAndOptionStocks_preservesNestedCollections() {
        EventItem first = item(10L, "150000", 100, optionStock(5L, 40), optionStock(6L, 60));
        EventItem second = item(11L, "180000", 50, optionStock(7L, 20), optionStock(8L, 30));

        Event event = Event.create(1L, "한정 스니커즈 드롭", START_AT, END_AT, List.of(first, second));

        assertThat(event.getItems()).containsExactly(first, second);
        assertThat(event.getItems().get(0).getOptionStocks()).extracting(EventItemOptionStock::getProductOptionValueId)
                .containsExactly(5L, 6L);
        assertThat(event.getItems().get(1).getOptionStocks()).extracting(EventItemOptionStock::getProductOptionValueId)
                .containsExactly(7L, 8L);
    }

    @Test
    @DisplayName("startAt이 null이면 영속화 전에 NullPointerException이 발생한다")
    void create_withNullStartAt_throwsNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> Event.create(1L, "한정 스니커즈 드롭", null, END_AT, List.of(item())));
    }

    @Test
    @DisplayName("endAt이 null이면 영속화 전에 NullPointerException이 발생한다")
    void create_withNullEndAt_throwsNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> Event.create(1L, "한정 스니커즈 드롭", START_AT, null, List.of(item())));
    }

    @Test
    @DisplayName("READY 상태에서 open을 호출하면 OPEN 상태로 변경된다")
    void open_whenStatusReady_changesStatusToOpen() {
        Event event = event();

        event.open();

        assertThat(event.getStatus()).isEqualTo(EventStatus.OPEN);
    }

    @Test
    @DisplayName("이미 OPEN 상태인 이벤트에서 open을 호출하면 IllegalStateException 계열 예외가 발생한다")
    void open_whenAlreadyOpen_throwsIllegalStateException() {
        Event event = eventWithStatus(EventStatus.OPEN);

        assertThatThrownBy(event::open)
                .isInstanceOf(IllegalStateException.class);
        assertThat(event.getStatus()).isEqualTo(EventStatus.OPEN);
    }

    @Test
    @DisplayName("CLOSED 상태인 이벤트에서 open을 호출하면 IllegalStateException 계열 예외가 발생한다")
    void open_whenClosed_throwsIllegalStateException() {
        Event event = eventWithStatus(EventStatus.CLOSED);

        assertThatThrownBy(event::open)
                .isInstanceOf(IllegalStateException.class);
        assertThat(event.getStatus()).isEqualTo(EventStatus.CLOSED);
    }

    private Event event() {
        return Event.create(1L, "한정 스니커즈 드롭", START_AT, END_AT, List.of(item()));
    }

    private Event eventWithStatus(EventStatus status) {
        Event event = event();
        ReflectionTestUtils.setField(event, "status", status);
        return event;
    }

    private EventItem item() {
        return item(10L, "150000", 100, optionStock(5L, 40));
    }

    private EventItem item(
            Long productId,
            String price,
            int quantity,
            EventItemOptionStock... optionStocks
    ) {
        return EventItem.create(productId, new BigDecimal(price), quantity, List.of(optionStocks));
    }

    private EventItemOptionStock optionStock(Long optionValueId, int stockQuantity) {
        return EventItemOptionStock.create(optionValueId, stockQuantity);
    }
}
