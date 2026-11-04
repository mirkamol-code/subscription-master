package com.mirkamolcode.mapper;

import com.mirkamolcode.dto.response.LoginEventResponse;
import com.mirkamolcode.entity.LoginEvent;
import com.mirkamolcode.entity.User;
import com.mirkamolcode.model.Role;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AdminUserMapperTest {

    private final AdminUserMapper mapper = Mappers.getMapper(AdminUserMapper.class);

    @Test
    void toResponse_shouldMapLoginEventWithParsedClientInfo() {
        // given
        User user = new User("admin@example.com", Set.of(Role.ADMIN));
        Instant now = Instant.parse("2026-11-04T10:00:00Z");
        String ua = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
        LoginEvent event = new LoginEvent(user, now, "192.168.1.1", ua, "Tashkent", "Uzbekistan");

        // when
        LoginEventResponse response = mapper.toResponse(event);

        // then
        assertThat(response).isNotNull();
        assertThat(response.occurredAt()).isEqualTo(now);
        assertThat(response.ip()).isEqualTo("192.168.1.1");
        assertThat(response.userAgent()).isEqualTo(ua);
        assertThat(response.device()).isEqualTo("Desktop");
        assertThat(response.browser()).isEqualTo("Chrome");
        assertThat(response.os()).isEqualTo("macOS");
        assertThat(response.city()).isEqualTo("Tashkent");
        assertThat(response.country()).isEqualTo("Uzbekistan");
    }

    @Test
    void toResponse_shouldReturnNull_whenEventIsNull() {
        // when
        LoginEventResponse response = mapper.toResponse((LoginEvent) null);

        // then
        assertThat(response).isNull();
    }
}
