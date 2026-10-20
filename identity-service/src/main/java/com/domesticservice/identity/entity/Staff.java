package com.domesticservice.identity.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

/**
 * Persistence model for staffs; schema changes belong to Flyway migrations.
 */
@Entity
@Table(name = "staffs")
@DynamicInsert
@DynamicUpdate
@PrimaryKeyJoinColumn(name = "id")
@Getter
@Setter
@NoArgsConstructor
public class Staff extends User {

    @Column(name = "date_of_birth", nullable = true)
    private LocalDate dateOfBirth;

    @Column(name = "gender", nullable = true, length = 10)
    private String gender;

    @Column(name = "identity_number", nullable = true, length = 30)
    private String identityNumber;

    @Column(name = "profile_description", nullable = true, columnDefinition = "text")
    private String profileDescription;

    @Column(name = "average_rating", nullable = false, precision = 3, scale = 2)
    private BigDecimal averageRating;

    @Column(name = "total_reviews", nullable = false)
    private Integer totalReviews;

    @Column(name = "experience_years", nullable = false, precision = 4, scale = 1)
    private BigDecimal experienceYears;

    @Column(name = "completion_rate", nullable = true, precision = 5, scale = 2)
    private BigDecimal completionRate;

    @Column(name = "satisfaction_rate", nullable = true, precision = 5, scale = 2)
    private BigDecimal satisfactionRate;

    @Column(name = "status", nullable = false, length = 20)
    private String staffStatus;

    @Column(name = "is_online", nullable = false)
    private Boolean isOnline;

    @Column(name = "approved_at", nullable = true)
    private Instant approvedAt;

    @Column(name = "approved_by", nullable = true)
    private Long approvedBy;

    @Column(name = "account_rank", nullable = false, length = 20)
    private String accountRank;

    @Column(name = "birth_year", nullable = true)
    private Short birthYear;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "approved_by", referencedColumnName = "id", insertable = false, updatable = false)
    private Admin approver;

    @OneToOne(mappedBy = "staff", fetch = FetchType.LAZY)
    @Setter(AccessLevel.NONE)
    private StaffCandidate staffCandidateByStaff;

    public void setApprover(Admin approver) {
        this.approver = approver;
        this.approvedBy = approver == null ? null : approver.getId();
    }
}
