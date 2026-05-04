package com.psh.exam.account;

import java.time.LocalDateTime;

public final class AccountDtos {

    private AccountDtos() {
    }

    public record SignUpRequest(
            String email,
            String password,
            String displayName
    ) {
    }

    public record LoginRequest(
            String email,
            String password
    ) {
    }

    public record LoginResponse(
            String accessToken,
            String tokenType,
            AccountResponse account
    ) {
    }

    public record AccountResponse(
            Long id,
            String email,
            String displayName,
            AccountRole role,
            LocalDateTime createdAt
    ) {
        public static AccountResponse from(Account account) {
            return new AccountResponse(
                    account.getId(),
                    account.getEmail(),
                    account.getDisplayName(),
                    account.getRole(),
                    account.getCreatedAt()
            );
        }
    }
}
