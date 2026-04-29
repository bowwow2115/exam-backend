package com.psh.exam.wrongnote;

import com.psh.exam.attempt.ExamAttempt;
import com.psh.exam.exam.Question;
import com.psh.exam.wrongnote.WrongNoteDtos.UpdateWrongNoteRequest;
import com.psh.exam.wrongnote.WrongNoteDtos.WrongNoteResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class WrongNoteService {

    private final WrongNoteRepository wrongNoteRepository;
    private final WrongNoteQueryRepository wrongNoteQueryRepository;

    public WrongNoteService(WrongNoteRepository wrongNoteRepository, WrongNoteQueryRepository wrongNoteQueryRepository) {
        this.wrongNoteRepository = wrongNoteRepository;
        this.wrongNoteQueryRepository = wrongNoteQueryRepository;
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
}
