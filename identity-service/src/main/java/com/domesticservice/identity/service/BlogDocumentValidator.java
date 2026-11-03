package com.domesticservice.identity.service;

import java.net.URI;
import java.util.*;
import org.springframework.http.HttpStatus;
import static com.domesticservice.identity.service.AccountService.error;

/** Structured rich text only: no arbitrary HTML, script, CSS or iframe payloads. */
public final class BlogDocumentValidator {
    private BlogDocumentValidator() {}
    private static final Set<String> NODES=Set.of("doc","paragraph","heading","text","bulletList","orderedList","listItem","blockquote","hardBreak","horizontalRule","codeBlock","image","video","youtube");
    private static final Set<String> MARKS=Set.of("bold","italic","underline","strike","code","textStyle","link");
    private static final Set<String> FONTS=Set.of("Arial","Georgia","Times New Roman","Verdana","Tahoma","Plus Jakarta Sans");
    public static void validate(Object document) {
        if(!(document instanceof Map<?,?> map) || !"doc".equals(map.get("type"))) throw bad();
        walk(map,0,new int[]{0});
    }
    private static void walk(Map<?,?> node,int depth,int[] count) {
        if(depth>24 || ++count[0]>5000 || !NODES.contains(Objects.toString(node.get("type"),""))) throw bad();
        String type=node.get("type").toString();
        if(node.containsKey("text") && (!(node.get("text") instanceof String s) || s.length()>100000)) throw bad();
        if(node.get("attrs") instanceof Map<?,?> attrs) attrs(attrs);
        if(Set.of("image","video","youtube").contains(type)) {
            if(!(node.get("attrs") instanceof Map<?,?> attrs)) throw bad();
            String src=Objects.toString(attrs.get("src"),""); url(src,false);
            if(type.equals("youtube") && !youtube(src)) throw bad();
        }
        if(node.get("marks")!=null) {
            if(!(node.get("marks") instanceof List<?> marks) || marks.size()>12) throw bad();
            for(var value:marks) {
                if(!(value instanceof Map<?,?> mark) || !MARKS.contains(Objects.toString(mark.get("type"),""))) throw bad();
                if(mark.get("attrs") instanceof Map<?,?> attrs) attrs(attrs);
                if("link".equals(mark.get("type"))) {
                    if(!(mark.get("attrs") instanceof Map<?,?> attrs)) throw bad();
                    url(Objects.toString(attrs.get("href"),""),true);
                }
            }
        }
        if(node.get("content")!=null) {
            if(!(node.get("content") instanceof List<?> children)) throw bad();
            for(var child:children) { if(!(child instanceof Map<?,?> map)) throw bad(); walk(map,depth+1,count); }
        }
    }
    private static void attrs(Map<?,?> attrs) {
        for(var entry:attrs.entrySet()) {
            String key=entry.getKey().toString();Object value=entry.getValue();if(value==null) continue;
            String text=value.toString();
            boolean valid=switch(key) {
                case "textAlign" -> Set.of("left","center","right","justify").contains(text);
                case "fontFamily" -> FONTS.contains(text);
                case "fontSize" -> text.matches("(12|14|16|18|20|24|28|32|36|48)px");
                case "color","backgroundColor" -> text.matches("#[0-9a-fA-F]{6}");
                case "lineHeight" -> Set.of("1","1.2","1.5","1.8","2").contains(text);
                case "level" -> value instanceof Number n && n.intValue()>=1 && n.intValue()<=3;
                case "start" -> value instanceof Number n && n.intValue()>=1 && n.intValue()<=10000;
                case "width","height" -> value instanceof Number n && n.intValue()>0 && n.intValue()<=4096;
                case "src","href" -> text.length()<=2000;
                case "alt","title","language" -> text.length()<=500;
                case "target" -> Set.of("_blank","_self").contains(text);
                case "rel" -> text.length()<=100;
                case "class" -> text.isEmpty();
                case "controls","autoplay","loop","muted" -> value instanceof Boolean;
                case "startAt" -> value instanceof Number n && n.intValue()>=0;
                default -> false;
            };
            if(!valid) throw bad();
        }
    }
    public static void url(String value,boolean internal) {
        if(internal && value.startsWith("/") && !value.startsWith("//") && !value.contains("\\")) return;
        try {var uri=URI.create(value);if(!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null) throw bad();}
        catch(IllegalArgumentException ex) {throw bad();}
    }
    public static boolean youtube(String value) {
        try {String host=URI.create(value).getHost();return Set.of("youtube.com","www.youtube.com","youtu.be","www.youtube-nocookie.com").contains(host);}
        catch(RuntimeException ex){return false;}
    }
    private static org.springframework.web.server.ResponseStatusException bad() {return error(HttpStatus.BAD_REQUEST,"Nội dung bài viết có định dạng hoặc liên kết không hợp lệ.");}
}
