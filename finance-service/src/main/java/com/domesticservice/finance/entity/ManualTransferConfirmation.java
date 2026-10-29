package com.domesticservice.finance.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity @Table(name="manual_transfer_confirmations") @Getter @Setter @NoArgsConstructor
public class ManualTransferConfirmation {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="payout_id",nullable=false) private Long payoutId;
    @Column(name="admin_id",nullable=false) private Long adminId;
    @Column(name="bank_reference",nullable=false,length=160) private String bankReference;
    @Column(name="recipient_note",nullable=false,columnDefinition="text") private String recipientNote;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="payout_id",insertable=false,updatable=false) private WithdrawalPayout payout;
}
