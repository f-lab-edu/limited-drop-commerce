package com.mist.commerce.domain.event.entity;

import com.mist.commerce.domain.event.exception.DropEventClosedException;
import com.mist.commerce.domain.event.exception.DropEventNotOpenException;
import com.mist.commerce.global.entity.BaseTimeEntity;
import io.jsonwebtoken.lang.Assert;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Event extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 20)
    private EventType eventType;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private EventStatus status;

    @OneToMany(cascade = CascadeType.PERSIST)
    @JoinColumn(name = "event_id")
    private List<EventItem> items;

    private Event(Long brandId, String title, Instant startAt, Instant endAt, List<EventItem> items) {
        validate(startAt, endAt);

        this.brandId = brandId;
        this.title = title;
        this.eventType = EventType.LIMITED_DROP;
        this.startAt = startAt;
        this.endAt = endAt;
        this.status = EventStatus.READY;
        this.items = items;
    }

    public static Event create(Long brandId, String title, Instant startAt, Instant endAt, List<EventItem> items) {
        return new Event(brandId, title, startAt, endAt, items);
    }

    private static void validate(Instant startAt, Instant endAt) {
        Assert.isTrue(endAt.isAfter(startAt), "Event end time must be after start time");
    }

    public void open() {
        if (this.status != EventStatus.READY) {
            throw new IllegalStateException("Drop event can only be opened from READY status");
        }
        this.status = EventStatus.OPEN;
    }

    public void verifyParticipable() {
        if (this.status == EventStatus.READY) {
            throw new DropEventNotOpenException();
        }
        if (this.status == EventStatus.CLOSED) {
            throw new DropEventClosedException();
        }
    }

    public String getEventStatusName() {
        return status.name();
    }

}
