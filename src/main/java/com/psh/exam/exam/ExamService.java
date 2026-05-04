package com.psh.exam.exam;

import com.psh.exam.account.Account;
import com.psh.exam.account.AccountService;
import com.psh.exam.exam.ExamDtos.CreateChoiceRequest;
import com.psh.exam.exam.ExamDtos.CreateExamRequest;
import com.psh.exam.exam.ExamDtos.CreateQuestionRequest;
import com.psh.exam.exam.ExamDtos.ExamDetailResponse;
import com.psh.exam.exam.ExamDtos.ExamSummaryResponse;
import com.psh.exam.exam.ExamDtos.QuestionCorrectChoiceIdsResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;

@Service
public class ExamService {

    private final ExamRepository examRepository;
    private final AccountService accountService;

    public ExamService(ExamRepository examRepository, AccountService accountService) {
        this.examRepository = examRepository;
        this.accountService = accountService;
    }

    @Transactional
    public ExamDetailResponse createExam(Long accountId, CreateExamRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "시험 생성 요청 본문이 필요합니다.");
        }

        Account creator = accountService.getAccount(accountId);
        Exam exam = new Exam(
                requireLength(request.title(), "시험 제목", 1, 200),
                trimToNull(request.description()),
                validateTimeLimit(request.timeLimitMinutes()),
                Boolean.TRUE.equals(request.published()),
                creator
        );

        List<CreateQuestionRequest> questions = request.questions();
        if (questions == null || questions.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "시험에는 최소 1개 이상의 문항이 필요합니다.");
        }

        // 요청 배열 순서를 그대로 sortOrder로 저장해 출제자가 입력한 문항 순서를 보존합니다.
        int questionOrder = 1;
        for (CreateQuestionRequest questionRequest : questions) {
            Question question = new Question(
                    questionOrder++,
                    requireLength(questionRequest.prompt(), "문항", 1, 5_000),
                    questionRequest.points() == null ? 1 : validatePositive(questionRequest.points(), "문항 배점"),
                    trimToNull(questionRequest.explanation())
            );

            List<CreateChoiceRequest> choices = questionRequest.choices();
            if (choices == null || choices.size() < 2) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "각 문항에는 최소 2개 이상의 선택지가 필요합니다.");
            }

            int correctCount = 0;
            int choiceOrder = 1;
            // 다중 정답을 지원하므로 correct=true 선택지가 여러 개 있어도 허용합니다.
            for (CreateChoiceRequest choiceRequest : choices) {
                boolean correct = Boolean.TRUE.equals(choiceRequest.correct());
                if (correct) {
                    correctCount++;
                }
                question.addChoice(new Choice(
                        choiceOrder++,
                        requireLength(choiceRequest.text(), "선택지", 1, 2_000),
                        correct
                    ));
            }
            if (correctCount == 0) {
                // 정답이 없는 문항은 채점이 불가능하므로 생성 단계에서 차단합니다.
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "각 문항에는 최소 1개 이상의 정답 선택지가 필요합니다.");
            }
            exam.addQuestion(question);
        }

        return ExamDetailResponse.from(examRepository.save(exam), true);
    }

    @Transactional(readOnly = true)
    public List<ExamSummaryResponse> listPublishedExams() {
        return examRepository.findByPublishedTrueOrderByCreatedAtDesc().stream()
                .map(ExamSummaryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ExamDetailResponse getPublishedExam(Long examId) {
        Exam exam = getDetailedExam(examId);
        if (!exam.isPublished()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "시험을 찾을 수 없습니다.");
        }
        return ExamDetailResponse.from(exam, false);
    }

    @Transactional(readOnly = true)
    public Exam getDetailedExam(Long examId) {
        return examRepository.findDetailedById(examId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "시험을 찾을 수 없습니다."));
    }

    /**
     * 게시된 시험의 한 문항에 대해 정답 선택지 ID만 반환합니다. (JWT 필요)
     */
    @Transactional(readOnly = true)
    public QuestionCorrectChoiceIdsResponse getPublishedQuestionCorrectChoiceIds(Long examId, Long questionId) {
        Exam exam = getDetailedExam(examId);
        if (!exam.isPublished()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "시험을 찾을 수 없습니다.");
        }
        Question question = exam.getQuestions().stream()
                .filter(q -> q.getId().equals(questionId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "문항을 찾을 수 없습니다."));
        List<Long> ids = question.getChoices().stream()
                .filter(Choice::isCorrect)
                .map(Choice::getId)
                .sorted(Comparator.naturalOrder())
                .toList();
        return new QuestionCorrectChoiceIdsResponse(ids);
    }

    private Integer validateTimeLimit(Integer timeLimitMinutes) {
        if (timeLimitMinutes == null) {
            return null;
        }
        return validatePositive(timeLimitMinutes, "제한 시간");
    }

    private int validatePositive(int value, String fieldName) {
        if (value <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + "은 1 이상이어야 합니다.");
        }
        return value;
    }

    private String requireLength(String value, String fieldName, int min, int max) {
        String trimmed = trimToNull(value);
        if (trimmed == null || trimmed.length() < min || trimmed.length() > max) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    fieldName + "은 " + min + "자 이상 " + max + "자 이하여야 합니다."
            );
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
