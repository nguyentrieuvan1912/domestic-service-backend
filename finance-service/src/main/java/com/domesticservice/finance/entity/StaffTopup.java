package com.domesticservice.finance.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name="staff_topups")
@Getter @Setter @NoArgsConstructor
public class StaffTopup {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="staff_id",nullable=false) private Long staffId;
    @Column(nullable=false) private Long amount;
    @Column(name="request_key",nullable=false,length=160) private String requestKey;
    @Column(nullable=false,length=20) private String status;
    @Column(name="provider_link_id",length=160) private String providerLinkId;
    @Column(name="checkout_url",columnDefinition="text") private String checkoutUrl;
    @Column(name="qr_content",columnDefinition="text") private String qrContent;
    @Column(name="account_number",length=50) private String accountNumber;
    @Column(name="account_name",length=160) private String accountName;
    @Column(name="bank_bin",length=20) private String bankBin;
    @Column(name="receiving_account_verified",nullable=false) private boolean receivingAccountVerified;
    @Column(nullable=false,length=25) private String description;
    @Column(name="amount_received",nullable=false) private Long amountReceived;
    @Column(name="expires_at",nullable=false) private Instant expiresAt;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="verified_at") private Instant verifiedAt;
    @Column(name="paid_at") private Instant paidAt;
}
