package com.domesticservice.finance.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name="sepay_webhook_events") @Getter @Setter @NoArgsConstructor
public class SepayWebhookEvent {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="event_key",nullable=false,unique=true,length=80) private String eventKey;
    @Column(name="body_hash",nullable=false,length=64) private String bodyHash;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false,columnDefinition="jsonb") private String payload;
    @Column(name="received_at",nullable=false) private Instant receivedAt;
}
