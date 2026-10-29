package com.domesticservice.finance.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Table(name="bank_account_reviews") @Getter @Setter @NoArgsConstructor
public class BankAccountReview {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="bank_account_id",nullable=false) private Long bankAccountId;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="bank_account_id",insertable=false,updatable=false) private StaffBankAccount bankAccount;
 @Column(name="staff_id",nullable=false) private Long staffId;
 @Column(name="admin_id",nullable=false) private Long adminId;
 @Column(name="admin_name",length=160) private String adminName;
 @Column(name="old_status",length=20) private String oldStatus;
 @Column(name="new_status",nullable=false,length=20) private String newStatus;
 @Column(name="bank_code",nullable=false,length=30) private String bankCode;
 @Column(name="account_number",nullable=false,length=50) private String accountNumber;
 @Column(name="account_holder",nullable=false,length=160) private String accountHolder;
 @Column(name="verification_note",nullable=false,columnDefinition="text") private String verificationNote;
 @Column(nullable=false) private boolean legacy;
 @Column(name="reviewed_at",nullable=false) private Instant reviewedAt;
}
