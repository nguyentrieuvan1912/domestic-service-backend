package com.domesticservice.booking.repository;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class AdminWorkRepository {
 private final JdbcTemplate db;
 public AdminWorkRepository(JdbcTemplate db){this.db=db;}
 public List<Map<String,Object>> snapshot(){return db.queryForList("""
   SELECT 'incident:'||i.id AS key,i.status AS state,'bookings' AS section,'/admin/bookings#booking-incidents' AS target,
    'Sự cố đơn '||b.booking_code AS title,left(i.reason,180) AS summary FROM booking_incidents i JOIN bookings b ON b.id=i.booking_id WHERE i.status='OPEN'
   UNION ALL
   SELECT 'capability:'||id,status,'staff','/admin/staff?staffId='||staff_id,'Năng lực nhân viên chờ duyệt #'||staff_id,'Dịch vụ #'||service_id FROM staff_service_capabilities WHERE status='PENDING'
   UNION ALL
   SELECT 'review:'||id,moderation_status,'reviews','/admin/reviews#service-reviews','Đánh giá cần xử lý #'||id,rating||' sao · '||left(coalesce(comment,''),140) FROM reviews WHERE rating<=3 AND moderation_status='PUBLISHED'
   UNION ALL
   SELECT 'booking:'||id,status,'bookings','/admin/bookings','Đơn cần điều phối '||booking_code,
    CASE status WHEN 'NO_STAFF_FOUND' THEN 'Chưa tìm được nhân viên' WHEN 'ABSENT' THEN 'Cần xử lý nhân viên vắng mặt' ELSE 'Đang chờ nhận / ghép nhân viên' END
   FROM bookings WHERE status IN ('PENDING','MATCHING','NO_STAFF_FOUND','ABSENT')
   UNION ALL
   SELECT 'settlement:'||id,status||':'||payment_status,'transactions','/admin/transactions?section=reconciliation','Đơn hoàn thành chờ quyết toán '||booking_code,
    CASE WHEN payment_method='CASH' AND payment_status<>'PAID' THEN 'Cần xác nhận tiền mặt trước khi quyết toán' ELSE 'Kiểm tra thanh toán và quyết toán thu nhập' END
   FROM bookings WHERE status='COMPLETED'
   """);}
}
