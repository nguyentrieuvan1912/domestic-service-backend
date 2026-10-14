package com.domesticservice.catalog.controller;
import com.domesticservice.catalog.application.PriceQuoteService;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/catalog")
public class PriceQuoteController {
    private final PriceQuoteService service;public PriceQuoteController(PriceQuoteService service){this.service=service;}
    @PostMapping("/quote-preview")public Map<String,Object>preview(@RequestHeader(value="Authorization",required=false)String token,@RequestBody Map<String,Object>b){return service.preview(token,b);}
    @PostMapping("/quotes")public Map<String,Object>create(@RequestHeader(value="Authorization",required=false)String token,@RequestBody Map<String,Object>b){return service.create(token,b);}
    @PostMapping("/quotes/{id}/consume")public Map<String,Object>consume(@RequestHeader(value="Authorization",required=false)String token,@PathVariable long id,@RequestBody Map<String,Object>b){return service.consume(token,id,bookingId(b));}
    private static long bookingId(Map<String,Object> b){try{long id=Long.parseLong(Objects.toString(b.get("bookingId"),""));if(id<1)throw new NumberFormatException();return id;}catch(NumberFormatException ex){throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"ID đơn không hợp lệ.");}}
}
