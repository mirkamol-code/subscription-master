package com.mirkamolcode.detection;

import com.mirkamolcode.integration.gmail.GmailMessage;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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
    private final Pattern cancellationConditional;
    private final Pattern priceChange;
    private final Pattern priceChangeEffective;
    private final Pattern paymentFailed;
    private final Pattern promotion;
    private final Pattern accessEnds;
    private final Pattern accessEndsNow;
    private final Pattern invoice;
    private final Pattern nonBilling;

    public EmailClassifier(DetectionProperties properties) {
        DetectionProperties.Keywords keywords = properties.keywords();
        this.receiptSubject = anyPhrase(keywords.receiptSubject());
        this.subscriptionSubject = anyPhrase(keywords.subscriptionSubject());
        this.paymentDue = anyPhrase(keywords.paymentDue());
        this.freePlan = anyPhrase(keywords.freePlan());
        this.recurring = anyPhrase(keywords.recurring());
        this.paymentConfirmation = anyPhrase(keywords.paymentConfirmation());
        this.cancellation = anyPhrase(keywords.cancellation());
        this.cancellationConditional = anyPhrase(keywords.cancellationConditional());
        this.priceChange = anyPhrase(keywords.priceChange());
        this.priceChangeEffective = anyPhrase(keywords.priceChangeEffective());
        this.paymentFailed = anyPhrase(keywords.paymentFailed());
        this.promotion = anyPhrase(keywords.promotion());
        this.accessEnds = anyPhrase(keywords.accessEnds());
        this.accessEndsNow = anyPhrase(keywords.accessEndsNow());
        this.invoice = anyPhrase(keywords.invoice());
        this.nonBilling = anyPhrase(keywords.nonBilling());
    }

    public EmailKind classify(GmailMessage message) {
        String subject = message.subject();
        String body = message.bodyText();
        if (isConversation(message) || promotion.matcher(subject).find())
            return EmailKind.IRRELEVANT;
        boolean confirmsPayment = paymentConfirmation.matcher(subject + " " + body).find();
        if (paymentFailed.matcher(subject).find() || (!confirmsPayment && paymentFailed.matcher(body).find()))
            return EmailKind.PAYMENT_FAILED;
        if (confirmsCancellation(message))
            return EmailKind.CANCELLATION;
        if (priceChange.matcher(subject).find() || (!confirmsPayment && priceChange.matcher(body).find()))
            return EmailKind.PRICE_CHANGE;
        if (paymentDue.matcher(subject).find() && !confirmsPayment)
            return EmailKind.IRRELEVANT;
        boolean isTransaction = receiptSubject.matcher(subject).find() || confirmsPayment;
        if (isMarketing(message, isTransaction))
            return EmailKind.IRRELEVANT;
        if (isTransaction || subscriptionSubject.matcher(subject).find())
            return EmailKind.RECEIPT;
        return EmailKind.IRRELEVANT;
    }

    private boolean confirmsCancellation(GmailMessage message) {
        if (cancellation.matcher(message.subject()).find())
            return true;
        String body = message.bodyText();
        return cancellation.matcher(body).find() && !cancellationConditional.matcher(body).find();
    }

    public Optional<PriceChangeNotice> findPriceChange(GmailMessage message) {
        return PriceChangeParser.parse(message.bodyText(), priceChangeEffective, message.receivedOnUtc());
    }

    public boolean mentionsFreePlan(GmailMessage message) {
        return matchesSubjectOrBody(freePlan, message);
    }

    public boolean isRecurringInvoiceCandidate(GmailMessage message) {
        return matchesSubjectOrBody(invoice, message) && !matchesSubjectOrBody(nonBilling, message);
    }

    public boolean mentionsRecurringBilling(GmailMessage message) {
        return matchesSubjectOrBody(recurring, message);
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
                || cancellation.matcher(subject).find() || paymentFailed.matcher(subject).find()
                || priceChange.matcher(subject).find());
    }

    private static boolean matchesSubjectOrBody(Pattern pattern, GmailMessage message) {
        return pattern.matcher(message.subject()).find() || pattern.matcher(message.bodyText()).find();
    }

    private static Pattern anyPhrase(List<String> phrases) {
        String alternatives = String.join("|", phrases.stream().map(Pattern::quote).toList());
        return Pattern.compile("(?iu)(?<![\\p{L}\\p{N}])(?:" + alternatives + ")(?![\\p{L}\\p{N}])");
    }
}
