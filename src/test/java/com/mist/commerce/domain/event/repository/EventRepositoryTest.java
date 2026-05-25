package com.mist.commerce.domain.event.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mist.commerce.domain.event.entity.Event;
import com.mist.commerce.domain.event.entity.EventItem;
import com.mist.commerce.domain.event.entity.EventItemOptionStock;
import com.mist.commerce.domain.event.entity.EventStatus;
import com.mist.commerce.domain.event.entity.EventType;
import com.mist.commerce.global.config.JpaAuditingConfig;
import com.mist.commerce.support.MySqlContainerTestSupport;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class EventRepositoryTest extends MySqlContainerTestSupport {

    private static final Instant START_AT = Instant.parse("2026-06-01T10:00:00Z");
    private static final Instant END_AT = Instant.parse("2026-06-01T12:00:00Z");

    @Autowired
    private EventRepository dropEventRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("Event를 저장하면 DB 식별자가 생성되고 id로 다시 조회할 수 있다")
    void saveAndFlush_persistsEventAndReloadsById() {
        Event event = event(List.of(item(10L, "150000", 100, optionStock(5L, 40))));

        Event saved = dropEventRepository.saveAndFlush(event);
        entityManager.clear();

        Event found = dropEventRepository.findById(saved.getId()).orElseThrow();
        assertThat(saved.getId()).isNotNull();
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getBrandId()).isEqualTo(1L);
        assertThat(found.getTitle()).isEqualTo("한정 스니커즈 드롭");
        assertThat(found.getStatus()).isEqualTo(EventStatus.READY);
        assertThat(found.getEventType()).isEqualTo(EventType.LIMITED_DROP);
        assertThat(found.getStartAt()).isEqualTo(START_AT);
        assertThat(found.getEndAt()).isEqualTo(END_AT);
    }

    @Test
    @DisplayName("Event 저장 시 item이 cascade persist된다")
    void saveAndFlush_cascadesItems() {
        Event event = event(List.of(
                item(10L, "150000", 100, optionStock(5L, 40)),
                item(11L, "180000", 50, optionStock(6L, 50))
        ));

        Event saved = dropEventRepository.saveAndFlush(event);
        entityManager.clear();

        Event found = dropEventRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getItems()).hasSize(2);
        assertThat(found.getItems()).extracting(EventItem::getProductId)
                .containsExactlyInAnyOrder(10L, 11L);
    }

    @Test
    @DisplayName("Event 저장 시 item의 optionStock이 cascade persist된다")
    void saveAndFlush_cascadesOptionStocks() {
        Event event = event(List.of(item(10L, "150000", 100, optionStock(5L, 40), optionStock(6L, 60))));

        Event saved = dropEventRepository.saveAndFlush(event);
        entityManager.clear();

        Event found = dropEventRepository.findById(saved.getId()).orElseThrow();
        EventItem foundItem = found.getItems().getFirst();
        assertThat(foundItem.getOptionStocks()).hasSize(2);
        assertThat(foundItem.getOptionStocks()).extracting(EventItemOptionStock::getProductOptionValueId)
                .containsExactlyInAnyOrder(5L, 6L);
        assertThat(foundItem.getOptionStocks()).extracting(EventItemOptionStock::getStockQuantity)
                .containsExactlyInAnyOrder(40, 60);
    }

    @Test
    @DisplayName("Event 저장 시 event와 자식 엔티티의 audit 필드가 채워진다")
    void saveAndFlush_populatesAuditFieldsForEventAndChildren() {
        Event event = event(List.of(item(10L, "150000", 100, optionStock(5L, 40))));

        Event saved = dropEventRepository.saveAndFlush(event);

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        EventItem savedItem = saved.getItems().getFirst();
        assertThat(savedItem.getCreatedAt()).isNotNull();
        assertThat(savedItem.getUpdatedAt()).isNotNull();
        EventItemOptionStock savedStock = savedItem.getOptionStocks().getFirst();
        assertThat(savedStock.getCreatedAt()).isNotNull();
        assertThat(savedStock.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Event 저장 시 item과 optionStock에도 DB 식별자가 생성된다")
    void saveAndFlush_assignsGeneratedIdsToEventItemsAndOptionStocks() {
        Event event = event(List.of(item(10L, "150000", 100, optionStock(5L, 40))));

        Event saved = dropEventRepository.saveAndFlush(event);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getItems()).allSatisfy(item -> assertThat(item.getId()).isNotNull());
        assertThat(saved.getItems().getFirst().getOptionStocks())
                .allSatisfy(stock -> assertThat(stock.getId()).isNotNull());
    }

    @Test
    @DisplayName("EventItem을 재조회하면 quantity와 기본 reserved/sold 수량이 보존된다")
    void reload_preservesDefaultQuantitiesOnEventItem() {
        Event event = event(List.of(item(10L, "150000", 100, optionStock(5L, 40))));

        Event saved = dropEventRepository.saveAndFlush(event);
        entityManager.clear();

        EventItem foundItem = dropEventRepository.findById(saved.getId()).orElseThrow().getItems().getFirst();
        assertThat(foundItem.getQuantity()).isEqualTo(100);
        assertThat(foundItem.getReservedQuantity()).isZero();
        assertThat(foundItem.getSoldQuantity()).isZero();
    }

    @Test
    @DisplayName("optionStock의 stockQuantity는 0으로 저장할 수 있다")
    void saveAndFlush_acceptsZeroOptionStockQuantity() {
        Event event = event(List.of(item(10L, "150000", 100, optionStock(5L, 0))));

        Event saved = dropEventRepository.saveAndFlush(event);
        entityManager.clear();

        EventItemOptionStock stock = dropEventRepository.findById(saved.getId())
                .orElseThrow()
                .getItems()
                .getFirst()
                .getOptionStocks()
                .getFirst();
        assertThat(stock.getStockQuantity()).isZero();
    }

    @Test
    @DisplayName("title이 200자를 초과하면 저장에 실패한다")
    void saveAndFlush_rejectsTitleOverMaxLength() {
        Event event = Event.create(
                1L,
                "A".repeat(201),
                START_AT,
                END_AT,
                List.of(item(10L, "150000", 100, optionStock(5L, 40)))
        );

        assertThatThrownBy(() -> dropEventRepository.saveAndFlush(event))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Event event(List<EventItem> items) {
        return Event.create(1L, "한정 스니커즈 드롭", START_AT, END_AT, items);
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
