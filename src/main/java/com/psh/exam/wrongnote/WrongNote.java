package com.psh.exam.wrongnote;

import com.psh.exam.account.Account;
import com.psh.exam.attempt.ExamAttempt;
import com.psh.exam.common.BaseTimeEntity;
import com.psh.exam.exam.Question;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * 사용자별 오답노트입니다.
 * 같은 사용자가 같은 문항을 여러 번 틀려도 하나의 노트에 누적되도록 account/question에 유니크 제약을 둡니다.
 */
@Entity
@Table(
        name = "wrong_notes",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_wrong_notes_account_question",
                columnNames = {"account_id", "question_id"}
        )
)
public class WrongNote extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_attempt_id")
    private ExamAttempt lastAttempt;

    /**
     * 사용자가 직접 적는 복습 메모입니다. 빈 문자열은 null로 정리합니다.
     */
    @Column(name = "personal_note", columnDefinition = "text")
    private String personalNote;

    @Column(nullable = false)
    private boolean resolved;

    @Column(nullable = false, name = "wrong_count")
    private int wrongCount;

    @Column(nullable = false, name = "last_wrong_at")
    private LocalDateTime lastWrongAt;

    protected WrongNote() {
    }

    public WrongNote(Account account, Question question, ExamAttempt lastAttempt) {
        this.account = account;
        this.question = question;
        markWrong(lastAttempt);
    }

    public void markWrong(ExamAttempt attempt) {
        // 다시 틀리면 해결 상태를 해제하고 마지막 오답 시각과 누적 횟수를 갱신합니다.
        this.lastAttempt = attempt;
        this.resolved = false;
        this.wrongCount++;
        this.lastWrongAt = LocalDateTime.now();
    }

    public void markResolved() {
        // 같은 문항을 나중에 맞히면 기존 오답노트는 보관하되 해결 상태로 바꿉니다.
        this.resolved = true;
    }

    public void update(String personalNote, Boolean resolved) {
        if (personalNote != null) {
            this.personalNote = personalNote.trim().isEmpty() ? null : personalNote.trim();
        }
        if (resolved != null) {
            this.resolved = resolved;
        }
    }

    public Long getId() {
        return id;
    }

    public Account getAccount() {
        return account;
    }

    public Question getQuestion() {
        return question;
    }

    public ExamAttempt getLastAttempt() {
        return lastAttempt;
    }

    public String getPersonalNote() {
        return personalNote;
    }

    public boolean isResolved() {
        return resolved;
    }

    public int getWrongCount() {
        return wrongCount;
    }

    public LocalDateTime getLastWrongAt() {
        return lastWrongAt;
    }
}
