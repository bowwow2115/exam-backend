package com.psh.exam.wrongnote;

import com.psh.exam.exam.Choice;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public final class WrongNoteDtos {

    private WrongNoteDtos() {
    }

    public record UpdateWrongNoteRequest(
            String personalNote,
            Boolean resolved
    ) {
    }

    public record CreateWrongNoteRequest(
            Long examId,
            Long questionId,
            List<Long> selectedChoiceIds
    ) {
    }

    public record WrongNoteResponse(
            Long id,
            Long examId,
            String examTitle,
            Long questionId,
            String prompt,
            String explanation,
            int wrongCount,
            boolean resolved,
            String personalNote,
            LocalDateTime lastWrongAt,
            List<WrongNoteChoiceResponse> choices
    ) {
        public static WrongNoteResponse from(WrongNote wrongNote) {
            return new WrongNoteResponse(
                    wrongNote.getId(),
                    wrongNote.getQuestion().getExam().getId(),
                    wrongNote.getQuestion().getExam().getTitle(),
                    wrongNote.getQuestion().getId(),
                    wrongNote.getQuestion().getPrompt(),
                    wrongNote.getQuestion().getExplanation(),
                    wrongNote.getWrongCount(),
                    wrongNote.isResolved(),
                    wrongNote.getPersonalNote(),
                    wrongNote.getLastWrongAt(),
                    wrongNote.getQuestion().getChoices().stream()
                            .sorted(Comparator.comparingInt(Choice::getSortOrder))
                            .map(WrongNoteChoiceResponse::from)
                            .toList()
            );
        }
    }

    public record WrongNoteChoiceResponse(
            Long id,
            int sortOrder,
            String text,
            boolean correct
    ) {
        public static WrongNoteChoiceResponse from(Choice choice) {
            return new WrongNoteChoiceResponse(
                    choice.getId(),
                    choice.getSortOrder(),
                    choice.getText(),
                    choice.isCorrect()
            );
        }
    }
}
