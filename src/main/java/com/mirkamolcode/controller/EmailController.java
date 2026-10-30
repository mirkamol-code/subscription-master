package com.mirkamolcode.controller;

import com.mirkamolcode.dto.response.EmailAttachmentFile;
import com.mirkamolcode.dto.response.RenderedEmail;
import com.mirkamolcode.service.EmailService;

import java.nio.charset.StandardCharsets;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/emails")
public class EmailController {
    private final EmailService service;

    public EmailController(EmailService service) {
        this.service = service;
    }

    @GetMapping("/{id}/attachments/{attachmentId}")
    @PreAuthorize("hasAuthority('EMAIL_READ_OWN')")
    public ResponseEntity<byte[]> attachment(@PathVariable Long id, @PathVariable Long attachmentId,
                                             @RequestParam(defaultValue = "false") boolean download) {
        EmailAttachmentFile file = service.fetchAttachment(id, attachmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDispositionHeader.build(download, file.filename()))
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(file.bytes());
    }

    @GetMapping(value = "/{id}/content", produces = MediaType.TEXT_HTML_VALUE)
    @PreAuthorize("hasAuthority('EMAIL_READ_OWN')")
    public ResponseEntity<String> content(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean images) {
        RenderedEmail email = service.renderContent(id, images);
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .header("Content-Security-Policy", email.contentSecurityPolicy())
                .header("X-Blocked-Remote-Images", String.valueOf(email.blockedRemoteImages()))
                .header("Referrer-Policy", "no-referrer")
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(email.html());
    }
}
