package com.domesticservice.booking.entity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
@Entity @Table(name="booking_reminder_deliveries") @Getter @Setter @NoArgsConstructor
public class BookingReminderDelivery {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="assignment_id") private Long assignmentId;
    @Column(name="starts_at") private Instant startsAt;
    @Column(name="minutes_before") private Integer minutesBefore;
    @Column(name="sent_at") private Instant sentAt;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="assignment_id",insertable=false,updatable=false) private BookingAssignment assignment;
}
