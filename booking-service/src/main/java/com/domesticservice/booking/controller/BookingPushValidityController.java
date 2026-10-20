package com.domesticservice.booking.controller;
import com.domesticservice.booking.repository.BookingPushValidityRepository;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/booking/internal")
public class BookingPushValidityController {
    private final BookingPushValidityRepository repository;private final String key;
    public BookingPushValidityController(BookingPushValidityRepository repository,@Value("${INTERNAL_SERVICE_TOKEN:}") String key){this.repository=repository;this.key=key;}
    @PostMapping("/push-validity") public Map<String,Object> valid(@RequestHeader(value="X-Service-Key",required=false) String supplied,@RequestBody Map<String,Object> data){
        if(key.isBlank()||supplied==null||!MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8)))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Không được phép.");
        return Map.of("valid",data.get("userId") instanceof Number user && repository.valid(data,user.longValue()));
    }
}
