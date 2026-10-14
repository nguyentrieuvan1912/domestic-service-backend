package com.domesticservice.catalog.controller;
import com.domesticservice.catalog.application.CatalogAdminService;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/catalog/admin")
public class CatalogAdminController {
    private final CatalogAdminService service; public CatalogAdminController(CatalogAdminService service){this.service=service;}
    @GetMapping("/snapshot") public Map<String,Object> snapshot(@RequestHeader(value="Authorization",required=false)String token){return service.snapshot(token);}
    @PostMapping("/catalog/{kind}") @ResponseStatus(HttpStatus.CREATED) public Map<String,Object> create(@RequestHeader(value="Authorization",required=false)String token,@PathVariable String kind,@RequestBody Map<String,Object>body){return service.save(token,kind,null,body);}
    @PutMapping("/catalog/{kind}/{id}") public Map<String,Object> update(@RequestHeader(value="Authorization",required=false)String token,@PathVariable String kind,@PathVariable long id,@RequestBody Map<String,Object>body){return service.save(token,kind,id,body);}
    @GetMapping("/promotions") public List<Map<String,Object>> promotions(@RequestHeader(value="Authorization",required=false)String token){return service.promotions(token);}
    @PostMapping("/promotions") @ResponseStatus(HttpStatus.CREATED) public Map<String,Object> createPromo(@RequestHeader(value="Authorization",required=false)String token,@RequestBody Map<String,Object>body){return service.savePromotion(token,null,body);}
    @PutMapping("/promotions/{id}") public Map<String,Object> updatePromo(@RequestHeader(value="Authorization",required=false)String token,@PathVariable long id,@RequestBody Map<String,Object>body){return service.savePromotion(token,id,body);}
}
