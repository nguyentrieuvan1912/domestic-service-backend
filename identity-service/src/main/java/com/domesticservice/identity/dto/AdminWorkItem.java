package com.domesticservice.identity.dto;

public record AdminWorkItem(String key, String state, String section, String target, String title, String summary) {
    public String eventKey(){return key+":"+state;}
}
