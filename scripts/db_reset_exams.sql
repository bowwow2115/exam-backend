-- 응시·오답·선택지·문항·시험만 제거합니다. accounts 테이블은 유지합니다.
TRUNCATE TABLE attempt_answer_choices, attempt_answers, exam_attempts, wrong_notes, question_choices, questions, exams
RESTART IDENTITY CASCADE;
