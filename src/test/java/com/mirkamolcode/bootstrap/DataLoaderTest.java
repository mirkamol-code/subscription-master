package com.mirkamolcode.bootstrap;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DataLoaderTest {

    @Test
    @SuppressWarnings("unchecked")
    void seedSubscriptions_shouldContainExactly200ValidEntries() throws Exception {
        Field field = DataLoader.class.getDeclaredField("SEED_SUBSCRIPTIONS");
        field.setAccessible(true);
        List<?> seeds = (List<?>) field.get(null);

        assertThat(seeds).hasSize(200);
    }
}
