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
 * Persistence model for admins; schema changes belong to Flyway migrations.
 */
@Entity
@Table(name = "admins")
@DynamicInsert
@DynamicUpdate
@PrimaryKeyJoinColumn(name = "id")
@Getter
@Setter
@NoArgsConstructor
public class Admin extends User {

    @Column(name = "created_at", nullable = false)
    private Instant adminCreatedAt;

    @OneToMany(mappedBy = "approver", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private Set<Staff> staffsByApprover = new LinkedHashSet<>();
}
