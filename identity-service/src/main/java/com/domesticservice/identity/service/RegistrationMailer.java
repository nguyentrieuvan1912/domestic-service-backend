package com.domesticservice.identity.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import static com.domesticservice.identity.service.AccountService.error;

@Component
public class RegistrationMailer {

    private final ObjectProvider<JavaMailSender> senders;

    private final String from;

    public RegistrationMailer(ObjectProvider<JavaMailSender> senders, @Value("${identity.mail.from:}") String from) {
        this.senders = senders;
        this.from = from;
    }

    public void send(String email, String code) {
        sendMessage(email, "CleanMaster - Mã xác thực đăng ký",
                "Mã xác thực của bạn: " + code + "\nMã có hiệu lực trong 10 phút. Không chia sẻ mã với người khác.\nNếu bạn không yêu cầu đăng ký, hãy bỏ qua email này.",
                "Chưa gửi được mã xác thực. Vui lòng thử lại sau.");
    }

    public void sendStaffCredentials(String email, String initialPassword) {
        sendMessage(email, "CleanMaster - Tài khoản nhân viên",
                "Tài khoản nhân viên CleanMaster của bạn đã được tạo.\n\n"
                        + "Email đăng nhập: " + email + "\n"
                        + "Mật khẩu khởi tạo: " + initialPassword + "\n\n"
                        + "Mở ứng dụng CleanMaster, chọn Nhân viên và đăng nhập. "
                        + "Sau khi đăng nhập, vào Tài khoản → Đổi mật khẩu để đặt mật khẩu của riêng bạn.",
                "Chưa gửi được email cấp tài khoản. Vui lòng thử lại sau.");
    }

    private void sendMessage(String email, String subject, String body, String failureMessage) {
        JavaMailSender sender = senders.getIfAvailable();
        if (sender == null || from.isBlank())
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "Dịch vụ gửi email chưa được cấu hình. Vui lòng liên hệ quản trị viên.");
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject(subject);
        message.setText(body);
        try {
            sender.send(message);
        } catch (MailException ex) {
            throw error(HttpStatus.SERVICE_UNAVAILABLE, failureMessage);
        }
    }
}
