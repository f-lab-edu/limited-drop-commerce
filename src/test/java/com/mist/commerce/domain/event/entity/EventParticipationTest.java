package com.mist.commerce.domain.event.entity;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mist.commerce.domain.event.exception.DropEventClosedException;
import com.mist.commerce.domain.event.exception.DropEventNotOpenException;
import com.mist.commerce.global.exception.BusinessException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class EventParticipationTest {

    private static final Instant START_AT = Instant.parse("2026-06-01T10:00:00Z");
    private static final Instant END_AT = Instant.parse("2026-06-01T12:00:00Z");

    @Test
    @DisplayName("TC-EPV-001: OPEN 상태 이벤트는 참여 가능 검증을 통과한다")
    void verifyParticipable_whenOpen_doesNotThrowException() {
        Event event = event();
        event.open();

        assertThatNoException().isThrownBy(event::verifyParticipable);
    }

    @Test
    @DisplayName("TC-EPV-002: READY 상태 이벤트는 DROP_EVENT_NOT_OPEN 예외를 던진다")
    void verifyParticipable_whenReady_throwsDropEventNotOpen() {
        Event event = event();

        assertThatThrownBy(event::verifyParticipable)
                .isInstanceOf(BusinessException.class)
                .isInstanceOf(DropEventNotOpenException.class)
                .extracting("code")
                .isEqualTo("DROP_EVENT_NOT_OPEN");
    }

    @Test
    @DisplayName("TC-EPV-003: CLOSED 상태 이벤트는 DROP_EVENT_CLOSED 예외를 던진다")
    void verifyParticipable_whenClosed_throwsDropEventClosed() {
        Event event = eventWithStatus(EventStatus.CLOSED);

        assertThatThrownBy(event::verifyParticipable)
                .isInstanceOf(BusinessException.class)
                .isInstanceOf(DropEventClosedException.class)
                .extracting("code")
                .isEqualTo("DROP_EVENT_CLOSED");
    }

    private Event event() {
        return Event.create(1L, "한정 스니커즈 드롭", START_AT, END_AT, List.of());
    }

    private Event eventWithStatus(EventStatus status) {
        Event event = event();
        ReflectionTestUtils.setField(event, "status", status);
        return event;
    }
}
