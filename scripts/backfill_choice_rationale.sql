-- 기존 데이터: 문항 explanation을 정답 보기의 rationale로 복사합니다.
-- (오답 보기 rationale은 비워 둡니다. import/API로 보기별 해설을 채우세요.)
UPDATE question_choices qc
SET rationale = q.explanation
FROM questions q
WHERE qc.question_id = q.id
  AND qc.is_correct = true
  AND q.explanation IS NOT NULL
  AND trim(q.explanation) <> ''
  AND (qc.rationale IS NULL OR trim(qc.rationale) = '');
