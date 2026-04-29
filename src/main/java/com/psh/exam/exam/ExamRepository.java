package com.psh.exam.exam;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExamRepository extends JpaRepository<Exam, Long> {

    @EntityGraph(attributePaths = {"createdBy", "questions"})
    List<Exam> findByPublishedTrueOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"createdBy", "questions", "questions.choices"})
    @Query("select e from Exam e where e.id = :id")
    Optional<Exam> findDetailedById(@Param("id") Long id);
}
