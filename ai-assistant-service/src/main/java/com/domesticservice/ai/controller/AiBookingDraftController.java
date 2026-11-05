package com.domesticservice.ai.controller;

import com.domesticservice.ai.service.AiBookingDraftService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai-assistant/conversations/{id}/booking-draft")
public class AiBookingDraftController {
    private final AiBookingDraftService service;

    public AiBookingDraftController(AiBookingDraftService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> get(@RequestHeader(value = "Authorization", required = false) String token,
                                   @PathVariable long id) {
        return service.get(token, id);
    }

    @PatchMapping
    public Map<String, Object> patch(@RequestHeader(value = "Authorization", required = false) String token,
                                     @PathVariable long id, @RequestBody AiBookingDraftService.Patch body) {
        return service.patch(token, id, body);
    }
}
