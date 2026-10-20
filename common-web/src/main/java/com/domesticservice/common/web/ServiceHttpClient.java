package com.domesticservice.common.web;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;

/** HTTP-only helper. No shared persistence or domain entities. */
public final class ServiceHttpClient {
    private final RestClient client;
    public ServiceHttpClient(String baseUrl) {
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        client=RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
    public Object call(String method,String path,String token,Object body) {
        try {
            var request=client.method(HttpMethod.valueOf(method)).uri(path).accept(MediaType.APPLICATION_JSON);
            if(token!=null) request.header("Authorization",token);
            if(body!=null) request.contentType(MediaType.APPLICATION_JSON).body(body);
            return request.retrieve().body(Object.class);
        } catch(RestClientResponseException ex) {
            if(ex.getStatusCode().is4xxClientError()) throw new ResponseStatusException(ex.getStatusCode(),"Yêu cầu không hợp lệ hoặc không được phép ở dịch vụ liên quan.");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Dịch vụ liên quan chưa sẵn sàng. Vui lòng thử lại.");
        } catch(RestClientException ex) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Không kết nối được dịch vụ liên quan."); }
    }
    @SuppressWarnings("unchecked") public Map<String,Object> get(String path,String token) { return (Map<String,Object>)call("GET",path,token,null); }
    @SuppressWarnings("unchecked") public List<Map<String,Object>> list(String path,String token) { return (List<Map<String,Object>>)call("GET",path,token,null); }
    public Map<String,Object> authenticate(String token,String... roles) {
        if(token==null || !token.startsWith("Bearer ") || token.length()>200) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Vui lòng đăng nhập.");
        var user=get("/api/v1/identity/auth/me",token);
        if(roles.length>0 && !Arrays.asList(roles).contains(user.get("role"))) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Tài khoản không được thực hiện chức năng này.");
        return user;
    }
    public static long userId(Map<String,Object> user) { return ((Number)user.get("id")).longValue(); }
    public Object internalEvent(String key,Object body) {
        return client.post().uri("/api/v1/identity/internal/events").header("X-Service-Key",key).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(Object.class);
    }
}
