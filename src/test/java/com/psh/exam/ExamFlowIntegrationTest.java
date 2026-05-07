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
import com.psh.exam.exam.ExamDtos.QuestionRevealResponse;
import com.psh.exam.exam.ExamService;
import com.psh.exam.wrongnote.WrongNoteDtos.WrongNoteResponse;
import com.psh.exam.wrongnote.WrongNoteDtos.CreateWrongNoteRequest;
import com.psh.exam.wrongnote.WrongNoteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                                        new CreateChoiceRequest("IDENTITY", true, "IDENTITY는 대표적인 JPA 식별자 생성 전략입니다."),
                                        new CreateChoiceRequest("SEQUENCE", true, "SEQUENCE는 대표적인 JPA 식별자 생성 전략입니다."),
                                        new CreateChoiceRequest("COOKIE", false, "COOKIE는 JPA 식별자 생성 전략이 아닙니다.")
                                )
                        ),
                        new CreateQuestionRequest(
                                "PostgreSQL 기본 포트는?",
                                1,
                                "PostgreSQL 기본 포트는 5432입니다.",
                                List.of(
                                        new CreateChoiceRequest("3306", false, "3306은 MySQL 기본 포트입니다."),
                                        new CreateChoiceRequest("5432", true, "5432는 PostgreSQL 기본 포트입니다.")
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

        QuestionRevealResponse reveal = examService.getPublishedQuestionReveal(exam.id(), postgresQuestionId);
        assertThat(reveal.correctChoiceIds()).containsExactly(exam.questions().get(1).choices().get(1).id());
        assertThat(reveal.questionExplanation()).isEqualTo("PostgreSQL 기본 포트는 5432입니다.");
        assertThat(reveal.choiceReveals())
                .extracting(QuestionRevealResponse.ChoiceRevealRow::rationale)
                .containsExactly("3306은 MySQL 기본 포트입니다.", "5432는 PostgreSQL 기본 포트입니다.");
    }

    @Test
    void createWrongNoteFromRevealOnlyAcceptsWrongSelectionAndRejectsDuplicates() {
        AccountResponse account = accountService.signUp(new SignUpRequest(
                "manual-note@example.com",
                "password123",
                "manual note user"
        ));
        ExamDetailResponse exam = examService.createExam(account.id(), new CreateExamRequest(
                "오답노트 수동 추가",
                "정답 확인에서 추가",
                10,
                true,
                List.of(new CreateQuestionRequest(
                        "AWS Lambda 로컬 이벤트 샘플 생성 명령은?",
                        1,
                        "sam local generate-event가 서비스 이벤트 구조에 맞는 샘플 이벤트를 생성합니다.",
                        List.of(
                                new CreateChoiceRequest("sam deploy", false, "sam deploy는 배포 명령입니다."),
                                new CreateChoiceRequest("sam local generate-event", true, "로컬 테스트용 이벤트 페이로드를 생성합니다.")
                        )
                ))
        ));
        Long questionId = exam.questions().get(0).id();
        Long wrongChoiceId = exam.questions().get(0).choices().get(0).id();
        Long correctChoiceId = exam.questions().get(0).choices().get(1).id();

        assertThatThrownBy(() -> wrongNoteService.createWrongNote(account.id(), new CreateWrongNoteRequest(
                exam.id(),
                questionId,
                List.of(correctChoiceId)
        )))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("정답 문항은 오답노트에 추가할 수 없습니다.");

        WrongNoteResponse created = wrongNoteService.createWrongNote(account.id(), new CreateWrongNoteRequest(
                exam.id(),
                questionId,
                List.of(wrongChoiceId)
        ));

        assertThat(created.questionId()).isEqualTo(questionId);
        assertThat(created.wrongCount()).isEqualTo(1);
        assertThat(wrongNoteService.listWrongNotes(account.id(), false)).hasSize(1);

        assertThatThrownBy(() -> wrongNoteService.createWrongNote(account.id(), new CreateWrongNoteRequest(
                exam.id(),
                questionId,
                List.of(wrongChoiceId)
        )))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("이미 오답노트에 추가된 문항입니다.");
    }
}
