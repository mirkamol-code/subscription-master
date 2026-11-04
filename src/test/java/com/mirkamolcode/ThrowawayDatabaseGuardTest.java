package com.mirkamolcode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ThrowawayDatabaseGuardTest {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "jdbc:postgresql://localhost:5222/subscription_master_db",
            "jdbc:postgresql://db:5432/subscription_master_db",
            "jdbc:postgresql://prod-db.abc123.eu-central-1.rds.amazonaws.com:5432/subscription_master_db"
    })
    void requireThrowaway_shouldRefuseEveryDatabaseExceptTheTestContainer(String jdbcUrl) {
        // when / then
        assertThatThrownBy(() -> ThrowawayDatabaseGuard.requireThrowaway(jdbcUrl))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("throwaway");
    }

    @Test
    void requireThrowaway_shouldAllowTheTestContainer() {
        // given
        SharedPostgresContainer container = SharedPostgresContainer.running();

        // when / then
        assertThatCode(() -> ThrowawayDatabaseGuard.requireThrowaway(container.getJdbcUrl())).doesNotThrowAnyException();
    }

    @Test
    void startup_shouldStopBeforeConnecting_whenDatabaseIsNotTheThrowawayOne() {
        // when / then
        assertThatThrownBy(() -> new SpringApplicationBuilder(SubscribeMasterApplication.class)
                .profiles("test", "apple-dry-run")
                .run("--spring.datasource.url=jdbc:postgresql://guard-check.invalid:5432/subscription_master_db",
                        "--app.redetection.apple.exit-after-dry-run=false"))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause().hasMessageContaining("throwaway");
    }
}
