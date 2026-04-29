package com.psh.exam.attempt;

import com.psh.exam.exam.Choice;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class AttemptDtos {

    private AttemptDtos() {
    }

    public record SubmitAttemptRequest(
            List<AnswerRequest> answers
    ) {
    }

    public record AnswerRequest(
            Long questionId,
            Set<Long> selectedChoiceIds
    ) {
    }

    public record AttemptResultResponse(
            Long attemptId,
            Long examId,
            String examTitle,
            int earnedScore,
            int totalScore,
            double percentage,
            LocalDateTime submittedAt,
            List<AnswerResultResponse> answers
    ) {
        public static AttemptResultResponse from(ExamAttempt attempt) {
            return new AttemptResultResponse(
                    attempt.getId(),
                    attempt.getExam().getId(),
                    attempt.getExam().getTitle(),
                    attempt.getEarnedScore(),
                    attempt.getTotalScore(),
                    calculatePercentage(attempt),
                    attempt.getSubmittedAt(),
                    attempt.getAnswers().stream()
                            .sorted(Comparator.comparingInt(answer -> answer.getQuestion().getSortOrder()))
                            .map(AnswerResultResponse::from)
                            .toList()
            );
        }

        private static double calculatePercentage(ExamAttempt attempt) {
            if (attempt.getTotalScore() == 0) {
                return 0.0;
            }
            return Math.round((attempt.getEarnedScore() * 10000.0) / attempt.getTotalScore()) / 100.0;
        }
    }

    public record AnswerResultResponse(
            Long questionId,
            String prompt,
            boolean correct,
            int earnedPoints,
            int maxPoints,
            Set<Long> selectedChoiceIds,
            Set<Long> correctChoiceIds,
            String explanation
    ) {
        public static AnswerResultResponse from(AttemptAnswer answer) {
            return new AnswerResultResponse(
                    answer.getQuestion().getId(),
                    answer.getQuestion().getPrompt(),
                    answer.isCorrect(),
                    answer.getEarnedPoints(),
                    answer.getQuestion().getPoints(),
                    answer.getSelectedChoices().stream()
                            .sorted(Comparator.comparingInt(Choice::getSortOrder))
                            .map(Choice::getId)
                            .collect(Collectors.toCollection(java.util.LinkedHashSet::new)),
                    answer.getQuestion().getChoices().stream()
                            .filter(Choice::isCorrect)
                            .sorted(Comparator.comparingInt(Choice::getSortOrder))
                            .map(Choice::getId)
                            .collect(Collectors.toCollection(java.util.LinkedHashSet::new)),
                    answer.getQuestion().getExplanation()
            );
        }
    }
}
