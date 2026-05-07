package com.psh.exam.exam;

import com.psh.exam.exam.ExamDtos.CreateExamRequest;
import com.psh.exam.exam.ExamDtos.ExamDetailResponse;
import com.psh.exam.exam.ExamDtos.ExamSummaryResponse;
import com.psh.exam.exam.ExamDtos.QuestionRevealResponse;
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
import java.util.List;

@RestController
@RequestMapping("/api/exams")
public class ExamController {

    private final ExamService examService;

    public ExamController(ExamService examService) {
        this.examService = examService;
    }

    @GetMapping
    public List<ExamSummaryResponse> listPublishedExams() {
        return examService.listPublishedExams();
    }

    @GetMapping("/{examId}")
    public ExamDetailResponse getPublishedExam(@PathVariable Long examId) {
        return examService.getPublishedExam(examId);
    }

    @GetMapping("/{examId}/questions/{questionId}/correct-choice-ids")
    public QuestionRevealResponse getPublishedQuestionReveal(
            @PathVariable Long examId,
            @PathVariable Long questionId
    ) {
        return examService.getPublishedQuestionReveal(examId, questionId);
    }

    @PostMapping
    public ResponseEntity<ExamDetailResponse> createExam(
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestBody CreateExamRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(examService.createExam(principal.accountId(), request));
    }
}
