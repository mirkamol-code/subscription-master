package com.mirkamolcode.service;

import com.mirkamolcode.entity.LoginCode;
import com.mirkamolcode.entity.User;
import com.mirkamolcode.exception.UnauthorizedException;
import com.mirkamolcode.repository.LoginCodeRepository;
import com.mirkamolcode.security.TokenHashes;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LoginCodeService {
    private static final Duration TIME_TO_LIVE = Duration.ofSeconds(60);
    private static final int CODE_BYTES = 32;

    private final LoginCodeRepository loginCodes;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public LoginCodeService(LoginCodeRepository loginCodes, Clock clock) {
        this.loginCodes = loginCodes;
        this.clock = clock;
    }

    public String issue(User user) {
        byte[] bytes = new byte[CODE_BYTES];
        random.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        loginCodes.save(new LoginCode(user, TokenHashes.sha256(code), clock.instant().plus(TIME_TO_LIVE)));
        return code;
    }

    public User consume(String code) {
        String codeHash = TokenHashes.sha256(code);
        if (loginCodes.markUsed(codeHash, clock.instant()) == 0)
            throw invalidCode();
        return loginCodes.findByCodeHash(codeHash)
                .map(LoginCode::getUser)
                .orElseThrow(LoginCodeService::invalidCode);
    }

    private static UnauthorizedException invalidCode() {
        return new UnauthorizedException("Invalid or expired login code");
    }
}
