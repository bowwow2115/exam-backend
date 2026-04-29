package com.psh.exam.exam;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 문항 선택지입니다.
 * 다중 정답 시험에서는 여러 Choice가 correct=true일 수 있습니다.
 */
@Entity
@Table(name = "question_choices")
public class Choice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(nullable = false, name = "sort_order")
    private int sortOrder;

    @Column(nullable = false, name = "choice_text", columnDefinition = "text")
    private String text;

    /**
     * 채점 기준입니다. 공개 시험 조회 DTO에서는 이 값이 노출되지 않도록 분리합니다.
     */
    @Column(nullable = false, name = "is_correct")
    private boolean correct;

    protected Choice() {
    }

    public Choice(int sortOrder, String text, boolean correct) {
        this.sortOrder = sortOrder;
        this.text = text;
        this.correct = correct;
    }

    void assignQuestion(Question question) {
        this.question = question;
    }

    public Long getId() {
        return id;
    }

    public Question getQuestion() {
        return question;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public String getText() {
        return text;
    }

    public boolean isCorrect() {
        return correct;
    }
}
