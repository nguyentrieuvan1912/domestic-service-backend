package com.domesticservice.booking.controller;

import com.domesticservice.booking.service.BookingChatService;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/booking/conversations")
public class BookingChatController {

    private final BookingChatService service;

    public BookingChatController(BookingChatService service) {
        this.service = service;
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestHeader(value = "Authorization", required = false) String token) {
        return service.list(token);
    }

    @GetMapping("/{id}/messages")
    public List<Map<String, Object>> messages(@RequestHeader(value = "Authorization", required = false) String token, @PathVariable long id) {
        return service.messages(token, id);
    }

    @PostMapping("/{id}/messages")
    public Map<String, Object> send(@RequestHeader(value = "Authorization", required = false) String token, @PathVariable long id, @RequestBody Map<String, Object> b) {
        return service.send(token, id, b);
    }

    @PatchMapping("/{id}/read")
    public Map<String, Object> read(@RequestHeader(value = "Authorization", required = false) String token, @PathVariable long id, @RequestBody(required = false) Map<String, Object> body) {
        return service.read(token, id, body);
    }
}
