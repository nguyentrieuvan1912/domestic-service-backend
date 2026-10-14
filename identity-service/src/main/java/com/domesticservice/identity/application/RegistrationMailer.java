package com.domesticservice.identity.application;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import static com.domesticservice.identity.application.AccountService.error;

@Component
public class RegistrationMailer {
    private final ObjectProvider<JavaMailSender> senders;
    private final String from;
    public RegistrationMailer(ObjectProvider<JavaMailSender> senders, @Value("${identity.mail.from:}") String from) {
        this.senders=senders; this.from=from;
    }
    public void send(String email, String code) {
        JavaMailSender sender=senders.getIfAvailable();
        if (sender==null || from.isBlank()) throw error(HttpStatus.SERVICE_UNAVAILABLE,"Dịch vụ gửi email chưa được cấu hình. Vui lòng liên hệ quản trị viên.");
        var message=new SimpleMailMessage();
        message.setFrom(from); message.setTo(email); message.setSubject("CleanMaster - Mã xác thực đăng ký");
        message.setText("Mã xác thực của bạn: "+code+"\nMã có hiệu lực trong 10 phút. Không chia sẻ mã với người khác.\nNếu bạn không yêu cầu đăng ký, hãy bỏ qua email này.");
        try { sender.send(message); }
        catch (MailException ex) { throw error(HttpStatus.SERVICE_UNAVAILABLE,"Chưa gửi được mã xác thực. Vui lòng thử lại sau."); }
    }
}
