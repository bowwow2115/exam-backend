package com.psh.exam.attempt;

import com.psh.exam.exam.Choice;
import com.psh.exam.exam.Question;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 한 응시에서 한 문항에 제출한 답안입니다.
 * 선택한 보기들을 조인 테이블로 보관해 단일 정답과 다중 정답을 같은 구조로 처리합니다.
 */
@Entity
@Table(
        name = "attempt_answers",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_attempt_answers_attempt_question",
                columnNames = {"attempt_id", "question_id"}
        )
)
public class AttemptAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false)
    private ExamAttempt attempt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    /**
     * 사용자가 선택한 보기 목록입니다. 빈 Set이면 미응답으로 간주합니다.
     */
    @ManyToMany
    @JoinTable(
            name = "attempt_answer_choices",
            joinColumns = @JoinColumn(name = "attempt_answer_id"),
            inverseJoinColumns = @JoinColumn(name = "choice_id")
    )
    private Set<Choice> selectedChoices = new LinkedHashSet<>();

    @Column(nullable = false)
    private boolean correct;

    /**
     * 현재 정책은 문항 단위 all-or-nothing 채점입니다. 부분 점수는 아직 저장하지 않습니다.
     */
    @Column(nullable = false, name = "earned_points")
    private int earnedPoints;

    protected AttemptAnswer() {
    }

    public AttemptAnswer(Question question, Set<Choice> selectedChoices, boolean correct, int earnedPoints) {
        this.question = question;
        this.selectedChoices.addAll(selectedChoices);
        this.correct = correct;
        this.earnedPoints = earnedPoints;
    }

    void assignAttempt(ExamAttempt attempt) {
        this.attempt = attempt;
    }

    public Long getId() {
        return id;
    }

    public ExamAttempt getAttempt() {
        return attempt;
    }

    public Question getQuestion() {
        return question;
    }

    public Set<Choice> getSelectedChoices() {
        return selectedChoices;
    }

    public boolean isCorrect() {
        return correct;
    }

    public int getEarnedPoints() {
        return earnedPoints;
    }
}
