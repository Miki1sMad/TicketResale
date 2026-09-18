package com.miki1smad.ticketresale.notifications;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${ticketresale.mail.from:noreply@ticketresale.com}")
    private String fromAddress;

    public void sendEmail(String to, String subject, String text) {
        log.info("Preparing to send email to={}, subject='{}'", to, subject);
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
            log.info("Email successfully sent to={}", to);
        } catch (Exception e) {
            log.error("Failed to send email to={}, subject='{}'", to, subject, e);
            throw new IllegalStateException("Failed to send email to " + to, e);
        }
    }
}
