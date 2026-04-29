package com.psh.exam;

import com.psh.exam.account.AccountDtos.AccountResponse;
import com.psh.exam.account.AccountDtos.SignUpRequest;
import com.psh.exam.account.AccountService;
import com.psh.exam.attempt.AttemptDtos.AnswerRequest;
import com.psh.exam.attempt.AttemptDtos.AttemptResultResponse;
import com.psh.exam.attempt.AttemptDtos.SubmitAttemptRequest;
import com.psh.exam.attempt.AttemptService;
import com.psh.exam.exam.ExamDtos.ChoiceResponse;
import com.psh.exam.exam.ExamDtos.CreateChoiceRequest;
import com.psh.exam.exam.ExamDtos.CreateExamRequest;
import com.psh.exam.exam.ExamDtos.CreateQuestionRequest;
import com.psh.exam.exam.ExamDtos.ExamDetailResponse;
import com.psh.exam.exam.ExamService;
import com.psh.exam.wrongnote.WrongNoteDtos.WrongNoteResponse;
import com.psh.exam.wrongnote.WrongNoteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ExamFlowIntegrationTest {

    private final AccountService accountService;
    private final ExamService examService;
    private final AttemptService attemptService;
    private final WrongNoteService wrongNoteService;

    @Autowired
    ExamFlowIntegrationTest(
            AccountService accountService,
            ExamService examService,
            AttemptService attemptService,
            WrongNoteService wrongNoteService
    ) {
        this.accountService = accountService;
        this.examService = examService;
        this.attemptService = attemptService;
        this.wrongNoteService = wrongNoteService;
    }

    @Test
    void submitAttemptScoresMultipleAnswerQuestionAndCreatesWrongNote() {
        AccountResponse account = accountService.signUp(new SignUpRequest(
                "student@example.com",
                "password123",
                "student"
        ));
        ExamDetailResponse exam = examService.createExam(account.id(), new CreateExamRequest(
                "JPA 기본",
                "다중 정답 시험",
                30,
                true,
                List.of(
                        new CreateQuestionRequest(
                                "JPA 엔티티 식별자 전략으로 올바른 것은?",
                                2,
                                "IDENTITY와 SEQUENCE는 대표적인 식별자 생성 전략입니다.",
                                List.of(
                                        new CreateChoiceRequest("IDENTITY", true),
                                        new CreateChoiceRequest("SEQUENCE", true),
                                        new CreateChoiceRequest("COOKIE", false)
                                )
                        ),
                        new CreateQuestionRequest(
                                "PostgreSQL 기본 포트는?",
                                1,
                                "PostgreSQL 기본 포트는 5432입니다.",
                                List.of(
                                        new CreateChoiceRequest("3306", false),
                                        new CreateChoiceRequest("5432", true)
                                )
                        )
                )
        ));

        List<ChoiceResponse> firstQuestionChoices = exam.questions().get(0).choices();
        Long identityChoiceId = firstQuestionChoices.get(0).id();
        Long sequenceChoiceId = firstQuestionChoices.get(1).id();
        Long postgresQuestionId = exam.questions().get(1).id();
        Long wrongPostgresChoiceId = exam.questions().get(1).choices().get(0).id();

        AttemptResultResponse result = attemptService.submitAttempt(account.id(), exam.id(), new SubmitAttemptRequest(
                List.of(
                        new AnswerRequest(exam.questions().get(0).id(), Set.of(identityChoiceId, sequenceChoiceId)),
                        new AnswerRequest(postgresQuestionId, Set.of(wrongPostgresChoiceId))
                )
        ));

        assertThat(result.earnedScore()).isEqualTo(2);
        assertThat(result.totalScore()).isEqualTo(3);
        assertThat(result.answers()).hasSize(2);
        assertThat(result.answers().get(0).correct()).isTrue();
        assertThat(result.answers().get(1).correct()).isFalse();

        List<WrongNoteResponse> wrongNotes = wrongNoteService.listWrongNotes(account.id(), false);
        assertThat(wrongNotes).hasSize(1);
        assertThat(wrongNotes.get(0).questionId()).isEqualTo(postgresQuestionId);
        assertThat(wrongNotes.get(0).wrongCount()).isEqualTo(1);
    }
}
