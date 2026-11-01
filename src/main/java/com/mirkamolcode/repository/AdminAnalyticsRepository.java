package com.mirkamolcode.repository;

import com.mirkamolcode.entity.Subscription;
import com.mirkamolcode.repository.projection.CategoryActivity;
import com.mirkamolcode.repository.projection.MerchantActivity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface AdminAnalyticsRepository extends Repository<Subscription, Long> {
    @Query(value = """
            with tracked as (
                select s.id, s.user_id, s.merchant_slug, s.name, s.status, s.created_at
                  from subscriptions s
                 where s.is_deleted = false and s.is_hidden = false and s.merchant_slug <> ''
                   and (cast(:category as varchar) is null or s.category = cast(:category as varchar))
            ),
            per_merchant as (
                select t.merchant_slug,
                       mode() within group (order by t.name) as name,
                       count(distinct t.user_id) filter (where t.status = 'ACTIVE') as active_subscribers,
                       count(*) filter (where t.status = 'ACTIVE') as active_subscriptions,
                       count(*) filter (where t.created_at >= :since7) as new_last_7_days,
                       count(*) filter (where t.created_at >= :since30) as new_last_30_days
                  from tracked t
                 group by t.merchant_slug
            ),
            cancellations as (
                select t.merchant_slug, count(distinct sc.subscription_id) as cancelled
                  from status_changes sc
                  join tracked t on t.id = sc.subscription_id
                 where sc.to_status = 'CANCELLED' and sc.effective_at >= :rangeStart
                 group by t.merchant_slug
            ),
            spend as (
                select t.merchant_slug, sum(p.amount_uzs) as total, count(distinct t.user_id) as paying_users
                  from payment_history p
                  join tracked t on t.id = p.subscription_id
                 where p.payment_date >= :rangeFirstDay
                 group by t.merchant_slug
            ),
            active_users as (
                select distinct t.merchant_slug, t.user_id from tracked t where t.status = 'ACTIVE'
            ),
            logins as (
                select au.merchant_slug, count(le.id) as logins
                  from active_users au
                  join login_events le on le.user_id = au.user_id
                 where le.occurred_at >= :rangeStart
                 group by au.merchant_slug
            )
            select m.merchant_slug as slug,
                   m.name as name,
                   m.active_subscribers as activeSubscribers,
                   m.active_subscriptions as activeSubscriptions,
                   m.new_last_7_days as newLast7Days,
                   m.new_last_30_days as newLast30Days,
                   coalesce(c.cancelled, 0) as cancelledInRange,
                   coalesce(sp.total, 0) as trackedSpendUzs,
                   coalesce(sp.paying_users, 0) as payingUsers,
                   coalesce(l.logins, 0) as logins
              from per_merchant m
              left join cancellations c on c.merchant_slug = m.merchant_slug
              left join spend sp on sp.merchant_slug = m.merchant_slug
              left join logins l on l.merchant_slug = m.merchant_slug
             order by m.active_subscribers desc, m.active_subscriptions desc, m.name, m.merchant_slug
            """, nativeQuery = true)
    List<MerchantActivity> summariseByMerchant(@Param("category") String category,
                                               @Param("since7") Instant since7,
                                               @Param("since30") Instant since30,
                                               @Param("rangeStart") Instant rangeStart,
                                               @Param("rangeFirstDay") LocalDate rangeFirstDay);

    @Query(value = """
            with tracked as (
                select s.id, s.user_id, s.category, s.status
                  from subscriptions s
                 where s.is_deleted = false and s.is_hidden = false and s.merchant_slug <> ''
            ),
            per_category as (
                select t.category,
                       count(distinct t.user_id) filter (where t.status = 'ACTIVE') as active_subscribers,
                       count(*) filter (where t.status = 'ACTIVE') as active_subscriptions
                  from tracked t
                 group by t.category
            ),
            spend as (
                select t.category, sum(p.amount_uzs) as total
                  from payment_history p
                  join tracked t on t.id = p.subscription_id
                 where p.payment_date >= :rangeFirstDay
                 group by t.category
            )
            select c.category as category,
                   c.active_subscribers as activeSubscribers,
                   c.active_subscriptions as activeSubscriptions,
                   coalesce(sp.total, 0) as trackedSpendUzs
              from per_category c
              left join spend sp on sp.category = c.category
             order by c.active_subscribers desc, c.active_subscriptions desc, c.category
            """, nativeQuery = true)
    List<CategoryActivity> summariseByCategory(@Param("rangeFirstDay") LocalDate rangeFirstDay);
}
