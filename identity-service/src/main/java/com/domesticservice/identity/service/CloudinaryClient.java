package com.domesticservice.identity.service;

import com.domesticservice.identity.config.CloudinaryProperties;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
@EnableConfigurationProperties(CloudinaryProperties.class)
public class CloudinaryClient {
    private final CloudinaryProperties config;private final ObjectMapper json;private final HttpClient http;private final String base;
    @Autowired public CloudinaryClient(CloudinaryProperties config,ObjectMapper json){this(config,json,HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build(),"https://api.cloudinary.com");}
    CloudinaryClient(CloudinaryProperties config,ObjectMapper json,HttpClient http,String base){this.config=config;this.json=json;this.http=http;this.base=base;}
    public boolean ready(){return config.ready();}
    public void requireReady(){if(!ready())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Chưa cấu hình Cloudinary. Bạn vẫn có thể đăng bài bằng văn bản.");}
    public Map<String,Object> ticket(long id,String publicId,String type,long timestamp){
        requireReady();
        Map<String,String> params=new TreeMap<>();params.put("public_id",publicId);params.put("timestamp",Long.toString(timestamp));params.put("overwrite","false");
        params.put("allowed_formats",type.equals("image")?"jpg,jpeg,png,webp":"mp4,webm,mov");
        return Map.of("id",id,"uploadUrl","https://api.cloudinary.com/v1_1/"+config.cloudName()+"/"+type+"/upload","apiKey",config.apiKey(),"params",params,"signature",signature(params));
    }
    String signature(Map<String,String> params){
        String body=new TreeMap<>(params).entrySet().stream().map(e->e.getKey()+"="+e.getValue()).collect(Collectors.joining("&"));
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((body+config.apiSecret()).getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    public Map<String,Object> inspect(String publicId,String type){
        requireReady();
        try{
            String path="/v1_1/"+config.cloudName()+"/resources/"+type+"/upload/"+URLEncoder.encode(publicId,StandardCharsets.UTF_8);
            String basic=Base64.getEncoder().encodeToString((config.apiKey()+":"+config.apiSecret()).getBytes(StandardCharsets.UTF_8));
            var request=HttpRequest.newBuilder(URI.create(base+path)).header("Authorization","Basic "+basic).timeout(Duration.ofSeconds(10)).GET().build();
            var response=http.send(request,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=200)throw unavailable();
            var asset=json.readValue(response.body(),new TypeReference<Map<String,Object>>(){});
            validate(asset,publicId,type);return asset;
        }catch(java.io.IOException|tools.jackson.core.JacksonException|IllegalArgumentException e){throw unavailable();}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw unavailable();}
    }
    void validate(Map<String,Object> asset,String publicId,String type){
        long max=type.equals("image")?10*1024*1024L:50*1024*1024L;
        double seconds=asset.get("duration") instanceof Number n?n.doubleValue():0;
        var formats=type.equals("image")?List.of("jpg","jpeg","png","webp"):List.of("mp4","webm","mov");
        URI url;
        try{url=URI.create(Objects.toString(asset.get("secure_url"),""));}catch(IllegalArgumentException e){throw unavailable();}
        if(!publicId.equals(asset.get("public_id")) || !type.equals(asset.get("resource_type")) || !"upload".equals(asset.get("type"))
            || !(asset.get("bytes") instanceof Number bytes) || bytes.longValue()<=0 || bytes.longValue()>max
            || !(asset.get("width") instanceof Number width) || width.intValue()<=0 || !(asset.get("height") instanceof Number height) || height.intValue()<=0
            || !formats.contains(asset.get("format")) || !Double.isFinite(seconds) || seconds<0 || seconds>120 || type.equals("video") && seconds==0
            || !"https".equals(url.getScheme()) || !"res.cloudinary.com".equals(url.getHost()) || !url.getPath().startsWith("/"+config.cloudName()+"/"+type+"/upload/"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Tệp Cloudinary không hợp lệ. Ảnh tối đa 10 MB; video tối đa 50 MB và 2 phút.");
    }
    private ResponseStatusException unavailable(){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Chưa xác minh được tệp trên Cloudinary. Giữ bản nháp và thử lại.");}
}
