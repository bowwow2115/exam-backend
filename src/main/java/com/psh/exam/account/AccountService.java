package com.psh.exam.account;

import com.psh.exam.account.AccountDtos.AccountResponse;
import com.psh.exam.account.AccountDtos.LoginRequest;
import com.psh.exam.account.AccountDtos.SignUpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class AccountService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    public AccountService(AccountRepository accountRepository, PasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AccountResponse signUp(SignUpRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "가입 요청 본문이 필요합니다.");
        }

        String email = normalizeEmail(request.email());
        String password = trimToNull(request.password());
        String displayName = trimToNull(request.displayName());

        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "유효한 이메일을 입력하세요.");
        }
        if (password == null || password.length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "비밀번호는 8자 이상이어야 합니다.");
        }
        if (displayName == null || displayName.length() > 80) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "표시 이름은 1자 이상 80자 이하여야 합니다.");
        }
        if (accountRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 가입된 이메일입니다.");
        }

        Account account = new Account(
                email,
                passwordEncoder.encode(password),
                displayName,
                AccountRole.USER
        );
        return AccountResponse.from(accountRepository.save(account));
    }

    @Transactional(readOnly = true)
    public Account getAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "계정을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public Account authenticate(LoginRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "로그인 요청 본문이 필요합니다.");
        }

        String email = normalizeEmail(request.email());
        String password = trimToNull(request.password());
        if (email == null || password == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        Account account = accountRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."));
        if (!passwordEncoder.matches(password, account.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다.");
        }
        return account;
    }

    private String normalizeEmail(String value) {
        // 로그인 비교 기준과 맞추기 위해 이메일은 저장 전에 소문자로 정규화합니다.
        String email = trimToNull(value);
        return email == null ? null : email.toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
