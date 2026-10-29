package com.domesticservice.identity.service;

import com.domesticservice.common.web.ServiceHttpClient;
import com.domesticservice.identity.dto.AdminWorkItem;
import com.domesticservice.identity.repository.AdminInboxRepository;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminInboxService {
 private final AccountService accounts;private final AdminInboxRepository repo;
 private final ServiceHttpClient finance,booking;
 private final ReentrantLock[] locks=new ReentrantLock[64];
 private record SyncState(Instant at,List<String> unavailable){}
 private final Map<Long,SyncState> states=new ConcurrentHashMap<>();
 public AdminInboxService(AccountService accounts,AdminInboxRepository repo,@Value("${clients.finance.base-url}") String financeUrl,@Value("${clients.booking.base-url}") String bookingUrl){
  this.accounts=accounts;this.repo=repo;finance=new ServiceHttpClient(financeUrl);booking=new ServiceHttpClient(bookingUrl);
  Arrays.setAll(locks,i->new ReentrantLock());
 }
 public Map<String,Object> list(String token,Long before,String filter){
  long user=accounts.authenticate(token,true).id();
  if(!List.of("all","unread","pending").contains(filter))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Bộ lọc không hợp lệ.");
  var result=new LinkedHashMap<String,Object>(repo.snapshot(user,before,filter));var state=states.get(user);result.put("unavailableSources",state==null?List.of():state.unavailable());result.put("syncedAt",state==null?null:state.at());return result;
 }
 private Map<String,Object> response(long user,Long before){var result=new LinkedHashMap<String,Object>(repo.snapshot(user,before));var state=states.get(user);result.put("unavailableSources",state==null?List.of():state.unavailable());result.put("syncedAt",state==null?null:state.at());return result;}
 private static List<AdminWorkItem> items(List<Map<String,Object>> rows){return rows.stream().map(row->new AdminWorkItem(row.get("key").toString(),row.get("state").toString(),row.get("section").toString(),row.get("target").toString(),row.get("title").toString(),Objects.toString(row.get("summary"),""))).toList();}
 @SuppressWarnings("unchecked")
 public Map<String,Object> sync(String token){
  long user=accounts.authenticate(token,true).id();var lock=locks[(int)(user%locks.length)];
  if(!lock.tryLock())return response(user,null);
  try{
   var previous=states.get(user);if(previous!=null && previous.at().isAfter(Instant.now().minusSeconds(8)))return response(user,null);
   var unavailable=new ArrayList<String>();
   try{repo.apply(user,"identity",items(repo.localWork()));}catch(RuntimeException ex){unavailable.add("identity");}
   // Each service reads its own database. A failed source retains its previous queue and cannot silently clear pending work.
   Map<String,Object> money=null;List<Map<String,Object>> jobs=null;
   try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
    var financeResult=pool.submit(()->finance.get("/api/v1/finance/admin/work-items",token));
    var bookingResult=pool.submit(()->booking.list("/api/v1/booking/admin/work-items",token));
    try{money=financeResult.get();repo.apply(user,"finance",items((List<Map<String,Object>>)money.get("items")));}catch(Exception ex){unavailable.add("finance");}
    try{jobs=bookingResult.get();repo.apply(user,"booking",items(jobs.stream().filter(row->!row.get("key").toString().startsWith("settlement:")).toList()));}catch(Exception ex){unavailable.add("booking");}
   }
   if(money!=null && jobs!=null && !unavailable.contains("finance") && !unavailable.contains("booking")){
    var settled=new HashSet<String>();((List<?>)money.get("settledBookingIds")).forEach(id->settled.add(id.toString()));
    repo.apply(user,"settlement",items(jobs.stream().filter(row->row.get("key").toString().startsWith("settlement:") && !settled.contains(row.get("key").toString().substring(11))).toList()));
   }
   states.put(user,new SyncState(Instant.now(),List.copyOf(unavailable)));return response(user,null);
  }finally{lock.unlock();}
 }
 public Map<String,Object> read(String token,long id){long user=accounts.authenticate(token,true).id();if(!repo.markRead(user,id))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy thông báo.");return response(user,null);}
 public Map<String,Object> readAll(String token,long through){long user=accounts.authenticate(token,true).id();if(through<1)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Mốc thông báo không hợp lệ.");repo.readAll(user,through);return response(user,null);}
}
