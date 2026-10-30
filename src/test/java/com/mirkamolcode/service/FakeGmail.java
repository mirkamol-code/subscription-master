package com.mirkamolcode.service;

import com.mirkamolcode.integration.gmail.GmailClient;
import com.mirkamolcode.integration.gmail.GmailHistoryExpiredException;
import com.mirkamolcode.integration.gmail.GmailHistoryPage;
import com.mirkamolcode.integration.gmail.GmailMessage;
import com.mirkamolcode.integration.gmail.GmailMessagePage;
import com.mirkamolcode.integration.gmail.GmailTransientException;
import com.mirkamolcode.integration.gmail.GmailUnauthorizedException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

final class FakeGmail implements GmailClient {
    private final ConcurrentHashMap<String, GmailMessage> messages = new ConcurrentHashMap<>();
    private volatile List<String> listedIds = List.of();
    private volatile List<String> historyAdded = List.of();
    final List<String> listQueries = new CopyOnWriteArrayList<>();
    final List<String> historyStartIds = new CopyOnWriteArrayList<>();
    final List<String> fetchedIds = new CopyOnWriteArrayList<>();
    final Set<String> failingIds = ConcurrentHashMap.newKeySet();
    final Set<String> failingHeaderIds = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, AtomicInteger> attemptsById = new ConcurrentHashMap<>();
    private final AtomicBoolean listingBlockPending = new AtomicBoolean();
    private volatile String blockedFetchId;
    private volatile CountDownLatch fetchBlocked = new CountDownLatch(0);
    private volatile CountDownLatch fetchReleased = new CountDownLatch(0);
    volatile boolean historyExpired;
    volatile String currentHistoryId;
    private volatile int revokeAfterFetches = -1;
    private final AtomicInteger fetches = new AtomicInteger();
    private volatile CountDownLatch listingStarted = new CountDownLatch(0);
    private volatile CountDownLatch listingReleased = new CountDownLatch(0);

    void reset() {
        messages.clear();
        listedIds = List.of();
        historyAdded = List.of();
        listQueries.clear();
        historyStartIds.clear();
        fetchedIds.clear();
        failingIds.clear();
        failingHeaderIds.clear();
        attemptsById.clear();
        listingBlockPending.set(false);
        blockedFetchId = null;
        fetchBlocked = new CountDownLatch(0);
        fetchReleased = new CountDownLatch(0);
        historyExpired = false;
        currentHistoryId = "100";
        revokeAfterFetches = -1;
        fetches.set(0);
        listingStarted = new CountDownLatch(0);
        listingReleased = new CountDownLatch(0);
    }

    void mailbox(GmailMessage... newestFirst) {
        List<String> ids = new ArrayList<>();
        for (GmailMessage message : newestFirst) {
            messages.put(message.id(), message);
            ids.add(message.id());
        }
        listedIds = List.copyOf(ids);
    }

    void deliver(GmailMessage message) {
        messages.put(message.id(), message);
    }

    void historyAdds(String... ids) {
        historyAdded = List.of(ids);
    }

    void blockListing() {
        listingStarted = new CountDownLatch(1);
        listingReleased = new CountDownLatch(1);
        listingBlockPending.set(true);
    }

    void blockFetchOf(String messageId) {
        blockedFetchId = messageId;
        fetchBlocked = new CountDownLatch(1);
        fetchReleased = new CountDownLatch(1);
    }

    void awaitBlockedFetch() throws InterruptedException {
        assertThat(fetchBlocked.await(5, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(200);
    }

    void releaseFetch() {
        fetchReleased.countDown();
    }

    int attempts(String messageId) {
        AtomicInteger count = attemptsById.get(messageId);
        return count == null ? 0 : count.get();
    }

    void awaitListingStarted() throws InterruptedException {
        assertThat(listingStarted.await(5, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(200);
    }

    void releaseListing() {
        listingReleased.countDown();
    }

    void revokeAccessAfterFetches(int count) {
        revokeAfterFetches = count;
    }

    void restoreAccess() {
        revokeAfterFetches = -1;
    }

    @Override
    public GmailMessagePage listMessageIds(String accessToken, String query, String pageToken) {
        listQueries.add(query);
        if (listingBlockPending.compareAndSet(true, false)) {
            listingStarted.countDown();
            awaitQuietly(listingReleased);
        }
        return new GmailMessagePage(listedIds, null);
    }

    @Override
    public GmailMessage getMessage(String accessToken, String messageId) {
        attemptsById.computeIfAbsent(messageId, id -> new AtomicInteger()).incrementAndGet();
        if (messageId.equals(blockedFetchId)) {
            blockedFetchId = null;
            fetchBlocked.countDown();
            awaitQuietly(fetchReleased);
        }
        if (revokeAfterFetches >= 0 && fetches.get() >= revokeAfterFetches)
            throw new GmailUnauthorizedException("Gmail API failed with 401 authError");
        if (failingIds.contains(messageId))
            throw new GmailTransientException("Gmail API failed with 503");
        fetches.incrementAndGet();
        fetchedIds.add(messageId);
        return messages.get(messageId);
    }

    @Override
    public GmailMessage getMessageHeaders(String accessToken, String messageId) {
        if (failingHeaderIds.contains(messageId))
            throw new GmailTransientException("Gmail API failed with 500");
        GmailMessage message = messages.get(messageId);
        return new GmailMessage(message.id(), message.threadId(), message.receivedAt(), message.from(), message.subject(), null, null);
    }

    @Override
    public GmailHistoryPage listHistory(String accessToken, String startHistoryId, String pageToken) {
        historyStartIds.add(startHistoryId);
        if (historyExpired)
            throw new GmailHistoryExpiredException(startHistoryId);
        return new GmailHistoryPage(historyAdded, null, currentHistoryId);
    }

    @Override
    public String fetchCurrentHistoryId(String accessToken) {
        return currentHistoryId;
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
