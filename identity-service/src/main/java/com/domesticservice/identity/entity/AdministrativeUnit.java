package com.domesticservice.identity.entity;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

/**
 * Persistence model for administrative_units; schema changes belong to Flyway migrations.
 */
@Entity
@Table(name = "administrative_units")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class AdministrativeUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "province", nullable = false, length = 100)
    private String province;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "unit_type", nullable = false, length = 20)
    private String unitType;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "source_url", nullable = false, columnDefinition = "text")
    private String sourceUrl;
}
