package com.psh.exam.wrongnote;

import com.psh.exam.exam.QExam;
import com.psh.exam.exam.QQuestion;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class WrongNoteQueryRepository {

    private final JPAQueryFactory queryFactory;

    public WrongNoteQueryRepository(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    public List<WrongNote> findNotes(Long accountId, Boolean resolved) {
        QWrongNote wrongNote = QWrongNote.wrongNote;
        QQuestion question = QQuestion.question;
        QExam exam = QExam.exam;

        return queryFactory
                .selectFrom(wrongNote)
                .join(wrongNote.question, question).fetchJoin()
                .join(question.exam, exam).fetchJoin()
                .leftJoin(question.choices).fetchJoin()
                .where(
                        wrongNote.account.id.eq(accountId),
                        resolvedEq(wrongNote, resolved)
                )
                // 선택지 컬렉션 fetch join 때문에 같은 WrongNote row가 늘어날 수 있어 distinct로 엔티티 중복을 제거합니다.
                .distinct()
                .orderBy(wrongNote.lastWrongAt.desc())
                .fetch();
    }

    private BooleanExpression resolvedEq(QWrongNote wrongNote, Boolean resolved) {
        return resolved == null ? null : wrongNote.resolved.eq(resolved);
    }
}
