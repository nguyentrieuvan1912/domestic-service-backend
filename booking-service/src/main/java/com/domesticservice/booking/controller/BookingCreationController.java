package com.domesticservice.booking.controller;
import com.domesticservice.booking.application.BookingCreationService;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/booking")
public class BookingCreationController {
    private final BookingCreationService service;public BookingCreationController(BookingCreationService service){this.service=service;}
    @PostMapping("/bookings") @ResponseStatus(HttpStatus.CREATED)public Map<String,Object>create(@RequestHeader(value="Authorization",required=false)String token,@RequestBody Map<String,Object>b){return service.create(token,b);}
    @GetMapping("/customer/staff")public List<Map<String,Object>>search(@RequestHeader(value="Authorization",required=false)String token,@RequestParam long serviceId,@RequestParam long addressId,@RequestParam String startsAt,@RequestParam(defaultValue="60")int durationMinutes){return service.staffSearch(token,serviceId,addressId,startsAt,durationMinutes);}
}
