package com.miki1smad.ticketresale.notifications;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${ticketresale.mail.from:noreply@ticketresale.com}")
    private String fromAddress;

    public void sendEmail(String to, String subject, String text) {
        log.info("Preparing to send simple email to={}, subject='{}'", to, subject);
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
            log.info("Simple email successfully sent to={}", to);
        } catch (Exception e) {
            log.error("Failed to send simple email to={}, subject='{}'", to, subject, e);
            throw new IllegalStateException("Failed to send email to " + to, e);
        }
    }

    public void sendTicketEmailWithBarcode(
            String to,
            String subject,
            String htmlContent,
            String plainTextFallback,
            String barcodeFilename,
            byte[] barcodeImage,
            byte[] qrCodeImage) {
        log.info("Preparing to send ticket barcode email to={}, subject='{}'", to, subject);
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            if (mimeMessage == null) {
                mimeMessage = new JavaMailSenderImpl().createMimeMessage();
            }

            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(plainTextFallback, htmlContent);

            if (barcodeImage != null && barcodeImage.length > 0) {
                ByteArrayResource barcodeResource = new ByteArrayResource(barcodeImage);
                helper.addInline("barcodeImage", barcodeResource, "image/png");
                helper.addAttachment("barcode-" + barcodeFilename + ".png", barcodeResource, "image/png");
            }

            if (qrCodeImage != null && qrCodeImage.length > 0) {
                ByteArrayResource qrResource = new ByteArrayResource(qrCodeImage);
                helper.addInline("qrCodeImage", qrResource, "image/png");
                helper.addAttachment("qrcode-" + barcodeFilename + ".png", qrResource, "image/png");
            }

            mailSender.send(mimeMessage);
            log.info("Ticket barcode email successfully sent to={}", to);
        } catch (Exception e) {
            log.error("Failed to send ticket barcode email to={}, subject='{}'", to, subject, e);
            throw new IllegalStateException("Failed to send ticket barcode email to " + to, e);
        }
    }
}
