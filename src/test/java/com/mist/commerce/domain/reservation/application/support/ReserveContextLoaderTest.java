package com.mist.commerce.domain.reservation.application.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mist.commerce.domain.event.entity.Event;
import com.mist.commerce.domain.event.entity.EventItem;
import com.mist.commerce.domain.event.entity.EventItemOptionStock;
import com.mist.commerce.domain.event.entity.EventStatus;
import com.mist.commerce.domain.event.repository.EventRepository;
import com.mist.commerce.domain.product.entity.ProductOptionGroup;
import com.mist.commerce.domain.product.entity.ProductOptionValue;
import com.mist.commerce.domain.product.repository.ProductOptionGroupRepository;
import com.mist.commerce.domain.product.repository.ProductOptionValueRepository;
import com.mist.commerce.domain.reservation.dto.ReserveCommand;
import com.mist.commerce.domain.reservation.dto.ReserveContext;
import com.mist.commerce.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReserveContextLoaderTest {

    private static final Long USER_ID = 10L;
    private static final Long EVENT_ID = 20L;
    private static final Long EVENT_ITEM_ID = 30L;
    private static final Long OPTION_STOCK_ID = 40L;
    private static final Long PRODUCT_OPTION_GROUP_ID = 50L;
    private static final Long PRODUCT_OPTION_VALUE_ID = 60L;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private ProductOptionGroupRepository productOptionGroupRepository;

    @Mock
    private ProductOptionValueRepository productOptionValueRepository;

    @InjectMocks
    private ReserveContextLoader contextLoader;

    @Test
    @DisplayName("정상: 이벤트·상품·옵션 재고와 옵션 스냅샷을 채운 컨텍스트를 반환한다")
    void load_whenAllResolvable_returnsPopulatedContext() {
        Event event = openEvent(item(optionStock()));
        ProductOptionGroup group = optionGroup("색상");
        ProductOptionValue value = optionValue("Black");
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(productOptionGroupRepository.findById(PRODUCT_OPTION_GROUP_ID)).thenReturn(Optional.of(group));
        when(productOptionValueRepository.findById(PRODUCT_OPTION_VALUE_ID)).thenReturn(Optional.of(value));

        ReserveContext context = contextLoader.load(command());

        assertThat(context.event()).isSameAs(event);
        assertThat(context.eventItem().getId()).isEqualTo(EVENT_ITEM_ID);
        assertThat(context.eventItemOptionStock().getId()).isEqualTo(OPTION_STOCK_ID);
        assertThat(context.optionSnapshot().groupName()).isEqualTo("색상");
        assertThat(context.optionSnapshot().optionName()).isEqualTo("Black");
    }

    @Test
    @DisplayName("이벤트가 없으면 DROP_EVENT_NOT_FOUND를 던진다")
    void load_whenEventDoesNotExist_throwsDropEventNotFound() {
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

        assertBusinessException("DROP_EVENT_NOT_FOUND", () -> contextLoader.load(command()));
    }

    @Test
    @DisplayName("이벤트 상품이 없으면 EVENT_ITEM_OPTION_NOT_FOUND를 던진다")
    void load_whenEventItemDoesNotExist_throwsEventItemOptionNotFound() {
        Event event = openEvent(item(999L, optionStock()));
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));

        assertBusinessException("EVENT_ITEM_OPTION_NOT_FOUND", () -> contextLoader.load(command()));
    }

    @Test
    @DisplayName("옵션 재고가 없으면 EVENT_ITEM_OPTION_NOT_FOUND를 던진다")
    void load_whenOptionStockDoesNotExist_throwsEventItemOptionNotFound() {
        Event event = openEvent(item(optionStock(999L)));
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));

        assertBusinessException("EVENT_ITEM_OPTION_NOT_FOUND", () -> contextLoader.load(command()));
    }

    @Test
    @DisplayName("옵션 그룹 이름을 해소할 수 없으면 EVENT_ITEM_OPTION_NOT_FOUND를 던진다")
    void load_whenProductOptionGroupDoesNotExist_throwsEventItemOptionNotFound() {
        Event event = openEvent(item(optionStock()));
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(productOptionGroupRepository.findById(PRODUCT_OPTION_GROUP_ID)).thenReturn(Optional.empty());

        assertBusinessException("EVENT_ITEM_OPTION_NOT_FOUND", () -> contextLoader.load(command()));
    }

    @Test
    @DisplayName("옵션 값 이름을 해소할 수 없으면 EVENT_ITEM_OPTION_NOT_FOUND를 던진다")
    void load_whenProductOptionValueDoesNotExist_throwsEventItemOptionNotFound() {
        Event event = openEvent(item(optionStock()));
        ProductOptionGroup group = optionGroup("색상");
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(productOptionGroupRepository.findById(PRODUCT_OPTION_GROUP_ID)).thenReturn(Optional.of(group));
        when(productOptionValueRepository.findById(PRODUCT_OPTION_VALUE_ID)).thenReturn(Optional.empty());

        assertBusinessException("EVENT_ITEM_OPTION_NOT_FOUND", () -> contextLoader.load(command()));
    }

    private void assertBusinessException(String expectedCode, ThrowingCallable callable) {
        assertThatThrownBy(callable::call)
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(expectedCode);
    }

    private ReserveCommand command() {
        return new ReserveCommand(USER_ID, EVENT_ID, EVENT_ITEM_ID, OPTION_STOCK_ID, 1);
    }

    private Event openEvent(EventItem eventItem) {
        Event event = Event.create(
                1L,
                "한정 스니커즈 드롭",
                Instant.parse("2026-06-17T00:00:00Z"),
                Instant.parse("2026-06-17T06:00:00Z"),
                List.of(eventItem));
        ReflectionTestUtils.setField(event, "id", EVENT_ID);
        ReflectionTestUtils.setField(event, "status", EventStatus.OPEN);
        return event;
    }

    private EventItem item(EventItemOptionStock optionStock) {
        return item(EVENT_ITEM_ID, optionStock);
    }

    private EventItem item(Long eventItemId, EventItemOptionStock optionStock) {
        EventItem eventItem = EventItem.create(
                100L,
                new BigDecimal("150000"),
                10,
                3,
                List.of(optionStock));
        ReflectionTestUtils.setField(eventItem, "id", eventItemId);
        return eventItem;
    }

    private EventItemOptionStock optionStock() {
        return optionStock(OPTION_STOCK_ID);
    }

    private EventItemOptionStock optionStock(Long optionStockId) {
        EventItemOptionStock optionStock = EventItemOptionStock.create(
                PRODUCT_OPTION_GROUP_ID,
                PRODUCT_OPTION_VALUE_ID,
                10);
        ReflectionTestUtils.setField(optionStock, "id", optionStockId);
        return optionStock;
    }

    private ProductOptionGroup optionGroup(String name) {
        ProductOptionGroup group = mock(ProductOptionGroup.class);
        when(group.getName()).thenReturn(name);
        return group;
    }

    private ProductOptionValue optionValue(String value) {
        ProductOptionValue optionValue = mock(ProductOptionValue.class);
        when(optionValue.getValue()).thenReturn(value);
        return optionValue;
    }

    @FunctionalInterface
    private interface ThrowingCallable {
        void call();
    }
}
