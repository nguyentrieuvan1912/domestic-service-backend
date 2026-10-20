package com.domesticservice.booking.controller;
import com.domesticservice.booking.service.BookingCallService;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/booking")
public class BookingCallController {
    private final BookingCallService service;
    public BookingCallController(BookingCallService service){this.service=service;}
    @GetMapping("/calls/capabilities") public Map<String,Object> capabilities(@RequestHeader(value="Authorization",required=false) String token){return service.capabilities(token);}
    @GetMapping("/calls/incoming") public List<Map<String,Object>> incoming(@RequestHeader(value="Authorization",required=false) String token){return service.incoming(token);}
    @PostMapping("/conversations/{id}/calls") public Map<String,Object> start(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long id,@RequestBody Map<String,Object> body){return service.start(token,id,body);}
    @GetMapping("/calls/{id}") public Map<String,Object> get(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long id){return service.get(token,id);}
    @PatchMapping("/calls/{id}") public Map<String,Object> action(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long id,@RequestBody Map<String,Object> body){return service.action(token,id,body);}
    @PostMapping("/calls/{id}/token") public Map<String,Object> join(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long id){return service.join(token,id);}
}
