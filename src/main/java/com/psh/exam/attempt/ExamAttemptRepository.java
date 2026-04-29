package com.psh.exam.attempt;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ExamAttemptRepository extends JpaRepository<ExamAttempt, Long> {

    @EntityGraph(attributePaths = {
            "account",
            "exam",
            "answers",
            "answers.question",
            "answers.question.choices",
            "answers.selectedChoices"
    })
    @Query("select a from ExamAttempt a where a.id = :id")
    Optional<ExamAttempt> findDetailedById(@Param("id") Long id);
}
