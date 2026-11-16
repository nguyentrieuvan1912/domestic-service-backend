package com.domesticservice.ai.controller;

import com.domesticservice.ai.service.KnowledgeDocuments;
import com.domesticservice.ai.service.AiCustomerKnowledge;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/ai-assistant/admin/knowledge")
public class KnowledgeController {
    private final KnowledgeDocuments documents;
    private final AiCustomerKnowledge knowledge;

    KnowledgeController(KnowledgeDocuments documents, AiCustomerKnowledge knowledge) {
        this.documents = documents;
        this.knowledge = knowledge;
    }

    @GetMapping
    List<Map<String, Object>> list(@RequestHeader(value="Authorization", required=false) String token) { return documents.list(token); }
    @GetMapping("/{id}")
    Map<String, Object> detail(@RequestHeader(value="Authorization", required=false) String token, @PathVariable long id) { return documents.detail(token, id); }
    @GetMapping("/{id}/history")
    List<Map<String, Object>> history(@RequestHeader(value="Authorization", required=false) String token, @PathVariable long id) { return documents.history(token, id); }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> create(@RequestHeader(value="Authorization", required=false) String token, @RequestBody KnowledgeDocuments.Edit body) {
        return documents.save(token, null, body);
    }
    @PutMapping("/{id}")
    Map<String, Object> edit(@RequestHeader(value="Authorization", required=false) String token, @PathVariable long id, @RequestBody KnowledgeDocuments.Edit body) {
        return documents.save(token, id, body);
    }
    @PostMapping("/{id}/{action:approve|hide|reindex}")
    Map<String, Object> transition(@RequestHeader(value="Authorization", required=false) String token, @PathVariable long id,
                                  @PathVariable String action, @RequestBody KnowledgeDocuments.Version body) {
        return documents.transition(token, id, body.version(), action);
    }
    @PostMapping("/search")
    Object search(@RequestHeader(value="Authorization", required=false) String token, @RequestBody Map<String, String> body) {
        documents.admin(token);
        String query = body.getOrDefault("query", "").strip();
        if (query.isBlank() || query.length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nhập câu hỏi từ 1 đến 2.000 ký tự.");
        }
        return knowledge.search(query);
    }
}
