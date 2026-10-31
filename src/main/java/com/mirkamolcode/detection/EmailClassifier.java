package com.mirkamolcode.detection;

import com.mirkamolcode.integration.gmail.GmailMessage;

import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class EmailClassifier {
    private static final Pattern REPLY_OR_FORWARD = Pattern.compile("(?iu)^\\s*(?:re|fwd?)\\s*:");
    private static final Pattern REPOSITORY_TAG = Pattern.compile("\\[\\p{Alpha}[\\w.-]*/[\\w.-]+\\]");

    private final Pattern receiptSubject;
    private final Pattern subscriptionSubject;
    private final Pattern paymentDue;
    private final Pattern freePlan;
    private final Pattern recurring;
    private final Pattern paymentConfirmation;
    private final Pattern cancellation;
    private final Pattern paymentFailed;
    private final Pattern promotion;
    private final Pattern accessEnds;
    private final Pattern accessEndsNow;

    public EmailClassifier(DetectionProperties properties) {
        DetectionProperties.Keywords keywords = properties.keywords();
        this.receiptSubject = anyPhrase(keywords.receiptSubject());
        this.subscriptionSubject = anyPhrase(keywords.subscriptionSubject());
        this.paymentDue = anyPhrase(keywords.paymentDue());
        this.freePlan = anyPhrase(keywords.freePlan());
        this.recurring = anyPhrase(keywords.recurring());
        this.paymentConfirmation = anyPhrase(keywords.paymentConfirmation());
        this.cancellation = anyPhrase(keywords.cancellation());
        this.paymentFailed = anyPhrase(keywords.paymentFailed());
        this.promotion = anyPhrase(keywords.promotion());
        this.accessEnds = anyPhrase(keywords.accessEnds());
        this.accessEndsNow = anyPhrase(keywords.accessEndsNow());
    }

    public EmailKind classify(GmailMessage message) {
        String subject = message.subject();
        String body = message.bodyText();
        if (isConversation(message) || promotion.matcher(subject).find())
            return EmailKind.IRRELEVANT;
        boolean confirmsPayment = paymentConfirmation.matcher(subject + " " + body).find();
        if (paymentFailed.matcher(subject).find() || (!confirmsPayment && paymentFailed.matcher(body).find()))
            return EmailKind.PAYMENT_FAILED;
        if (cancellation.matcher(subject).find() || cancellation.matcher(body).find())
            return EmailKind.CANCELLATION;
        if (paymentDue.matcher(subject).find() && !confirmsPayment)
            return EmailKind.IRRELEVANT;
        boolean isTransaction = receiptSubject.matcher(subject).find() || confirmsPayment;
        if (isMarketing(message, isTransaction))
            return EmailKind.IRRELEVANT;
        if (isTransaction || subscriptionSubject.matcher(subject).find())
            return EmailKind.RECEIPT;
        return EmailKind.IRRELEVANT;
    }

    public boolean mentionsFreePlan(GmailMessage message) {
        return freePlan.matcher(message.subject()).find() || freePlan.matcher(message.bodyText()).find();
    }

    public boolean mentionsRecurringBilling(GmailMessage message) {
        return recurring.matcher(message.subject()).find() || recurring.matcher(message.bodyText()).find();
    }

    private boolean isMarketing(GmailMessage message, boolean isTransaction) {
        return message.headers().hasUnsubscribeLink()
                && !isTransaction
                && promotion.matcher(message.subject() + " " + message.bodyText()).find();
    }

    public boolean isConversation(GmailMessage message) {
        String subject = message.subject();
        return message.headers().isMailingList()
                || REPLY_OR_FORWARD.matcher(subject).find()
                || REPOSITORY_TAG.matcher(subject).find();
    }

    public LocalDate findAccessEndDate(GmailMessage message) {
        if (accessEndsNow.matcher(message.bodyText()).find())
            return message.receivedOnUtc();
        return DateFinder.findDateAfter(message.bodyText(), accessEnds, message.receivedOnUtc());
    }

    public boolean hasBillingSubject(GmailMessage headers) {
        String subject = headers.subject();
        return !promotion.matcher(subject).find()
                && !paymentDue.matcher(subject).find()
                && (receiptSubject.matcher(subject).find() || subscriptionSubject.matcher(subject).find()
                || cancellation.matcher(subject).find() || paymentFailed.matcher(subject).find());
    }

    private static Pattern anyPhrase(List<String> phrases) {
        String alternatives = String.join("|", phrases.stream().map(Pattern::quote).toList());
        return Pattern.compile("(?iu)(?<![\\p{L}\\p{N}])(?:" + alternatives + ")(?![\\p{L}\\p{N}])");
    }
}
