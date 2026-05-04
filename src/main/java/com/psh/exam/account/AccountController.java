package com.psh.exam.account;

import com.psh.exam.account.AccountDtos.AccountResponse;
import com.psh.exam.account.AccountDtos.LoginRequest;
import com.psh.exam.account.AccountDtos.LoginResponse;
import com.psh.exam.account.AccountDtos.SignUpRequest;
import com.psh.exam.security.AccountPrincipal;
import com.psh.exam.security.JwtService;
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
    private final JwtService jwtService;

    public AccountController(AccountService accountService, JwtService jwtService) {
        this.accountService = accountService;
        this.jwtService = jwtService;
    }

    @PostMapping("/signup")
    public ResponseEntity<AccountResponse> signUp(@RequestBody SignUpRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.signUp(request));
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        Account account = accountService.authenticate(request);
        AccountResponse accountResponse = AccountResponse.from(account);
        return new LoginResponse(jwtService.createToken(account), "Bearer", accountResponse);
    }

    @GetMapping("/me")
    public AccountResponse me(@AuthenticationPrincipal AccountPrincipal principal) {
        return AccountResponse.from(principal.account());
    }
}
