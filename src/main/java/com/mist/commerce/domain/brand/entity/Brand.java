package com.mist.commerce.domain.brand.entity;

import com.mist.commerce.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "brand",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_brand_company_id_name",
                columnNames = {"company_id", "name"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Brand extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    private Brand(Long companyId, String name, String description) {
        this.companyId = Objects.requireNonNull(companyId);
        this.name = Objects.requireNonNull(name);
        this.description = description;
    }

    public static Brand create(Long companyId, String name, String description) {
        return new Brand(companyId, name, description);
    }
}
