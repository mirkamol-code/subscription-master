package com.mirkamolcode.repository;

import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.entity.ExchangeRate;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {
    Optional<ExchangeRate> findFirstByCurrencyOrderByRateDateDesc(CurrencyCode currency);

    Optional<ExchangeRate> findByCurrencyAndRateDate(CurrencyCode currency, LocalDate rateDate);
}
