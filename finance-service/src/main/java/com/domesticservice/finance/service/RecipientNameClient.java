package com.domesticservice.finance.service;

import java.util.Map;

public interface RecipientNameClient {
    boolean ready();
    String provider();
    Map<String,Object> lookup(String bin,String account);
}
