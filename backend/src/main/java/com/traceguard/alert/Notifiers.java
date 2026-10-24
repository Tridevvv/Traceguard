package com.traceguard.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.List;

public final class Notifiers {

    private Notifiers() {}

    /** Always active: writes the alert to the application log. */
    @Component
    static class LogNotifier implements Notifier {
        private static final Logger log = LoggerFactory.getLogger(LogNotifier.class);

        @Override
        public void notify(Alert alert, List<String> recipients) {
            log.warn("ALERT {} [{}] tag={} -> {} recipient(s): {}",
                    alert.getCode(), alert.getSeverity(), alert.getTagId(), recipients.size(), alert.getMessage());
        }
    }

    /** Active only when spring.mail.host is set (see backend README). */
    @Component
    @ConditionalOnProperty("spring.mail.host")
    static class SmtpNotifier implements Notifier {
        private static final Logger log = LoggerFactory.getLogger(SmtpNotifier.class);

        private final JavaMailSender mail;
        private final String from;

        SmtpNotifier(JavaMailSender mail, @Value("${traceguard.mail.from:traceguard@localhost}") String from) {
            this.mail = mail;
            this.from = from;
        }

        @Override
        public void notify(Alert alert, List<String> recipients) {
            if (recipients.isEmpty()) return;
            try {
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setFrom(from);
                msg.setTo(recipients.toArray(String[]::new));
                msg.setSubject("[TraceGuard] " + alert.getSeverity() + " alert: " + alert.getCode() + " on " + alert.getTagId());
                msg.setText(alert.getMessage() + "\n\nOpen the Alerts center in TraceGuard to acknowledge or resolve it.");
                mail.send(msg);
            } catch (RuntimeException e) {
                // A mail outage must never break scanning or verification.
                log.error("Could not send alert email for {}: {}", alert.getCode(), e.getMessage());
            }
        }
    }
}
