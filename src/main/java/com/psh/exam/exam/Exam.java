package com.psh.exam.exam;

import com.psh.exam.account.Account;
import com.psh.exam.common.BaseTimeEntity;
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

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 시험의 루트 엔티티입니다.
 * 문항과 선택지는 시험 생성 시 함께 저장되므로 cascade/orphanRemoval로 생명주기를 묶습니다.
 */
@Entity
@Table(name = "exams")
public class Exam extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "time_limit_minutes")
    private Integer timeLimitMinutes;

    @Column(nullable = false)
    private boolean published;

    /**
     * 생성자 계정은 조회 시 불필요한 조인을 피하기 위해 지연 로딩합니다.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private Account createdBy;

    /**
     * Set을 쓰되 sortOrder로 정렬해 API 응답과 채점 순서를 안정적으로 유지합니다.
     */
    @OneToMany(mappedBy = "exam", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private Set<Question> questions = new LinkedHashSet<>();

    protected Exam() {
    }

    public Exam(String title, String description, Integer timeLimitMinutes, boolean published, Account createdBy) {
        this.title = title;
        this.description = description;
        this.timeLimitMinutes = timeLimitMinutes;
        this.published = published;
        this.createdBy = createdBy;
    }

    public void addQuestion(Question question) {
        // 양방향 연관관계의 주인은 Question.exam이므로 편의 메서드에서 함께 세팅합니다.
        questions.add(question);
        question.assignExam(this);
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Integer getTimeLimitMinutes() {
        return timeLimitMinutes;
    }

    public boolean isPublished() {
        return published;
    }

    public Account getCreatedBy() {
        return createdBy;
    }

    public Set<Question> getQuestions() {
        return questions;
    }
}
