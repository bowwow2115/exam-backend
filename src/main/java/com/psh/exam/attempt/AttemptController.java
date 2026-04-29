package com.psh.exam.attempt;

import com.psh.exam.attempt.AttemptDtos.AttemptResultResponse;
import com.psh.exam.attempt.AttemptDtos.SubmitAttemptRequest;
import com.psh.exam.security.AccountPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class AttemptController {

    private final AttemptService attemptService;

    public AttemptController(AttemptService attemptService) {
        this.attemptService = attemptService;
    }

    @PostMapping("/api/exams/{examId}/attempts")
    public ResponseEntity<AttemptResultResponse> submitAttempt(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable Long examId,
            @RequestBody SubmitAttemptRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(attemptService.submitAttempt(principal.accountId(), examId, request));
    }

    @GetMapping("/api/attempts/{attemptId}")
    public AttemptResultResponse getAttemptResult(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable Long attemptId
    ) {
        return attemptService.getAttemptResult(principal.accountId(), attemptId);
    }
}
