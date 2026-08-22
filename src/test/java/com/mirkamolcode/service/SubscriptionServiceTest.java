package com.mirkamolcode.service;

import com.mirkamolcode.dto.request.SubscriptionRequest;
import com.mirkamolcode.dto.response.SubscriptionResponse;
import com.mirkamolcode.entity.Subscription;
import com.mirkamolcode.entity.User;
import com.mirkamolcode.exception.NotFoundException;
import com.mirkamolcode.mapper.SubscriptionMapper;
import com.mirkamolcode.model.BillingFrequency;
import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.model.Permission;
import com.mirkamolcode.model.Role;
import com.mirkamolcode.model.SubscriptionCategory;
import com.mirkamolcode.model.SubscriptionStatus;
import com.mirkamolcode.repository.SubscriptionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {
    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private SubscriptionMapper subscriptionMapper;
    @Mock
    private CurrentUserService currentUserService;
    @InjectMocks
    private SubscriptionService underTest;

    private User owner;
    private User other;
    private SubscriptionRequest request;

    @BeforeEach
    void setUp() {
        owner = user(1L, "owner@example.com");
        other = user(2L, "other@example.com");
        request = request("Netflix", "12.99");
    }

    @Test
    void create_shouldSaveSubscriptionForCurrentUser_andReturnMappedResponse() {
        // Given
        when(currentUserService.requiredUser()).thenReturn(owner);
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(subscriptionMapper.toResponse(any(Subscription.class))).thenAnswer(invocation -> response(invocation.getArgument(0)));

        // When
        SubscriptionResponse result = underTest.create(request);

        // Then
        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isSameAs(owner);
        assertThat(captor.getValue().getNextPaymentDate()).isEqualTo(request.startDate().plusMonths(1));
        assertThat(result.name()).isEqualTo("Netflix");
    }

    @Test
    void constructor_shouldCalculateNextPaymentDateForEveryFrequency() {
        LocalDate startDate = LocalDate.of(2026, 8, 22);

        assertThat(subscription(BillingFrequency.WEEKLY, startDate).getNextPaymentDate()).isEqualTo(LocalDate.of(2026, 8, 29));
        assertThat(subscription(BillingFrequency.MONTHLY, startDate).getNextPaymentDate()).isEqualTo(LocalDate.of(2026, 9, 22));
        assertThat(subscription(BillingFrequency.ANNUAL, startDate).getNextPaymentDate()).isEqualTo(LocalDate.of(2027, 8, 22));
    }

    @Test
    void list_shouldRejectInvalidPriceRange_withoutQueryingRepository() {
        assertThatThrownBy(() -> underTest.list(null, null, new BigDecimal("20"), new BigDecimal("10"), PageRequest.of(0, 20)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("minPrice must not exceed maxPrice");
        verifyNoInteractions(subscriptionRepository);
    }

    @Test
    void list_shouldReturnMappedOwnerSubscriptions_whenUserHasOwnPermission() {
        // Given
        Subscription subscription = subscription(owner, "Netflix");
        PageRequest pageable = PageRequest.of(0, 20);
        when(currentUserService.hasPermission(Permission.SUBSCRIPTION_READ_ALL)).thenReturn(false);
        when(currentUserService.requiredUser()).thenReturn(owner);
        when(subscriptionRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(java.util.List.of(subscription), pageable, 1));
        when(subscriptionMapper.toResponse(subscription)).thenReturn(response(subscription));

        // When
        var result = underTest.list(SubscriptionStatus.ACTIVE, CurrencyCode.USD, null, null, pageable);

        // Then
        assertThat(result.getContent()).extracting(SubscriptionResponse::name).containsExactly("Netflix");
        verify(currentUserService).requiredUser();
    }

    @Test
    void list_shouldNotResolveOwner_whenUserHasReadAllPermission() {
        // Given
        PageRequest pageable = PageRequest.of(0, 20);
        when(currentUserService.hasPermission(Permission.SUBSCRIPTION_READ_ALL)).thenReturn(true);
        when(subscriptionRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(java.util.List.of(), pageable, 0));

        // When
        var result = underTest.list(null, null, null, null, pageable);

        // Then
        assertThat(result).isEmpty();
        verify(currentUserService, never()).requiredUser();
    }

    @Test
    void get_shouldReturnSubscription_whenCurrentUserOwnsIt() {
        // Given
        Subscription subscription = subscription(owner, "Netflix");
        when(subscriptionRepository.findById(7L)).thenReturn(Optional.of(subscription));
        when(currentUserService.hasPermission(Permission.SUBSCRIPTION_READ_ALL)).thenReturn(false);
        when(currentUserService.requiredUser()).thenReturn(owner);
        when(subscriptionMapper.toResponse(subscription)).thenReturn(response(subscription));

        // When
        SubscriptionResponse result = underTest.get(7L);

        // Then
        assertThat(result.name()).isEqualTo("Netflix");
    }

    @Test
    void get_shouldThrowNotFound_whenSubscriptionDoesNotExistIsDeletedOrBelongsToAnotherUser() {
        when(subscriptionRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> underTest.get(99L)).isInstanceOf(NotFoundException.class);

        Subscription deleted = subscription(owner, "Deleted");
        deleted.markDeleted();
        when(subscriptionRepository.findById(8L)).thenReturn(Optional.of(deleted));
        assertThatThrownBy(() -> underTest.get(8L)).isInstanceOf(NotFoundException.class);

        Subscription anotherUsers = subscription(other, "Private");
        when(subscriptionRepository.findById(9L)).thenReturn(Optional.of(anotherUsers));
        when(currentUserService.hasPermission(Permission.SUBSCRIPTION_READ_ALL)).thenReturn(false);
        when(currentUserService.requiredUser()).thenReturn(owner);
        assertThatThrownBy(() -> underTest.get(9L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Subscription not found: 9");
    }

    @Test
    void get_shouldAllowAnotherUsersSubscription_whenUserHasReadAllPermission() {
        // Given
        Subscription subscription = subscription(other, "Private");
        when(subscriptionRepository.findById(9L)).thenReturn(Optional.of(subscription));
        when(currentUserService.hasPermission(Permission.SUBSCRIPTION_READ_ALL)).thenReturn(true);
        when(subscriptionMapper.toResponse(subscription)).thenReturn(response(subscription));

        // When
        SubscriptionResponse result = underTest.get(9L);

        // Then
        assertThat(result.name()).isEqualTo("Private");
        verify(currentUserService, never()).requiredUser();
    }

    @Test
    void update_shouldModifyOwnedSubscription_andReturnMappedResponse() {
        // Given
        Subscription subscription = subscription(owner, "Old name");
        when(subscriptionRepository.findById(7L)).thenReturn(Optional.of(subscription));
        when(currentUserService.hasPermission(Permission.SUBSCRIPTION_UPDATE_ALL)).thenReturn(false);
        when(currentUserService.requiredUser()).thenReturn(owner);
        when(subscriptionMapper.toResponse(subscription)).thenAnswer(invocation -> response(subscription));

        // When
        SubscriptionResponse result = underTest.update(7L, request("New name", "19.99"));

        // Then
        assertThat(subscription.getName()).isEqualTo("New name");
        assertThat(subscription.getPrice()).isEqualByComparingTo("19.99");
        assertThat(result.name()).isEqualTo("New name");
    }

    @Test
    void delete_shouldSoftDeleteOwnedSubscription() {
        // Given
        Subscription subscription = subscription(owner, "Netflix");
        when(subscriptionRepository.findById(7L)).thenReturn(Optional.of(subscription));
        when(currentUserService.hasPermission(Permission.SUBSCRIPTION_DELETE_ALL)).thenReturn(false);
        when(currentUserService.requiredUser()).thenReturn(owner);

        // When
        underTest.delete(7L);

        // Then
        assertThat(subscription.isDeleted()).isTrue();
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        verify(subscriptionRepository, never()).delete(any(Subscription.class));
    }

    private User user(Long id, String email) {
        User user = new User(email, "hash", Set.of(Role.USER));
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Subscription subscription(User user, String name) {
        Subscription subscription = new Subscription(user, name, new BigDecimal("12.99"), CurrencyCode.USD,
                BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE,
                SubscriptionCategory.ENTERTAINMENT, LocalDate.of(2026, 1, 10));
        ReflectionTestUtils.setField(subscription, "id", 7L);
        return subscription;
    }

    private Subscription subscription(BillingFrequency frequency, LocalDate startDate) {
        return new Subscription(owner, "Netflix", new BigDecimal("12.99"), CurrencyCode.USD,
                frequency, SubscriptionStatus.ACTIVE,
                SubscriptionCategory.ENTERTAINMENT, startDate);
    }

    private SubscriptionRequest request(String name, String price) {
        return new SubscriptionRequest(name, new BigDecimal(price), CurrencyCode.USD,
                BillingFrequency.MONTHLY, SubscriptionStatus.ACTIVE,
                SubscriptionCategory.ENTERTAINMENT, LocalDate.of(2026, 1, 10));
    }

    private SubscriptionResponse response(Subscription subscription) {
        return new SubscriptionResponse(subscription.getId(), subscription.getName(), subscription.getPrice(),
                subscription.getCurrency(), subscription.getFrequency(), subscription.getStatus(),
                subscription.getCategory(), subscription.getStartDate(), subscription.getNextPaymentDate(),
                subscription.getVersion());
    }
}
