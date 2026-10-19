package com.domesticservice.identity.service;

import com.domesticservice.identity.service.AccountService;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Service;
import com.domesticservice.identity.repository.CommunitySqlRepository;

@Service()
public class CommunityService {

    private final AccountService accounts;

    private final CommunitySqlRepository repository;

    public CommunityService(AccountService accounts, CommunitySqlRepository repository) {
        this.accounts = accounts;
        this.repository = repository;
    }

    private String text(Map<String, Object> b, int max) {
        String value = Objects.toString(b.get("content"), "").strip();
        if (value.isEmpty() || value.length() > max)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nội dung không hợp lệ.");
        return value;
    }

    private void visible(long id, long user, boolean admin) {
        if (!Boolean.TRUE.equals(repository.visibleExistsCommunityPosts(Boolean.class, id, user, admin)))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bài viết.");
    }

    private List<Map<String, Object>> posts(long user, boolean admin) {
        var result = repository.postsExistsCommunityLikes(user, user, admin);
        for (var item : result) {
            item.put("comments", repository.postsFindCommunityComments(item.get("id")));
            if (!admin)
                item.remove("moderationNote");
        }
        return result;
    }

    public List<Map<String, Object>> list(String token) {
        return posts(token == null ? 0 : accounts.authenticate(token, false).id(), false);
    }

    public List<Map<String, Object>> admin(String token) {
        return posts(accounts.authenticate(token, true).id(), true);
    }

    @Transactional
    public Map<String, Object> create(String token, Map<String, Object> b) {
        var user = accounts.authenticate(token, false);
        String content = text(b, 5000);
        if (b.get("media") instanceof List<?> media && !media.isEmpty())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Chưa cấu hình lưu trữ ảnh/video. Bài có tệp chưa được đăng; bạn có thể đăng nội dung văn bản.");
        String status = "ADMIN".equals(user.role()) ? "PUBLISHED" : "PENDING";
        long id = repository.createFindCommunityPosts(Long.class, user.id(), content, status);
        return Map.of("id", id, "status", status);
    }

    @Transactional
    public Map<String, Object> comment(String token, long id, Map<String, Object> b) {
        var user = accounts.authenticate(token, false);
        visible(id, user.id(), "ADMIN".equals(user.role()));
        long result = repository.commentFindCommunityComments(Long.class, id, user.id(), text(b, 2000));
        return Map.of("id", result);
    }

    @Transactional
    public Map<String, Object> like(String token, long id, Map<String, Object> b) {
        var user = accounts.authenticate(token, false);
        visible(id, user.id(), "ADMIN".equals(user.role()));
        if (!(b.get("liked") instanceof Boolean liked))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái thích không hợp lệ.");
        if (liked)
            repository.likeInsertCommunityLikes(id, user.id());
        else
            repository.likeDeleteCommunityLikes(id, user.id());
        return Map.of("id", id, "isLiked", liked, "likes", repository.likeCountCommunityLikes(Long.class, id));
    }

    @Transactional
    public Map<String, Object> moderate(String token, long id, Map<String, Object> b) {
        var user = accounts.authenticate(token, true);
        String status = Objects.toString(b.get("status"), ""), note = Objects.toString(b.get("note"), "");
        if (!List.of("PENDING", "PUBLISHED", "HIDDEN").contains(status) || note.length() > 2000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái kiểm duyệt không hợp lệ.");
        if (repository.moderateUpdateCommunityPosts(status, note, user.id(), id) == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bài viết.");
        return Map.of("id", id, "status", status);
    }
}
