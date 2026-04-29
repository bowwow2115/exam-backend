package com.psh.exam.attempt;

import com.psh.exam.account.Account;
import com.psh.exam.common.BaseTimeEntity;
import com.psh.exam.exam.Exam;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 사용자의 한 번의 시험 제출 결과입니다.
 * 제출 시점의 선택 내역과 점수를 저장해 이후 시험 내용이 바뀌어도 결과를 조회할 수 있게 합니다.
 */
@Entity
@Table(name = "exam_attempts")
public class ExamAttempt extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    @Column(nullable = false, name = "earned_score")
    private int earnedScore;

    @Column(nullable = false, name = "total_score")
    private int totalScore;

    @Column(nullable = false, name = "submitted_at")
    private LocalDateTime submittedAt;

    /**
     * AttemptAnswer가 FK 주인입니다. 응시 결과 저장 시 답안도 함께 저장합니다.
     */
    @OneToMany(mappedBy = "attempt", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private Set<AttemptAnswer> answers = new LinkedHashSet<>();

    protected ExamAttempt() {
    }

    public ExamAttempt(Account account, Exam exam) {
        this.account = account;
        this.exam = exam;
        this.submittedAt = LocalDateTime.now();
    }

    public void addAnswer(AttemptAnswer answer) {
        answers.add(answer);
        answer.assignAttempt(this);
    }

    public void finish(int earnedScore, int totalScore) {
        // 점수 계산이 끝난 뒤 한 번에 확정해 중간 상태가 응답으로 나가지 않게 합니다.
        this.earnedScore = earnedScore;
        this.totalScore = totalScore;
        this.submittedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Account getAccount() {
        return account;
    }

    public Exam getExam() {
        return exam;
    }

    public int getEarnedScore() {
        return earnedScore;
    }

    public int getTotalScore() {
        return totalScore;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public Set<AttemptAnswer> getAnswers() {
        return answers;
    }
}
