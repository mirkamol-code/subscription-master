package com.mirkamolcode.service;

import com.mirkamolcode.entity.BillingEmail;
import com.mirkamolcode.model.BillingFrequency;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class RecurringInvoiceMatcher {
    private static final Map<BillingFrequency, DayRange> CYCLE_GAPS = Map.of(
            BillingFrequency.MONTHLY, new DayRange(25, 35),
            BillingFrequency.ANNUAL, new DayRange(350, 380));
    private static final int MIN_INVOICES = 2;

    public Optional<RecurringSeries> findSeries(BillingEmail latest, List<BillingEmail> candidates) {
        List<BillingEmail> invoices = distinctInvoices(candidates.stream()
                .filter(candidate -> isSameCustomer(candidate, latest))
                .filter(candidate -> candidate.getBilledOn() != null && candidate.getParsedAmount() != null)
                .sorted(Comparator.comparing(BillingEmail::getBilledOn))
                .toList());
        int position = invoices.indexOf(latest);
        if (position < 0)
            return Optional.empty();
        return CYCLE_GAPS.entrySet().stream()
                .map(cycle -> new RecurringSeries(chainAround(invoices, position, cycle.getValue()), cycle.getKey()))
                .filter(series -> series.invoices().size() >= MIN_INVOICES)
                .max(Comparator.comparingInt(series -> series.invoices().size()));
    }

    private static boolean isSameCustomer(BillingEmail candidate, BillingEmail latest) {
        return Objects.equals(candidate.getCustomerReference(), latest.getCustomerReference());
    }

    private static List<BillingEmail> distinctInvoices(List<BillingEmail> sorted) {
        Set<String> seenReferences = new HashSet<>();
        List<BillingEmail> distinct = new ArrayList<>();
        for (BillingEmail invoice : sorted)
            if (invoice.getInvoiceReference() == null || seenReferences.add(invoice.getInvoiceReference()))
                distinct.add(invoice);
        return distinct;
    }

    private static List<BillingEmail> chainAround(List<BillingEmail> invoices, int position, DayRange gap) {
        int first = position;
        while (first > 0 && gap.contains(invoices.get(first - 1), invoices.get(first)))
            first--;
        int last = position;
        while (last < invoices.size() - 1 && gap.contains(invoices.get(last), invoices.get(last + 1)))
            last++;
        return List.copyOf(invoices.subList(first, last + 1));
    }

    public record RecurringSeries(List<BillingEmail> invoices, BillingFrequency cycle) {
        public BillingEmail first() {
            return invoices.getFirst();
        }

        public BillingEmail latest() {
            return invoices.getLast();
        }
    }

    private record DayRange(long min, long max) {
        boolean contains(BillingEmail earlier, BillingEmail later) {
            long days = ChronoUnit.DAYS.between(earlier.getBilledOn(), later.getBilledOn());
            return days >= min && days <= max;
        }
    }
}
