package com.mirkamolcode.detection;

import com.mirkamolcode.integration.gmail.GmailMessage;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class EmailClassifier {
    private final Pattern receiptSubject;
    private final Pattern paymentConfirmation;
    private final Pattern cancellation;
    private final Pattern paymentFailed;
    private final Pattern promotion;

    public EmailClassifier(DetectionProperties properties) {
        DetectionProperties.Keywords keywords = properties.keywords();
        this.receiptSubject = anyPhrase(keywords.receiptSubject());
        this.paymentConfirmation = anyPhrase(keywords.paymentConfirmation());
        this.cancellation = anyPhrase(keywords.cancellation());
        this.paymentFailed = anyPhrase(keywords.paymentFailed());
        this.promotion = anyPhrase(keywords.promotion());
    }

    public EmailKind classify(GmailMessage message) {
        String subject = message.subject();
        String body = message.bodyText();
        if (promotion.matcher(subject).find())
            return EmailKind.IRRELEVANT;
        boolean confirmsPayment = paymentConfirmation.matcher(subject + " " + body).find();
        if (paymentFailed.matcher(subject).find() || (!confirmsPayment && paymentFailed.matcher(body).find()))
            return EmailKind.PAYMENT_FAILED;
        if (cancellation.matcher(subject).find() || cancellation.matcher(body).find())
            return EmailKind.CANCELLATION;
        if (receiptSubject.matcher(subject).find() || confirmsPayment)
            return EmailKind.RECEIPT;
        return EmailKind.IRRELEVANT;
    }

    public boolean hasBillingSubject(GmailMessage headers) {
        String subject = headers.subject();
        return !promotion.matcher(subject).find()
                && (receiptSubject.matcher(subject).find() || cancellation.matcher(subject).find() || paymentFailed.matcher(subject).find());
    }

    private static Pattern anyPhrase(List<String> phrases) {
        String alternatives = String.join("|", phrases.stream().map(Pattern::quote).toList());
        return Pattern.compile("(?iu)(?<![\\p{L}\\p{N}])(?:" + alternatives + ")(?![\\p{L}\\p{N}])");
    }
}
