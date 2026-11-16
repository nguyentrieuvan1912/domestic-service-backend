package com.domesticservice.ai.controller;

import com.domesticservice.ai.service.AiBookingDraftService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai-assistant/conversations/{id}")
public class AiBookingDraftController {
    private final AiBookingDraftService service;

    public AiBookingDraftController(AiBookingDraftService service) {
        this.service = service;
    }

    @GetMapping("/booking-draft")
    public Map<String, Object> get(@RequestHeader(value = "Authorization", required = false) String token,
                                   @PathVariable long id, @RequestParam(required = false) Long draftId) {
        return service.get(token, id, draftId);
    }

    @GetMapping("/booking-drafts")
    public Map<String, Object> list(@RequestHeader(value = "Authorization", required = false) String token,
                                    @PathVariable long id) {
        return service.list(token, id);
    }

    @PatchMapping("/booking-draft")
    public Map<String, Object> patch(@RequestHeader(value = "Authorization", required = false) String token,
                                     @PathVariable long id, @RequestBody AiBookingDraftService.Patch body) {
        return service.patch(token, id, body);
    }
}
