package com.domesticservice.booking.entity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
@Entity @Table(name="booking_calls") @Getter @Setter @NoArgsConstructor
public class BookingCall {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="conversation_id") private Long conversationId;
    @Column(name="caller_id") private Long callerId;
    @Column(name="callee_id") private Long calleeId;
    @Column(name="room_name",length=120) private String roomName;
    @Column(name="request_key",length=160) private String requestKey;
    @Column(name="status",length=16) private String status;
    @Column(name="created_at") private Instant createdAt;
    @Column(name="accepted_at") private Instant acceptedAt;
    @Column(name="ended_at") private Instant endedAt;
    @Column(name="room_closed_at") private Instant roomClosedAt;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="conversation_id",insertable=false,updatable=false) private Conversation conversation;
}
