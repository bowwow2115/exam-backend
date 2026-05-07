package com.psh.exam.wrongnote;

import com.psh.exam.account.Account;
import com.psh.exam.account.AccountService;
import com.psh.exam.attempt.ExamAttempt;
import com.psh.exam.exam.Choice;
import com.psh.exam.exam.Exam;
import com.psh.exam.exam.ExamRepository;
import com.psh.exam.exam.Question;
import com.psh.exam.wrongnote.WrongNoteDtos.CreateWrongNoteRequest;
import com.psh.exam.wrongnote.WrongNoteDtos.UpdateWrongNoteRequest;
import com.psh.exam.wrongnote.WrongNoteDtos.WrongNoteResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class WrongNoteService {

    private final WrongNoteRepository wrongNoteRepository;
    private final WrongNoteQueryRepository wrongNoteQueryRepository;
    private final ExamRepository examRepository;
    private final AccountService accountService;

    public WrongNoteService(
            WrongNoteRepository wrongNoteRepository,
            WrongNoteQueryRepository wrongNoteQueryRepository,
            ExamRepository examRepository,
            AccountService accountService
    ) {
        this.wrongNoteRepository = wrongNoteRepository;
        this.wrongNoteQueryRepository = wrongNoteQueryRepository;
        this.examRepository = examRepository;
        this.accountService = accountService;
    }

    @Transactional
    public void recordAnswer(ExamAttempt attempt, Question question, boolean correct) {
        // 오답노트는 account/question 단위로 하나만 유지합니다.
        // 이미 있으면 상태를 갱신하고, 없으면 오답일 때만 새로 만듭니다.
        wrongNoteRepository.findByAccountIdAndQuestionId(attempt.getAccount().getId(), question.getId())
                .ifPresentOrElse(
                        wrongNote -> updateExisting(wrongNote, attempt, correct),
                        () -> createIfWrong(attempt, question, correct)
                );
    }

    @Transactional(readOnly = true)
    public List<WrongNoteResponse> listWrongNotes(Long accountId, Boolean resolved) {
        return wrongNoteQueryRepository.findNotes(accountId, resolved).stream()
                .map(WrongNoteResponse::from)
                .toList();
    }

    @Transactional
    public WrongNoteResponse createWrongNote(Long accountId, CreateWrongNoteRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "오답노트 생성 요청 본문이 필요합니다.");
        }
        if (request.examId() == null || request.questionId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "시험과 문항 정보가 필요합니다.");
        }

        Exam exam = examRepository.findDetailedById(request.examId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "시험을 찾을 수 없습니다."));
        Question question = exam.getQuestions().stream()
                .filter(q -> q.getId().equals(request.questionId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "문항을 찾을 수 없습니다."));

        Set<Long> selectedChoiceIds = validateSelectedChoiceIds(question, request.selectedChoiceIds());
        if (isCorrectSelection(question, selectedChoiceIds)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "정답 문항은 오답노트에 추가할 수 없습니다.");
        }
        if (wrongNoteRepository.findByAccountIdAndQuestionId(accountId, question.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 오답노트에 추가된 문항입니다.");
        }

        Account account = accountService.getAccount(accountId);
        return WrongNoteResponse.from(wrongNoteRepository.save(new WrongNote(account, question, null)));
    }

    @Transactional
    public WrongNoteResponse updateWrongNote(Long accountId, Long noteId, UpdateWrongNoteRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "오답노트 수정 요청 본문이 필요합니다.");
        }

        WrongNote wrongNote = wrongNoteRepository.findByIdAndAccountId(noteId, accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "오답노트를 찾을 수 없습니다."));
        wrongNote.update(request.personalNote(), request.resolved());
        return WrongNoteResponse.from(wrongNote);
    }

    private void updateExisting(WrongNote wrongNote, ExamAttempt attempt, boolean correct) {
        if (correct) {
            // 재응시에서 맞힌 문항은 복습 대상으로 남기되 해결 처리합니다.
            wrongNote.markResolved();
            return;
        }
        wrongNote.markWrong(attempt);
    }

    private void createIfWrong(ExamAttempt attempt, Question question, boolean correct) {
        if (!correct) {
            wrongNoteRepository.save(new WrongNote(attempt.getAccount(), question, attempt));
        }
    }

    private Set<Long> validateSelectedChoiceIds(Question question, List<Long> selectedChoiceIds) {
        Set<Long> selected = selectedChoiceIds == null ? Set.of() : new HashSet<>(selectedChoiceIds);
        if (selected.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "선택한 보기가 필요합니다.");
        }
        Set<Long> available = new HashSet<>();
        for (Choice choice : question.getChoices()) {
            available.add(choice.getId());
        }
        if (!available.containsAll(selected)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "문항에 포함되지 않은 선택지가 있습니다.");
        }
        return selected;
    }

    private boolean isCorrectSelection(Question question, Set<Long> selected) {
        Set<Long> correct = new HashSet<>();
        for (Choice choice : question.getChoices()) {
            if (choice.isCorrect()) {
                correct.add(choice.getId());
            }
        }
        return selected.equals(correct);
    }
}
