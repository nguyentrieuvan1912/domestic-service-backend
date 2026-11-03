package com.domesticservice.finance.service;

import com.domesticservice.common.web.ServiceHttpClient;
import com.domesticservice.finance.dto.StaffTopupRequest;
import com.domesticservice.finance.repository.support.NativeQueryExecutor;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CustomerWalletService {
    private final NativeQueryExecutor db;
    private final ServiceHttpClient identity, booking;
    private final RestClient internalBooking;
    private final String key;
    private final TransactionTemplate tx;
    private final CustomerTopupService topups;
    public CustomerWalletService(NativeQueryExecutor db, PlatformTransactionManager manager, CustomerTopupService topups,
        @Value("${clients.identity.base-url}") String identityUrl, @Value("${clients.booking.base-url}") String bookingUrl,
        @Value("${INTERNAL_SERVICE_TOKEN:}") String key) {
        this.db=db;this.topups=topups;this.tx=new TransactionTemplate(manager);this.key=key;
        identity=new ServiceHttpClient(identityUrl);booking=new ServiceHttpClient(bookingUrl);
        var factory=new org.springframework.http.client.JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(4)).build());
        factory.setReadTimeout(java.time.Duration.ofSeconds(10));
        internalBooking=RestClient.builder().baseUrl(bookingUrl).requestFactory(factory).build();
    }
    private static long n(Map<String,Object> row,String name){return ((Number)row.get(name)).longValue();}
    private static ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
    public void authorize(String supplied){if(key.isBlank() || !java.security.MessageDigest.isEqual(key.getBytes(java.nio.charset.StandardCharsets.UTF_8),Objects.toString(supplied,"").getBytes(java.nio.charset.StandardCharsets.UTF_8)))throw new ResponseStatusException(HttpStatus.FORBIDDEN);}
    private void lock(long customer){db.update("INSERT INTO customer_wallets(customer_id) VALUES (?) ON CONFLICT DO NOTHING",customer);db.queryForMap("SELECT * FROM customer_wallets WHERE customer_id=? FOR UPDATE",customer);}
    public Map<String,Object> wallet(String token){
        long customer=ServiceHttpClient.userId(identity.authenticate(token,"CUSTOMER"));
        return tx.execute(state->{lock(customer);return Map.of("available",db.queryForObject("SELECT available_balance FROM customer_wallets WHERE customer_id=?",Long.class,customer),"transactions",db.queryForList("SELECT id,direction,transaction_type AS type,amount,booking_id AS \"bookingId\",created_at AS \"createdAt\" FROM customer_wallet_transactions WHERE customer_id=? ORDER BY id DESC LIMIT 100",customer));});
    }
    public Map<String,Object> pay(String token,long id){
        if(key.isBlank())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Chưa cấu hình kết nối thanh toán nội bộ.");
        long customer=ServiceHttpClient.userId(identity.authenticate(token,"CUSTOMER"));
        var book=booking.get("/api/v1/booking/bookings/"+id,token);
        if(n(book,"customerId")!=customer)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if(!Boolean.TRUE.equals(book.get("pricingConfirmed")))throw conflict("Báo giá chưa được xác nhận.");
        if(!"AWAITING_PAYMENT".equals(book.get("status"))) {
            var existing=db.queryForList("SELECT * FROM customer_booking_payments WHERE booking_id=? AND customer_id=?",id,customer);
            if(existing.isEmpty())throw conflict("Đơn không còn chờ thanh toán.");
            reconcile(id);return status(id);
        }
        if("BANK_TRANSFER_QR".equals(book.get("paymentMethod"))){
            var topup=topups.create(token,new StaffTopupRequest(n(book,"totalAmount"),"booking-"+id),id);
            reconcile(id);return Map.of("topup",topup,"payment",status(id));
        }
        if(!"WALLET".equals(book.get("paymentMethod")))throw conflict("Đơn không dùng thanh toán ví.");
        debit(book,null);
        sync(id);
        return status(id);
    }
    // Always lock the customer's wallet first, then payment/topup. Each debit is atomic and unique per booking.
    private void debit(Map<String,Object> book,Long topupId){
        long id=n(book,"id"),customer=n(book,"customerId"),amount=n(book,"totalAmount");
        tx.executeWithoutResult(state->{
            lock(customer);
            if(!db.queryForList("SELECT booking_id FROM customer_booking_payments WHERE booking_id=?",id).isEmpty())return;
            if(topupId!=null){
                var row=db.queryForMap("SELECT * FROM customer_topups WHERE id=? FOR UPDATE",topupId);
                if(!"PAID".equals(row.get("status")) || n(row,"customer_id")!=customer || n(row,"amount")!=amount || n(row,"booking_id")!=id)return;
            }
            int changed=db.update("UPDATE customer_wallets SET available_balance=available_balance-?,updated_at=now() WHERE customer_id=? AND available_balance>=?",amount,customer,amount);
            if(changed!=1)throw conflict("Số dư ví không đủ. Vui lòng nạp thêm tiền.");
            long payment=db.insert("INSERT INTO payments(booking_id,customer_id,payment_code,amount,payment_method,provider,transaction_code,status,paid_at) VALUES (?,?,?,?,?,?,?,'PAID',now())",id,customer,"CUSTOMER-"+id,amount,book.get("paymentMethod"),topupId==null?"CUSTOMER_WALLET":"PAYOS",topupId==null?"wallet-booking-"+id:"customer-topup-"+topupId);
            db.insert("INSERT INTO customer_wallet_transactions(customer_id,direction,transaction_type,amount,booking_id,idempotency_key) VALUES (?,'DEBIT','PAYMENT',?,?,?)",customer,amount,id,"booking:"+id+":pay");
            db.update("INSERT INTO customer_booking_payments(booking_id,customer_id,payment_id,amount) VALUES (?,?,?,?)",id,customer,payment,amount);
        });
    }
    public Map<String,Object> status(long id){
        var rows=db.queryForList("SELECT amount,refunded FROM customer_booking_payments WHERE booking_id=?",id);
        if(rows.isEmpty())return Map.of("paidAmount",0L,"refundedAmount",0L);
        var p=rows.getFirst();return Map.of("paidAmount",p.get("amount"),"refundedAmount",Boolean.TRUE.equals(p.get("refunded"))?p.get("amount"):0L);
    }
    @SuppressWarnings("unchecked") private Map<String,Object> snapshot(long id){return internalBooking.get().uri("/api/v1/booking/internal/bookings/"+id).header("X-Service-Key",key).retrieve().body(Map.class);}
    private void sync(long id){internalBooking.post().uri("/api/v1/booking/internal/bookings/"+id+"/reconcile-payment").header("X-Service-Key",key).retrieve().toBodilessEntity();}
    public void reconcile(long id){
        if(key.isBlank())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Chưa cấu hình kết nối thanh toán nội bộ.");
        var book=snapshot(id);
        if("AWAITING_PAYMENT".equals(book.get("status")) && "BANK_TRANSFER_QR".equals(book.get("paymentMethod"))){
            var rows=db.queryForList("SELECT id FROM customer_topups WHERE booking_id=? AND status='PAID'",id);
            if(!rows.isEmpty())debit(book,n(rows.getFirst(),"id"));
        }
        if(List.of("CANCELLED","NO_STAFF_FOUND","REJECTED","REFUNDED").contains(book.get("status"))){
            tx.executeWithoutResult(state->{
                lock(n(book,"customerId"));
                var rows=db.queryForList("SELECT * FROM customer_booking_payments WHERE booking_id=? FOR UPDATE",id);
                if(rows.isEmpty() || Boolean.TRUE.equals(rows.getFirst().get("refunded")))return;
                var p=rows.getFirst();long amount=n(p,"amount"),customer=n(p,"customer_id");
                db.insert("INSERT INTO refunds(payment_id,amount,reason,status,transaction_code,completed_at,admin_note) VALUES (?,?,?,'PROCESSED',?,now(),'Hoàn vào ví khách hàng')",p.get("payment_id"),amount,"Đơn hủy hoặc không tìm được nhân viên","wallet-refund-"+id);
                db.insert("INSERT INTO customer_wallet_transactions(customer_id,direction,transaction_type,amount,booking_id,idempotency_key) VALUES (?,'CREDIT','REFUND',?,?,?)",customer,amount,id,"booking:"+id+":refund");
                db.update("UPDATE customer_wallets SET available_balance=available_balance+?,updated_at=now() WHERE customer_id=?",amount,customer);
                db.update("UPDATE customer_booking_payments SET refunded=true,reconciled_at=NULL WHERE booking_id=?",id);
                // Keep gross payment PAID; processed refunds are tracked separately by existing finance reports.
            });
        }
        sync(id);
        tx.executeWithoutResult(state->db.update("UPDATE customer_booking_payments SET reconciled_at=now() WHERE booking_id=?",id));
    }
    @Scheduled(fixedDelay=5000,initialDelay=15000)
    public void reconcilePending(){
        if(key.isBlank())return;
        var ids=db.queryForList("SELECT booking_id FROM (SELECT booking_id,coalesce(reconciled_at,'epoch'::timestamptz) AS checked FROM customer_booking_payments WHERE NOT refunded OR reconciled_at IS NULL UNION ALL SELECT t.booking_id,coalesce(p.reconciled_at,'epoch'::timestamptz) FROM customer_topups t LEFT JOIN customer_booking_payments p ON p.booking_id=t.booking_id WHERE t.booking_id IS NOT NULL AND t.status='PAID' AND p.booking_id IS NULL) q GROUP BY booking_id ORDER BY min(checked) LIMIT 30",Long.class);
        for(long id:ids)try{reconcile(id);}catch(RuntimeException ignored){/* Retry durable intent after recovery. */}
    }
}
