package com.domesticservice.identity.service;

import com.domesticservice.identity.dto.AccountRequests.Status;
import com.domesticservice.identity.repository.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class CommunityReportService {
    private final AccountService accounts;
    private final CommunitySqlRepository community;
    private final CommunityReportRepository reports;
    private final ObjectMapper json;
    public CommunityReportService(AccountService accounts,CommunitySqlRepository community,CommunityReportRepository reports,ObjectMapper json){this.accounts=accounts;this.community=community;this.reports=reports;this.json=json;}
    private ResponseStatusException bad(String text){return new ResponseStatusException(HttpStatus.BAD_REQUEST,text);}
    private String text(Map<String,Object> b,String field,int max){
        if(b.get(field)!=null && !(b.get(field) instanceof String))throw bad("Thông tin báo cáo không hợp lệ.");
        String value=Objects.toString(b.get(field),"").strip();if(value.length()>max)throw bad("Nội dung quá dài.");return value;
    }
    @Transactional public Map<String,Object> report(String token,long post,long comment,Map<String,Object> b){
        var user=accounts.authenticate(token,false);String reason=text(b,"reason",20),detail=text(b,"detail",2000);
        if(!List.of("SPAM","MISINFORMATION","ABUSE","OTHER").contains(reason) || "OTHER".equals(reason) && detail.isBlank())throw bad("Chọn lý do báo cáo; lý do khác cần mô tả thêm.");
        community.lockUser(user.id());community.lockVisible(post,user.id(),"ADMIN".equals(user.role()));community.lockComment(post,comment);
        return reports.report(comment,user.id(),reason,detail);
    }
    public Map<String,Object> list(String token,String status,Long before){
        accounts.authenticate(token,true);
        if(!List.of("ALL","OPEN","RESOLVED","DISMISSED").contains(status) || before!=null && before<=0)throw bad("Bộ lọc báo cáo không hợp lệ.");
        var rows=new ArrayList<>(reports.list(status,before));boolean more=rows.size()>50;if(more)rows.removeLast();
        rows.forEach(row->row.put("media",json.readValue(row.get("media").toString(),new TypeReference<List<Map<String,Object>>>(){})));
        return Map.of("items",rows,"hasMore",more,"nextBefore",rows.isEmpty()?0:rows.getLast().get("id"));
    }
    @Transactional public Map<String,Object> resolve(String token,long id,Map<String,Object> b){
        var admin=accounts.authenticate(token,true);String action=text(b,"action",24),note=text(b,"note",2000);
        if(!List.of("DISMISS","DELETE","BLOCK","DELETE_AND_BLOCK").contains(action))throw bad("Thao tác không hợp lệ.");
        var initial=reports.find(id,false);long author=((Number)initial.get("author_id")).longValue();
        // Match writer lock order: account first, then report/comment. A blocked writer cannot commit afterwards.
        var account=reports.lockAuthor(author);var row=reports.find(id,true);
        if(!"OPEN".equals(row.get("status"))){
            if(!action.equals(row.get("resolution")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Báo cáo đã được xử lý. Hãy tải lại.");
            return Map.of("id",id,"status",row.get("status"));
        }
        boolean block=action.contains("BLOCK"),delete=action.startsWith("DELETE");
        if(block){
            if(!List.of("CUSTOMER","STAFF").contains(account.get("role")) || author==admin.id())throw bad("Không thể chặn tài khoản quản trị viên tại đây.");
            if(note.isBlank())throw bad("Nhập lý do chặn tài khoản.");
            accounts.status(author,account.get("role").toString(),new Status("LOCKED",note));
        }
        long comment=((Number)row.get("comment_id")).longValue();
        if(delete)reports.delete(comment,admin.id());
        reports.resolve(id,comment,action,note,admin.id(),delete);
        return Map.of("id",id,"status","DISMISS".equals(action)?"DISMISSED":"RESOLVED");
    }
}
