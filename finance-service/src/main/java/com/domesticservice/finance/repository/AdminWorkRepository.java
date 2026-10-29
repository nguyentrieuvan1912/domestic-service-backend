package com.domesticservice.finance.repository;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class AdminWorkRepository {
 private final JdbcTemplate db;
 public AdminWorkRepository(JdbcTemplate db){this.db=db;}
 public Map<String,Object> snapshot(){
  var items=db.queryForList("""
   SELECT 'bank:'||id AS key,status AS state,'transactions' AS section,'/admin/transactions?section=withdrawals' AS target,
    'Tài khoản ngân hàng chờ xác minh #'||id AS title,'Nhân viên #'||staff_id AS summary
   FROM staff_bank_accounts WHERE status='PENDING' OR (status='VERIFIED' AND verified_at IS NULL)
   UNION ALL
   SELECT 'withdrawal:'||w.id,w.status||':'||coalesce(p.status,''),'transactions','/admin/transactions?section=withdrawals',
    'Yêu cầu rút tiền #'||w.id,CASE w.status WHEN 'PENDING' THEN 'Chờ duyệt' WHEN 'APPROVED' THEN 'Chờ admin chuyển tiền' ELSE 'Cần kiểm tra kết quả chuyển khoản' END
   FROM withdrawals w LEFT JOIN withdrawal_payouts p ON p.withdrawal_id=w.id WHERE w.status IN ('PENDING','APPROVED','PROCESSING') OR p.status='REVIEW'
   UNION ALL
   SELECT 'refund:'||id,status,'transactions','/admin/transactions?section=transactions','Hoàn tiền cần xử lý #'||id,'Trạng thái: '||status FROM refunds WHERE status IN ('PENDING','APPROVED','PROCESSING','FAILED')
   UNION ALL
   SELECT 'reward:'||id,status,'transactions','/admin/transactions?section=reconciliation','Khoản thưởng chờ duyệt #'||id,left(reason,180) FROM rewards WHERE status='PENDING'
   UNION ALL
   SELECT 'penalty:'||id,status,'transactions','/admin/transactions?section=reconciliation','Khoản phạt cần xử lý #'||id,left(reason,180) FROM penalties WHERE status IN ('PENDING','APPEALED')
   UNION ALL
   SELECT 'topup:'||id,status,'transactions','/admin/transactions?section=topups','Nạp tiền cần đối soát #'||id,'Nhân viên #'||staff_id FROM staff_topups WHERE status='REVIEW' OR (status='CREATING' AND created_at<now()-interval '2 minutes')
   """);
  return Map.of("items",items,"settledBookingIds",db.queryForList("SELECT DISTINCT booking_id FROM invoices WHERE status='PAID'",Long.class));
 }
}
