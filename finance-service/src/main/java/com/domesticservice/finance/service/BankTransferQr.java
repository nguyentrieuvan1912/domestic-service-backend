package com.domesticservice.finance.service;

import java.nio.charset.StandardCharsets;

/** EMVCo VietQR credit transfer payload. It only fills a bank transfer; it never authorizes payment. */
public final class BankTransferQr {
    private BankTransferQr() {}
    private static String field(String id,String value) {
        int size=value.getBytes(StandardCharsets.UTF_8).length;
        if(size>99) throw new IllegalArgumentException("QR field too long");
        return id+String.format("%02d",size)+value;
    }
    public static String create(String bin,String account,long amount,String content) {
        if(!bin.matches("[0-9]{6}") || !account.matches("[0-9]{5,30}") || amount<=0 || !content.matches("[A-Z0-9 ]{1,50}"))
            throw new IllegalArgumentException("Invalid bank transfer details");
        String merchant=field("00","A000000727")+field("01",field("00",bin)+field("01",account))+field("02","QRIBFTTA");
        String payload=field("00","01")+field("01","12")+field("38",merchant)+field("53","704")+field("54",Long.toString(amount))
            +field("58","VN")+field("62",field("08",content))+"6304";
        int crc=0xffff;
        for(byte b:payload.getBytes(StandardCharsets.UTF_8)) {
            crc^=(b&255)<<8;
            for(int bit=0;bit<8;bit++) crc=((crc&0x8000)!=0?(crc<<1)^0x1021:crc<<1)&0xffff;
        }
        return payload+String.format("%04X",crc);
    }
}
