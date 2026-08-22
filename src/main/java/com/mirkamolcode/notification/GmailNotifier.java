package com.mirkamolcode.notification;

import com.mirkamolcode.service.NotificationService;
import com.mirkamolcode.entity.Subscription;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
        name = "app.notifications.gmail.enabled",
        havingValue = "true"
)
public class GmailNotifier implements NotificationService {

    private final JavaMailSender mailSender;
    private final String from;

    public GmailNotifier(JavaMailSender mailSender,
                         @Value("${app.notifications.gmail-from}")   String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void paymentDueSoon(Subscription subscription) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(subscription.getUser().getEmail());
        message.setSubject("Subscription payment due soon: " + subscription.getName());
        message.setText("Your " + subscription.getName() + " subscription payment of "
                + subscription.getPrice() + " " + subscription.getCurrency()
                + " is due on " + subscription.getNextPaymentDate() + ".");
        mailSender.send(message);
    }
}
