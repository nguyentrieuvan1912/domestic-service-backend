package com.domesticservice.booking.controller;
import java.util.*;
import com.domesticservice.booking.repository.AdminWorkRepository;
import com.domesticservice.booking.service.BookingOperationsService;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/booking/admin/work-items")
public class AdminWorkController {
 private final AdminWorkRepository repo;private final BookingOperationsService ops;
 public AdminWorkController(AdminWorkRepository repo,BookingOperationsService ops){this.repo=repo;this.ops=ops;}
 @GetMapping public List<Map<String,Object>> get(@RequestHeader(value="Authorization",required=false) String token){ops.actor(token,"ADMIN");return repo.snapshot();}
}
