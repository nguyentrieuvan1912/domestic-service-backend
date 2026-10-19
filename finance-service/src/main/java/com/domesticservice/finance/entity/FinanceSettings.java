package com.domesticservice.finance.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

/**
 * Persistence model for finance_settings; schema changes belong to Flyway migrations.
 */
@Entity
@Table(name = "finance_settings")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class FinanceSettings {

    @Id
    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "number_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal numberValue;

    @Column(name = "updated_by", nullable = false)
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
