package com.mist.commerce.domain.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.mist.commerce.domain.event.dto.EventCreateRequest;
import com.mist.commerce.domain.event.dto.EventCreateResponse;
import com.mist.commerce.domain.event.entity.Event;
import com.mist.commerce.domain.event.entity.EventItem;
import com.mist.commerce.domain.event.entity.EventItemOptionStock;
import com.mist.commerce.domain.event.entity.EventStatus;
import com.mist.commerce.domain.event.entity.EventType;
import com.mist.commerce.domain.event.exception.EventRegistrationForbiddenException;
import com.mist.commerce.domain.event.exception.EventScheduleValidationException;
import com.mist.commerce.domain.event.policy.EventRegistrationPolicy;
import com.mist.commerce.domain.event.repository.EventRepository;
import com.mist.commerce.domain.product.exception.ProductNotFoundException;
import com.mist.commerce.domain.user.entity.User;
import com.mist.commerce.domain.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventServiceTest {

    private static final Instant START_AT = Instant.parse("2026-06-01T10:00:00Z");
    private static final Instant END_AT = Instant.parse("2026-06-01T12:00:00Z");
    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    @Mock
    private UserRepository userRepository;

    @Mock
    private EventRepository dropEventRepository;

    @Mock
    private EventRegistrationPolicy eventRegistrationPolicy;

    @Mock
    private User user;

    private EventService service;

    @BeforeEach
    void setUp() {
        service = new EventService(userRepository, dropEventRepository, eventRegistrationPolicy);
    }

    @Test
    @DisplayName("정책이 통과하면 READY LIMITED_DROP 이벤트를 저장하고 응답을 반환한다")
    void create_whenPolicyAllows_persistsReadyLimitedDropAndReturnsResponse() {
        EventCreateRequest request = request(List.of(item(10L)));
        Instant savedCreatedAt = Instant.parse("2026-06-01T00:30:00Z");
        Event savedEvent = savedEvent(
                42L,
                savedCreatedAt,
                savedItem(101L, 10L)
        );
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(dropEventRepository.save(any(Event.class))).willReturn(savedEvent);

        EventCreateResponse response = service.create(1L, request);

        verify(eventRegistrationPolicy).validate(user, request);
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(dropEventRepository).save(eventCaptor.capture());
        Event capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.getId()).isNull();
        assertThat(capturedEvent.getStatus()).isEqualTo(EventStatus.READY);
        assertThat(capturedEvent.getEventType()).isEqualTo(EventType.LIMITED_DROP);
        assertThat(capturedEvent.getBrandId()).isEqualTo(1L);
        assertThat(capturedEvent.getTitle()).isEqualTo("한정 스니커즈 드롭");

        assertThat(response.eventId()).isEqualTo(42L);
        assertThat(response.status()).isEqualTo("READY");
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().eventItemId()).isEqualTo(101L);
        assertThat(response.items().getFirst().productId()).isEqualTo(10L);
        assertThat(response.createdAt()).isEqualTo(savedCreatedAt.atZone(SEOUL_ZONE).toOffsetDateTime());
    }

    @Test
    @DisplayName("저장된 이벤트에 item이 여러 개이면 응답 item도 모두 매핑한다")
    void create_whenRequestHasMultipleItems_mapsAllResponseItems() {
        EventCreateRequest request = request(List.of(item(10L), item(11L)));
        Event savedEvent = savedEvent(
                42L,
                Instant.parse("2026-06-01T00:30:00Z"),
                savedItem(101L, 10L),
                savedItem(102L, 11L)
        );
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(dropEventRepository.save(any(Event.class))).willReturn(savedEvent);

        EventCreateResponse response = service.create(1L, request);

        assertThat(response.items()).hasSize(2);
        assertThat(response.items()).extracting(EventCreateResponse.ResponseItem::eventItemId)
                .containsExactly(101L, 102L);
        assertThat(response.items()).extracting(EventCreateResponse.ResponseItem::productId)
                .containsExactly(10L, 11L);
    }

    @Test
    @DisplayName("인증 사용자와 요청 값을 정책에 전달한다")
    void create_passesAuthenticatedUserAndRequestValuesToPolicy() {
        EventCreateRequest request = request(List.of(item(10L), item(11L)));
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(dropEventRepository.save(any(Event.class))).willReturn(
                savedEvent(42L, Instant.now(), savedItem(101L, 10L)));

        service.create(1L, request);

        ArgumentCaptor<EventCreateRequest> requestCaptor = ArgumentCaptor.forClass(EventCreateRequest.class);
        verify(eventRegistrationPolicy).validate(eq(user), requestCaptor.capture());
        EventCreateRequest captured = requestCaptor.getValue();
        assertThat(captured.brandId()).isEqualTo(1L);
        assertThat(captured.startAt()).isEqualTo(START_AT);
        assertThat(captured.endAt()).isEqualTo(END_AT);
        assertThat(captured.items()).extracting(EventCreateRequest.Item::productId)
                .containsExactly(10L, 11L);
    }

    @Test
    @DisplayName("사용자를 찾을 수 없으면 EventRegistrationForbiddenException이 발생하고 저장하지 않는다")
    void create_whenUserMissing_throwsEventRegistrationForbiddenAndDoesNotSave() {
        given(userRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(1L, request(List.of(item(10L)))))
                .isInstanceOf(EventRegistrationForbiddenException.class);
        verify(dropEventRepository, never()).save(any(Event.class));
    }

    @Test
    @DisplayName("정책이 권한 예외를 던지면 그대로 전파하고 저장하지 않는다")
    void create_whenPolicyThrowsForbidden_propagatesAndDoesNotSave() {
        EventCreateRequest request = request(List.of(item(10L)));
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        willThrow(new EventRegistrationForbiddenException(1L))
                .given(eventRegistrationPolicy).validate(user, request);

        assertThatThrownBy(() -> service.create(1L, request))
                .isInstanceOf(EventRegistrationForbiddenException.class);
        verify(dropEventRepository, never()).save(any(Event.class));
    }

    @Test
    @DisplayName("정책이 상품 없음 예외를 던지면 그대로 전파하고 저장하지 않는다")
    void create_whenPolicyThrowsProductNotFound_propagatesAndDoesNotSave() {
        EventCreateRequest request = request(List.of(item(10L)));
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        willThrow(new ProductNotFoundException(10L))
                .given(eventRegistrationPolicy).validate(user, request);

        assertThatThrownBy(() -> service.create(1L, request))
                .isInstanceOf(ProductNotFoundException.class);
        verify(dropEventRepository, never()).save(any(Event.class));
    }

    @Test
    @DisplayName("정책이 일정 검증 예외를 던지면 그대로 전파하고 저장하지 않는다")
    void create_whenPolicyThrowsScheduleValidation_propagatesAndDoesNotSave() {
        EventCreateRequest request = request(List.of(item(10L)));
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        willThrow(new EventScheduleValidationException())
                .given(eventRegistrationPolicy).validate(user, request);

        assertThatThrownBy(() -> service.create(1L, request))
                .isInstanceOf(EventScheduleValidationException.class);
        verify(dropEventRepository, never()).save(any(Event.class));
    }

    @Test
    @DisplayName("요청에 eventType이 없더라도 저장 대상은 LIMITED_DROP으로 고정된다")
    void create_ignoresClientEventTypeBecauseServerFixesLimitedDrop() {
        EventCreateRequest request = request(List.of(item(10L)));
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(dropEventRepository.save(any(Event.class))).willReturn(
                savedEvent(42L, Instant.now(), savedItem(101L, 10L)));

        service.create(1L, request);

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(dropEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo(EventType.LIMITED_DROP);
    }

    private EventCreateRequest request(List<EventCreateRequest.Item> items) {
        return new EventCreateRequest(1L, "한정 스니커즈 드롭", START_AT, END_AT, items);
    }

    private EventCreateRequest.Item item(Long productId) {
        return new EventCreateRequest.Item(productId, new BigDecimal("150000"), 100, List.of(optionStock()));
    }

    private EventCreateRequest.OptionStock optionStock() {
        return new EventCreateRequest.OptionStock(5L, 40);
    }

    private Event savedEvent(Long eventId, Instant createdAt, EventItem... items) {
        Event event = Event.create(1L, "한정 스니커즈 드롭", START_AT, END_AT, List.of(items));
        ReflectionTestUtils.setField(event, "id", eventId);
        ReflectionTestUtils.setField(event, "createdAt", createdAt);
        return event;
    }

    private EventItem savedItem(Long eventItemId, Long productId) {
        EventItem item = EventItem.create(productId, new BigDecimal("150000"), 100, List.of(
                EventItemOptionStock.create(5L, 40)
        ));
        ReflectionTestUtils.setField(item, "id", eventItemId);
        return item;
    }
}
