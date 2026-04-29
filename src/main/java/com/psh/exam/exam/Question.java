package com.psh.exam.exam;

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
 * 시험 문항입니다.
 * 객관식 다중 정답을 지원하므로 정답 여부는 문항이 아니라 각 선택지에 둡니다.
 */
@Entity
@Table(name = "questions")
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    @Column(nullable = false, name = "sort_order")
    private int sortOrder;

    @Column(nullable = false, columnDefinition = "text")
    private String prompt;

    @Column(nullable = false)
    private int points;

    @Column(columnDefinition = "text")
    private String explanation;

    /**
     * 선택지는 문항에 종속됩니다. 문항 삭제 시 선택지도 함께 삭제됩니다.
     */
    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private Set<Choice> choices = new LinkedHashSet<>();

    protected Question() {
    }

    public Question(int sortOrder, String prompt, int points, String explanation) {
        this.sortOrder = sortOrder;
        this.prompt = prompt;
        this.points = points;
        this.explanation = explanation;
    }

    void assignExam(Exam exam) {
        this.exam = exam;
    }

    public void addChoice(Choice choice) {
        // Choice.question이 FK 주인이므로 문항 추가 시 역방향도 같이 맞춥니다.
        choices.add(choice);
        choice.assignQuestion(this);
    }

    public Long getId() {
        return id;
    }

    public Exam getExam() {
        return exam;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public String getPrompt() {
        return prompt;
    }

    public int getPoints() {
        return points;
    }

    public String getExplanation() {
        return explanation;
    }

    public Set<Choice> getChoices() {
        return choices;
    }
}
