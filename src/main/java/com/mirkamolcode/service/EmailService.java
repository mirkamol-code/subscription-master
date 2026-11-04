package com.mirkamolcode.service;

import com.mirkamolcode.detection.EmailKind;
import com.mirkamolcode.dto.response.EmailAttachmentFile;
import com.mirkamolcode.dto.response.RenderedEmail;
import com.mirkamolcode.entity.BillingEmail;
import com.mirkamolcode.entity.EmailAttachment;
import com.mirkamolcode.exception.AttachmentTooLargeException;
import com.mirkamolcode.exception.NotFoundException;
import com.mirkamolcode.integration.gmail.GmailClient;
import com.mirkamolcode.integration.gmail.GmailMessage;
import com.mirkamolcode.integration.gmail.GmailNotFoundException;
import com.mirkamolcode.repository.BillingEmailRepository;
import com.mirkamolcode.security.EmailHtmlSanitizer;
import com.mirkamolcode.security.SanitizedEmail;

import com.mirkamolcode.dto.request.AccountAssignmentRequest;
import com.mirkamolcode.dto.response.AwaitingAccountResponse;
import com.mirkamolcode.dto.response.SubscriptionResponse;
import com.mirkamolcode.entity.Subscription;
import com.mirkamolcode.exception.ConflictException;
import com.mirkamolcode.mapper.SubscriptionMapper;
import com.mirkamolcode.repository.SubscriptionRepository;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import org.jsoup.nodes.Entities;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EmailService {
    private static final String PDF = "application/pdf";
    private static final String DOCUMENT_POLICY = "default-src 'none'; img-src %s; style-src 'unsafe-inline'";
    private static final String STRICT_IMAGES = "data:";
    private static final String HTTPS_IMAGES = "data: https:";

    private final BillingEmailRepository billingEmails;
    private final CurrentUserService currentUser;
    private final GmailClient gmail;
    private final GmailAccessor gmailAccessor;
    private final EmailHtmlSanitizer sanitizer;
    private final long maxAttachmentBytes;
    private final SubscriptionRepository subscriptions;
    private final BillingEmailRecorder recorder;
    private final SubscriptionMapper subscriptionMapper;

    public EmailService(BillingEmailRepository billingEmails,
                        CurrentUserService currentUser,
                        GmailClient gmail,
                        GmailAccessor gmailAccessor,
                        EmailHtmlSanitizer sanitizer,
                        @Value("${app.gmail.attachments.max-bytes}") long maxAttachmentBytes,
                        SubscriptionRepository subscriptions,
                        BillingEmailRecorder recorder,
                        SubscriptionMapper subscriptionMapper) {
        this.billingEmails = billingEmails;
        this.currentUser = currentUser;
        this.gmail = gmail;
        this.gmailAccessor = gmailAccessor;
        this.sanitizer = sanitizer;
        this.maxAttachmentBytes = maxAttachmentBytes;
        this.subscriptions = subscriptions;
        this.recorder = recorder;
        this.subscriptionMapper = subscriptionMapper;
    }

    public List<AwaitingAccountResponse> findAwaitingAccount() {
        Long userId = currentUser.requiredUser().getId();
        return billingEmails.findByUserIdAndAwaitingAccountTrueOrderByReceivedAtDesc(userId).stream()
                .map(email -> mapToAwaitingAccountResponse(email,
                        subscriptions.findAllByUserIdAndMerchantKeyAndIsDeletedFalse(userId, email.getMerchantKey())))
                .toList();
    }

    @Transactional
    public SubscriptionResponse assignAccount(Long emailId, AccountAssignmentRequest request) {
        Long userId = currentUser.requiredUser().getId();
        BillingEmail email = findOwnBillingEmail(emailId, userId);
        if (!email.isAwaitingAccount())
            throw new ConflictException("Only an email waiting for an account can be assigned");
        Subscription subscription = subscriptions.findById(request.subscriptionId())
                .filter(candidate -> candidate.getUser().getId().equals(userId) && !candidate.isDeleted())
                .orElseThrow(() -> new NotFoundException("Subscription not found: " + request.subscriptionId()));
        if (!Objects.equals(subscription.getMerchantKey(), email.getMerchantKey()))
            throw new ConflictException("The email belongs to a different merchant");
        recorder.assignAccount(email, subscription);
        return subscriptionMapper.toResponse(subscription);
    }

    private static AwaitingAccountResponse mapToAwaitingAccountResponse(BillingEmail email, List<Subscription> accounts) {
        return new AwaitingAccountResponse(email.getId(), email.getKind(), email.getSender(), email.getSubject(), email.getReceivedAt(),
                email.getParsedAmount(), email.getParsedCurrency(), accounts.stream()
                .map(account -> new AwaitingAccountResponse.AccountOption(account.getId(), account.getName(), account.getCustomerReference()))
                .toList());
    }

    public EmailAttachmentFile fetchAttachment(Long emailId, Long attachmentId) {
        Long userId = currentUser.requiredUser().getId();
        BillingEmail email = findOwnBillingEmail(emailId, userId);
        EmailAttachment attachment = email.getAttachments().stream()
                .filter(candidate -> candidate.getId().equals(attachmentId))
                .filter(candidate -> PDF.equalsIgnoreCase(candidate.getMimeType()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Attachment not found"));
        if (attachment.getSizeBytes() > maxAttachmentBytes)
            throw new AttachmentTooLargeException(attachment.getSizeBytes(), maxAttachmentBytes);
        byte[] bytes = fetchFromGmail(() -> gmailAccessor.callWithFreshToken(userId, accessToken ->
                gmail.getAttachment(accessToken, email.getGmailMessageId(), attachment.getGmailAttachmentId())));
        return new EmailAttachmentFile(attachment.getFilename(), attachment.getMimeType(), bytes);
    }

    public RenderedEmail renderContent(Long emailId, boolean allowRemoteImages) {
        Long userId = currentUser.requiredUser().getId();
        BillingEmail email = findOwnBillingEmail(emailId, userId);
        GmailMessage message = fetchFromGmail(() -> gmailAccessor.callWithFreshToken(userId, accessToken ->
                gmail.getMessage(accessToken, email.getGmailMessageId())));
        SanitizedEmail sanitized = message.htmlBody() != null
                ? sanitizer.sanitize(message.htmlBody(), allowRemoteImages)
                : new SanitizedEmail(renderPlainText(message.bodyText()), 0);
        String documentPolicy = DOCUMENT_POLICY.formatted(allowRemoteImages ? HTTPS_IMAGES : STRICT_IMAGES);
        return new RenderedEmail(buildDocument(sanitized.html(), documentPolicy), documentPolicy + "; sandbox",
                sanitized.blockedRemoteImages());
    }

    private BillingEmail findOwnBillingEmail(Long emailId, Long userId) {
        return billingEmails.findByIdAndUserId(emailId, userId)
                .filter(email -> email.getKind() != EmailKind.IRRELEVANT)
                .orElseThrow(() -> new NotFoundException("Email not found"));
    }

    private static <T> T fetchFromGmail(Supplier<T> gmailCall) {
        try {
            return gmailCall.get();
        } catch (GmailNotFoundException e) {
            throw new NotFoundException("This email is no longer available in Gmail");
        }
    }

    private static String renderPlainText(String text) {
        return "<pre style=\"white-space:pre-wrap;font-family:inherit\">" + Entities.escape(text) + "</pre>";
    }

    private static String buildDocument(String bodyHtml, String documentPolicy) {
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\">"
                + "<meta http-equiv=\"Content-Security-Policy\" content=\"" + documentPolicy + "\">"
                + "<meta name=\"referrer\" content=\"no-referrer\">"
                + "</head><body>" + bodyHtml + "</body></html>";
    }
}
