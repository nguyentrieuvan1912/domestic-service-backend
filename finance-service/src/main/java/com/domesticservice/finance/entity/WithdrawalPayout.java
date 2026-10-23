package com.domesticservice.finance.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name="withdrawal_payouts") @Getter @Setter @NoArgsConstructor
public class WithdrawalPayout {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="withdrawal_id",nullable=false,unique=true) private Long withdrawalId;
    @Column(name="reference_id",length=100,nullable=false,unique=true) private String referenceId;
    @Column(name="idempotency_key",length=100,nullable=false,unique=true) private String idempotencyKey;
    @Column(name="provider_id",length=160,unique=true) private String providerId;
    @Column(nullable=false,length=20) private String status;
    @Column(name="attempted_at",nullable=false) private Instant attemptedAt;
    @Column(name="checked_at") private Instant checkedAt;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="provider_proof",columnDefinition="jsonb") private String providerProof;
    @Column(nullable=false,columnDefinition="text") private String message;
    @Column(name="initiated_by",nullable=false) private Long initiatedBy;
    @OneToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="withdrawal_id",insertable=false,updatable=false) private Withdrawal withdrawal;
}
