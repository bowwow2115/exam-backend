package com.psh.exam.account;

import com.psh.exam.account.AccountDtos.AccountResponse;
import com.psh.exam.account.AccountDtos.SignUpRequest;
import com.psh.exam.security.AccountPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/signup")
    public ResponseEntity<AccountResponse> signUp(@RequestBody SignUpRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.signUp(request));
    }

    @PostMapping("/login")
    public AccountResponse login(@AuthenticationPrincipal AccountPrincipal principal) {
        return AccountResponse.from(principal.account());
    }

    @GetMapping("/me")
    public AccountResponse me(@AuthenticationPrincipal AccountPrincipal principal) {
        return AccountResponse.from(principal.account());
    }
}
