package com.psh.exam.exam;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public final class ExamDtos {

    private ExamDtos() {
    }

    public record CreateExamRequest(
            String title,
            String description,
            Integer timeLimitMinutes,
            Boolean published,
            List<CreateQuestionRequest> questions
    ) {
    }

    public record CreateQuestionRequest(
            String prompt,
            Integer points,
            String explanation,
            List<CreateChoiceRequest> choices
    ) {
    }

    public record CreateChoiceRequest(
            String text,
            Boolean correct
    ) {
    }

    public record ExamSummaryResponse(
            Long id,
            String title,
            String description,
            Integer timeLimitMinutes,
            int questionCount,
            boolean published,
            String createdBy,
            LocalDateTime createdAt
    ) {
        public static ExamSummaryResponse from(Exam exam) {
            return new ExamSummaryResponse(
                    exam.getId(),
                    exam.getTitle(),
                    exam.getDescription(),
                    exam.getTimeLimitMinutes(),
                    exam.getQuestions().size(),
                    exam.isPublished(),
                    exam.getCreatedBy().getDisplayName(),
                    exam.getCreatedAt()
            );
        }
    }

    public record ExamDetailResponse(
            Long id,
            String title,
            String description,
            Integer timeLimitMinutes,
            boolean published,
            String createdBy,
            List<QuestionResponse> questions,
            LocalDateTime createdAt
    ) {
        public static ExamDetailResponse from(Exam exam, boolean includeCorrectAnswers) {
            return new ExamDetailResponse(
                    exam.getId(),
                    exam.getTitle(),
                    exam.getDescription(),
                    exam.getTimeLimitMinutes(),
                    exam.isPublished(),
                    exam.getCreatedBy().getDisplayName(),
                    exam.getQuestions().stream()
                            .sorted(Comparator.comparingInt(Question::getSortOrder))
                            .map(question -> QuestionResponse.from(question, includeCorrectAnswers))
                            .toList(),
                    exam.getCreatedAt()
            );
        }
    }

    public record QuestionResponse(
            Long id,
            int sortOrder,
            String prompt,
            int points,
            String explanation,
            /** Count of correct choices (for attempt UI; which choices are correct remains hidden on published exams). */
            int correctAnswerCount,
            List<ChoiceResponse> choices
    ) {
        public static QuestionResponse from(Question question, boolean includeCorrectAnswers) {
            int correctCount = (int) question.getChoices().stream()
                    .filter(Choice::isCorrect)
                    .count();
            return new QuestionResponse(
                    question.getId(),
                    question.getSortOrder(),
                    question.getPrompt(),
                    question.getPoints(),
                    includeCorrectAnswers ? question.getExplanation() : null,
                    correctCount,
                    question.getChoices().stream()
                            .sorted(Comparator.comparingInt(Choice::getSortOrder))
                            .map(choice -> ChoiceResponse.from(choice, includeCorrectAnswers))
                            .toList()
            );
        }
    }

    public record ChoiceResponse(
            Long id,
            int sortOrder,
            String text,
            Boolean correct
    ) {
        public static ChoiceResponse from(Choice choice, boolean includeCorrectAnswers) {
            return new ChoiceResponse(
                    choice.getId(),
                    choice.getSortOrder(),
                    choice.getText(),
                    includeCorrectAnswers ? choice.isCorrect() : null
            );
        }
    }

    /**
     * 로그인 사용자가 응시 중 정답 보기를 요청할 때 사용합니다. (공개 시험 상세 응답에는 정답이 포함되지 않습니다.)
     */
    public record QuestionCorrectChoiceIdsResponse(
            List<Long> correctChoiceIds
    ) {
    }
}
