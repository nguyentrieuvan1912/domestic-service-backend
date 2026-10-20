package com.domesticservice.booking.service;
import com.domesticservice.booking.repository.BookingReminderRepository;
import com.domesticservice.common.web.ServiceHttpClient;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
@Service @EnableScheduling
public class BookingReminderService {
    private final BookingReminderRepository repository;
    private final ServiceHttpClient identity;
    private final String key;
    private final TransactionTemplate tx;
    public BookingReminderService(BookingReminderRepository repository,PlatformTransactionManager manager,@Value("${clients.identity.base-url}") String url,@Value("${INTERNAL_SERVICE_TOKEN:}") String key){this.repository=repository;this.identity=new ServiceHttpClient(url);this.key=key;this.tx=new TransactionTemplate(manager);}
    @Scheduled(fixedDelay=15000,initialDelay=15000)
    public void dispatch(){
        if(key.isBlank())return;
        try { for(long id:repository.candidates())try { tx.executeWithoutResult(state->{for(var row:repository.due(id))if(repository.claim(row)){
            int minutes=((Number)row.get("minutes")).intValue();
            identity.internalEvent(key,Map.of("eventKey","reminder:"+row.get("assignment_id")+":"+row.get("starts_at")+":"+minutes,
                "userIds",List.of(row.get("staff_id")),"type","SCHEDULE_UPDATE","title",minutes==60?"Ca làm bắt đầu sau 1 giờ":"Còn 30 phút đến ca làm",
                "content","Đơn "+row.get("booking_code")+". "+(minutes==60?"Hãy chuẩn bị dụng cụ và kiểm tra địa chỉ.":"Hãy chuẩn bị di chuyển để đến đúng giờ."),
                "referenceId",id,"data",Map.of("bookingId",id,"assignmentId",row.get("assignment_id"),"reminderMinutes",minutes,"startsAt",((java.sql.Timestamp)row.get("starts_at")).toInstant().toString(),"expiresAt",((java.sql.Timestamp)row.get("starts_at")).toInstant().minusSeconds(minutes==60?1800:0).toString()),"bookingCreated",false));
        }}); }catch(RuntimeException ignored){ /* Transaction rollback and event key allow retry without duplicate notification. */ }
        }catch(RuntimeException ignored){ /* Retry when database recovers. */ }
    }
}
