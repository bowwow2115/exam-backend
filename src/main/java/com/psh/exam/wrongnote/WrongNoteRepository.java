package com.psh.exam.wrongnote;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WrongNoteRepository extends JpaRepository<WrongNote, Long> {

    Optional<WrongNote> findByAccountIdAndQuestionId(Long accountId, Long questionId);

    @EntityGraph(attributePaths = {"question", "question.exam", "question.choices"})
    Optional<WrongNote> findByIdAndAccountId(Long id, Long accountId);
}
