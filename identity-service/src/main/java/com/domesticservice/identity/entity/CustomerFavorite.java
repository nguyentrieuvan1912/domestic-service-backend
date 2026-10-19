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
 * Persistence model for customer_favorites; schema changes belong to Flyway migrations.
 */
@Entity
@Table(name = "customer_favorites")
@DynamicInsert
@DynamicUpdate
@IdClass(CustomerFavoriteId.class)
@Getter
@Setter
@NoArgsConstructor
public class CustomerFavorite {

    @Id
    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Id
    @Column(name = "service_id", nullable = false)
    private Long serviceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Customer customer;

    public void setCustomer(Customer customer) {
        this.customer = customer;
        this.customerId = customer == null ? null : customer.getId();
    }
}
