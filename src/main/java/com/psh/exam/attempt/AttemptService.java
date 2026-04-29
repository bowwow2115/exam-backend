package com.psh.exam.attempt;

import com.psh.exam.account.Account;
import com.psh.exam.account.AccountService;
import com.psh.exam.attempt.AttemptDtos.AnswerRequest;
import com.psh.exam.attempt.AttemptDtos.AttemptResultResponse;
import com.psh.exam.attempt.AttemptDtos.SubmitAttemptRequest;
import com.psh.exam.exam.Choice;
import com.psh.exam.exam.Exam;
import com.psh.exam.exam.ExamService;
import com.psh.exam.exam.Question;
import com.psh.exam.wrongnote.WrongNoteService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AttemptService {

    private final ExamAttemptRepository examAttemptRepository;
    private final ExamService examService;
    private final AccountService accountService;
    private final WrongNoteService wrongNoteService;

    public AttemptService(
            ExamAttemptRepository examAttemptRepository,
            ExamService examService,
            AccountService accountService,
            WrongNoteService wrongNoteService
    ) {
        this.examAttemptRepository = examAttemptRepository;
        this.examService = examService;
        this.accountService = accountService;
        this.wrongNoteService = wrongNoteService;
    }

    @Transactional
    public AttemptResultResponse submitAttempt(Long accountId, Long examId, SubmitAttemptRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "응시 제출 요청 본문이 필요합니다.");
        }

        Account account = accountService.getAccount(accountId);
        Exam exam = examService.getDetailedExam(examId);
        if (!exam.isPublished()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "공개된 시험만 응시할 수 있습니다.");
        }

        Map<Long, AnswerRequest> answersByQuestionId = indexAnswers(request.answers());
        ExamAttempt attempt = new ExamAttempt(account, exam);
        int earnedScore = 0;
        int totalScore = 0;

        // 제출되지 않은 문항도 오답으로 처리해야 하므로 시험의 전체 문항을 기준으로 순회합니다.
        for (Question question : exam.getQuestions()) {
            AnswerRequest answerRequest = answersByQuestionId.get(question.getId());
            Set<Long> selectedChoiceIds = answerRequest == null || answerRequest.selectedChoiceIds() == null
                    ? Collections.emptySet()
                    : answerRequest.selectedChoiceIds();

            Map<Long, Choice> choicesById = question.getChoices().stream()
                    .collect(Collectors.toMap(Choice::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
            Set<Choice> selectedChoices = resolveSelectedChoices(question, selectedChoiceIds, choicesById);
            Set<Long> correctChoiceIds = question.getChoices().stream()
                    .filter(Choice::isCorrect)
                    .map(Choice::getId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            // 다중 정답은 정답 집합과 제출 집합이 완전히 같아야 정답입니다.
            // 정답을 하나 덜 고르거나 오답을 하나 더 고르면 해당 문항은 0점입니다.
            boolean correct = selectedChoiceIds.equals(correctChoiceIds);
            int earnedPoints = correct ? question.getPoints() : 0;
            totalScore += question.getPoints();
            earnedScore += earnedPoints;

            AttemptAnswer answer = new AttemptAnswer(question, selectedChoices, correct, earnedPoints);
            attempt.addAnswer(answer);
        }

        attempt.finish(earnedScore, totalScore);
        ExamAttempt savedAttempt = examAttemptRepository.save(attempt);
        // WrongNote.lastAttempt가 저장된 ExamAttempt를 참조하므로 attempt를 먼저 영속화한 뒤 오답노트를 갱신합니다.
        savedAttempt.getAnswers()
                .forEach(answer -> wrongNoteService.recordAnswer(savedAttempt, answer.getQuestion(), answer.isCorrect()));

        return AttemptResultResponse.from(savedAttempt);
    }

    @Transactional(readOnly = true)
    public AttemptResultResponse getAttemptResult(Long accountId, Long attemptId) {
        ExamAttempt attempt = examAttemptRepository.findDetailedById(attemptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "응시 결과를 찾을 수 없습니다."));
        if (!attempt.getAccount().getId().equals(accountId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인의 응시 결과만 조회할 수 있습니다.");
        }
        return AttemptResultResponse.from(attempt);
    }

    private Map<Long, AnswerRequest> indexAnswers(List<AnswerRequest> answers) {
        if (answers == null) {
            return Collections.emptyMap();
        }
        Map<Long, AnswerRequest> indexed = new LinkedHashMap<>();
        for (AnswerRequest answer : answers) {
            if (answer.questionId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "questionId는 필수입니다.");
            }
            // 같은 문항 답안이 두 번 들어오면 뒤의 값으로 덮지 않고 요청 오류로 처리합니다.
            if (indexed.put(answer.questionId(), answer) != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "같은 문항에 대한 답안이 중복되었습니다.");
            }
        }
        return indexed;
    }

    private Set<Choice> resolveSelectedChoices(
            Question question,
            Set<Long> selectedChoiceIds,
            Map<Long, Choice> choicesById
    ) {
        Set<Choice> selectedChoices = new LinkedHashSet<>();
        for (Long choiceId : selectedChoiceIds) {
            Choice choice = choicesById.get(choiceId);
            if (choice == null) {
                // 다른 시험/문항의 choiceId로 제출 점수를 조작하지 못하도록 문항 소속을 검증합니다.
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "문항 " + question.getId() + "에 속하지 않는 선택지가 포함되어 있습니다."
                );
            }
            selectedChoices.add(choice);
        }
        return selectedChoices;
    }
}
