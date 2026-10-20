package com.domesticservice.identity.service;
import com.domesticservice.identity.repository.CommunityMediaRepository;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
@Service
public class CommunityMediaService {
    private final AccountService accounts;private final CommunityMediaRepository repository;private final CloudinaryClient cloud;private final TransactionTemplate tx;
    public CommunityMediaService(AccountService accounts,CommunityMediaRepository repository,CloudinaryClient cloud,PlatformTransactionManager manager){this.accounts=accounts;this.repository=repository;this.cloud=cloud;tx=new TransactionTemplate(manager);}
    public Map<String,Object> capabilities(){return Map.of("enabled",cloud.ready(),"maxFiles",4,"maxImageBytes",10*1024*1024,"maxVideoBytes",50*1024*1024,"maxVideoSeconds",120);}
    public Map<String,Object> sign(String token,Map<String,Object> body){
        var user=accounts.authenticate(token,false);cloud.requireReady();
        String type=Objects.toString(body.get("type"),"");if(!List.of("image","video").contains(type))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Chỉ hỗ trợ ảnh hoặc video.");
        String publicId="cleanmaster/community/"+user.id()+"/"+UUID.randomUUID();
        long id=tx.execute(state->repository.reserve(user.id(),publicId,type));return cloud.ticket(id,publicId,type,Instant.now().getEpochSecond());
    }
    public Map<String,Object> complete(String token,long id){
        var user=accounts.authenticate(token,false);var row=repository.find(id,user.id(),false);
        check(row);
        if("RESERVED".equals(row.get("status"))){
            if(((java.sql.Timestamp)row.get("expires_at")).toInstant().isBefore(Instant.now()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Phiên tải tệp đã hết hạn. Hãy chọn lại tệp.");
            var asset=cloud.inspect(row.get("public_id").toString(),row.get("resource_type").toString());
            tx.executeWithoutResult(state->{var locked=repository.find(id,user.id(),true);check(locked);repository.verified(id,asset);});
        }
        return view(repository.find(id,user.id(),false));
    }
    private void check(Map<String,Object> row){if(row==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy tệp của bạn.");}
    public static Map<String,Object> view(Map<String,Object> row){return Map.of("id",row.get("id"),"uri",row.get("secure_url"),"type",row.get("resource_type"),"width",row.get("width"),"height",row.get("height"));}
}
