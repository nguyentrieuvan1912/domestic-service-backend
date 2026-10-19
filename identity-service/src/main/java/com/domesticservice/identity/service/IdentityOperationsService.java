package com.domesticservice.identity.service;

import com.domesticservice.identity.service.AccountService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import com.domesticservice.identity.repository.IdentityOperationsSqlRepository;

@Service()
public class IdentityOperationsService {

    private final AccountService accounts;

    private final IdentityOperationsSqlRepository repository;

    private final ObjectMapper json;

    private final String serviceKey;

    public IdentityOperationsService(AccountService accounts, IdentityOperationsSqlRepository repository, ObjectMapper json, @Value("${INTERNAL_SERVICE_TOKEN:}") String key) {
        this.accounts = accounts;
        this.repository = repository;
        this.json = json;
        serviceKey = key;
    }

    @Transactional
    public Map<String, Object> event(String key, Map<String, Object> b) {
        if (serviceKey.isBlank() || key == null || !MessageDigest.isEqual(serviceKey.getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không có quyền gửi sự kiện nội bộ.");
        String event = Objects.toString(b.get("eventKey"), ""), type = Objects.toString(b.get("type"), "SYSTEM"), title = Objects.toString(b.get("title"), ""), content = Objects.toString(b.get("content"), "");
        if (event.isBlank() || event.length() > 200 || type.length() > 50 || title.isBlank() || title.length() > 255 || content.isBlank() || content.length() > 5000 || !(b.get("userIds") instanceof List<?> ids) || ids.size() > 200)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sự kiện không hợp lệ.");
        int changed = 0;
        for (Object raw : ids) {
            long id;
            try {
                id = Long.parseLong(raw.toString());
            } catch (NumberFormatException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID người nhận không hợp lệ.");
            }
            var person = repository.eventFindUsers(id);
            if (person.isEmpty())
                continue;
            int inserted = repository.eventInsertNotifications(id, type, title, content, b.get("referenceId"), json.writeValueAsString(b.getOrDefault("data", Map.of())), event);
            changed += inserted;
            if (inserted > 0 && Boolean.TRUE.equals(b.get("bookingCreated")) && "CUSTOMER".equals(person.getFirst().get("role")))
                repository.eventUpdateCustomers(id);
        }
        return Map.of("created", changed);
    }

    public List<Map<String, Object>> support(String token) {
        accounts.authenticate(token, true);
        return repository.supportFindSupportRequests();
    }

    @Transactional
    public Map<String, Object> support(String token, long id, Map<String, Object> b) {
        accounts.authenticate(token, true);
        String state = Objects.toString(b.get("status"), ""), note = Objects.toString(b.get("adminNote"), "");
        if (!List.of("RECEIVED", "IN_PROGRESS", "RESOLVED").contains(state) || note.length() > 2000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nội dung xử lý không hợp lệ.");
        if (repository.supportUpdateSupportRequests(state, note, id) == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu hỗ trợ.");
        return Map.of("id", id, "status", state);
    }
}
