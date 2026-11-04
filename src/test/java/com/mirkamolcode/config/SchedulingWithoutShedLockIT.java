package com.mirkamolcode.config;

import com.mirkamolcode.AbstractTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulingWithoutShedLockIT extends AbstractTestConfig {
    @Autowired
    private ApplicationContext context;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void app_shouldStartWithoutSchedulerLockBeanOrTable() {
        // when
        boolean hasLockProvider = context.containsBean("lockProvider");
        Integer lockTables = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = current_schema() and table_name = 'shedlock'",
                Integer.class);

        // then
        assertThat(hasLockProvider).isFalse();
        assertThat(lockTables).isZero();
    }
}
