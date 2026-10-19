package com.domesticservice.identity.entity;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

/**
 * Persistence model for customers; schema changes belong to Flyway migrations.
 */
@Entity
@Table(name = "customers")
@DynamicInsert
@DynamicUpdate
@PrimaryKeyJoinColumn(name = "id")
@Getter
@Setter
@NoArgsConstructor
public class Customer extends User {

    @Column(name = "date_of_birth", nullable = true)
    private LocalDate dateOfBirth;

    @Column(name = "gender", nullable = true, length = 10)
    private String gender;

    @Column(name = "total_bookings", nullable = false)
    private Integer totalBookings;

    @Column(name = "reward_points", nullable = false)
    private Long rewardPoints;

    @OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private Set<Address> addresssByCustomer = new LinkedHashSet<>();

    @OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private Set<CustomerFavorite> customerFavoritesByCustomer = new LinkedHashSet<>();
}
