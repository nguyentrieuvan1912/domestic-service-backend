package com.domesticservice.booking.application;
import com.domesticservice.common.web.ServiceHttpClient;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
@Service
public class BookingPaymentVerification {
    private final BookingOperationsService ops;private final JdbcTemplate db;private final ServiceHttpClient finance;
    public BookingPaymentVerification(BookingOperationsService ops,JdbcTemplate db,@Value("${clients.finance.base-url}")String url){this.ops=ops;this.db=db;finance=new ServiceHttpClient(url);}
    @Transactional public Map<String,Object>verify(String token,long id){ops.actor(token,"ADMIN");var proof=finance.get("/api/v1/finance/bookings/"+id+"/payment-status",token);var rows=db.queryForList("SELECT total_amount,payment_status FROM bookings WHERE id=? FOR UPDATE",id);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy đơn.");long total=((Number)rows.getFirst().get("total_amount")).longValue(),paid=((Number)proof.get("paidAmount")).longValue(),refunded=((Number)proof.get("refundedAmount")).longValue();String state=paid>0&&refunded>=paid?"REFUNDED":paid-refunded>=total?"PAID":"PENDING";db.update("UPDATE bookings SET payment_status=?,updated_at=now() WHERE id=?",state,id);return Map.of("id",id,"paymentStatus",state);}
}
