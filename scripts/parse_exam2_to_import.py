#!/usr/bin/env python3
"""
exam2.txt → data/exam2-import.json

문항별로 `답 :` 뒤에 오는 `이유`/`참고사항` 블록을 문항 explanation 및
정답 선지 rationale 로 넣고, 오답 선지에는 정답 안내 + 요약 문구를 채웁니다.
"""
import json
import re
from pathlib import Path


ANSWER_SPLIT_RE = re.compile(r"(?m)^답\s*[:：]\s*(.+?)\s*$")
OPTION_RE = re.compile(r"^\s*([A-Z])\.\s*(.*)$")
NOISE_LINE_RE = re.compile(r"^\s*(닫기|더보기|이유\s*:|참고사항|정답\s*:)\s*")


def _is_question_sentence(line: str) -> bool:
    """마지막으로 이어지는 질문/요구 문장(보기 직전) 판별."""
    s = line.strip()
    if not s:
        return False
    if "?" in s or "？" in s:
        return True
    if "까요" in s or "무엇입니까" in s or "어떻게 해야" in s or "어떤 결과" in s:
        return True
    return False


def _vignette_start_index(pre: list[str]) -> int:
    """
    이전 문항의 '이유/참고' 블록 뒤에 오는 본문(시나리오) 시작 위치.
    연속 빈 줄이 2줄 이상인 구간 직후를 새 시나리오 시작으로 본다(단일 빈 줄은 문단 나눔으로 간주).
    """
    best = 0
    run = 0
    for idx, line in enumerate(pre):
        if not line.strip():
            run += 1
        else:
            if run >= 2:
                best = idx
            run = 0
    return best


def extract_question_lines(lines, option_start_index):
    pre = lines[:option_start_index]
    if not pre:
        return []

    v_start = _vignette_start_index(pre)
    slice_pre = pre[v_start:]

    last_q_rel = -1
    for i, line in enumerate(slice_pre):
        if _is_question_sentence(line):
            last_q_rel = i
    if last_q_rel == -1:
        # Fallback: use the last non-empty paragraph right before options.
        tail = []
        for i in range(option_start_index - 1, -1, -1):
            if not lines[i].strip() and tail:
                break
            if lines[i].strip():
                tail.append(lines[i].strip())
        return list(reversed(tail))

    last_q_idx = v_start + last_q_rel

    question_lines = [line.strip() for line in pre[v_start : last_q_idx + 1] if line.strip()]
    return question_lines


def parse_options(lines, option_start_index):
    options = []
    current = None
    for line in lines[option_start_index:]:
        m = OPTION_RE.match(line)
        if m:
            letter = m.group(1)
            text = m.group(2).strip()
            if not text:
                text = f"Option {letter}"
            current = {"letter": letter, "text": text}
            options.append(current)
            continue

        stripped = line.strip()
        if not stripped:
            continue
        if NOISE_LINE_RE.match(stripped):
            continue
        if current is not None:
            current["text"] += " " + stripped

    return options


def peel_meta_after_answer(chunk: str) -> tuple[str | None, str]:
    """
    `답` 줄 다음 조각(parts[i+1])에서, 앞부분의 이유·참고 블록을 떼어 내고
    나머지를 다음 문항 본문으로 돌려준다.
    연속 빈 줄 3줄 이상이면 이전 문항 해설 블록 종료로 본다.
    """
    if not chunk:
        return None, ""
    lines = chunk.splitlines()
    i = 0
    while i < len(lines) and not lines[i].strip():
        i += 1
    if i >= len(lines):
        return None, ""
    if lines[i].strip() == "닫기":
        i += 1
        while i < len(lines) and not lines[i].strip():
            i += 1
    if i >= len(lines):
        return None, ""
    first = lines[i].strip()
    if not (first.startswith("이유") or first.startswith("참고사항")):
        return None, chunk

    buf: list[str] = []
    blank_run = 0
    while i < len(lines):
        line = lines[i]
        if not line.strip():
            blank_run += 1
            if blank_run >= 3 and any(s.strip() for s in buf):
                i += 1
                while i < len(lines) and not lines[i].strip():
                    i += 1
                break
            i += 1
            continue
        blank_run = 0
        buf.append(line)
        i += 1

    explanation = "\n".join(buf).strip()
    rest = "\n".join(lines[i:]).lstrip("\n")
    return explanation or None, rest


def _clip(s: str, max_len: int = 7900) -> str:
    if len(s) <= max_len:
        return s
    return s[: max_len - 1] + "…"


def _clean_explanation_header(raw: str) -> str:
    """저장용: 선두 '이유 :' 한 번 제거(본문은 그대로)."""
    s = raw.strip()
    s = re.sub(r"^이유\s*[:：]\s*", "", s, count=1)
    return s.strip()


def build_choices_for_export(
    options: list[dict],
    correct_letters: set[str],
    explanation: str | None,
) -> list[dict]:
    """선지 텍스트·정답·rationale(정답=해설, 오답=짧은 대조)을 채운다."""
    expl = (explanation or "").strip()
    letter_label = ", ".join(sorted(correct_letters))
    out: list[dict] = []
    for opt in options:
        letter = opt["letter"]
        text = opt["text"].strip()
        correct = letter in correct_letters
        if correct:
            if expl:
                rationale = expl
            else:
                rationale = (
                    f"{letter}번 보기가 정답입니다. "
                    "원문에 별도 해설이 없어 정답 여부만 표시합니다."
                )
        else:
            if expl:
                rationale = (
                    f"정답은 {letter_label}번 보기입니다. "
                    f"{letter}번 보기는 시나리오와 제약을 고려할 때 최선의 솔루션이 아닙니다. "
                    "맞는 이유·배경 설명은 위 문항 해설을 참고하세요."
                )
            else:
                rationale = (
                    f"정답은 {letter_label}번 보기입니다. "
                    f"{letter}번 보기는 조건에 맞는 최선의 솔루션이 아닙니다."
                )
        out.append({"text": text, "correct": correct, "rationale": _clip(rationale)})
    return out


def build_question_explanation(options: list[dict], correct_letters: set[str], explanation: str | None) -> str:
    """원문 해설이 없으면 정답 보기 기반 문항 해설을 생성한다."""
    expl = (explanation or "").strip()
    if expl:
        return expl
    correct_lines = []
    for opt in options:
        if opt["letter"] in correct_letters:
            correct_lines.append(f"- {opt['letter']}. {opt['text'].strip()}")
    letter_label = ", ".join(sorted(correct_letters))
    return _clip(
        "원문에 별도 해설이 없어 정답 보기 기준으로 해설을 생성했습니다.\n\n"
        f"정답은 {letter_label}번입니다.\n"
        + "\n".join(correct_lines)
        + "\n\n위 보기 조합이 문제에서 제시한 요구사항과 제약 조건에 가장 부합합니다.",
        7900,
    )


def parse_block(body, answer_text):
    lines = body.splitlines()
    option_indices = [i for i, line in enumerate(lines) if OPTION_RE.match(line)]
    if not option_indices:
        return None

    question_lines = []
    options = []
    candidate_starts = [
        i for i in option_indices if OPTION_RE.match(lines[i]).group(1) == "A"
    ] or option_indices
    for candidate_start in reversed(candidate_starts):
        candidate_questions = extract_question_lines(lines, candidate_start)
        candidate_options = parse_options(lines, candidate_start)
        if candidate_questions and len(candidate_options) >= 2:
            question_lines = candidate_questions
            options = candidate_options
            break
    if not question_lines or len(options) < 2:
        return None

    answer_letters = set(re.findall(r"[A-Z]", answer_text.upper()))
    if not answer_letters:
        return None

    option_letters = {opt["letter"] for opt in options}
    correct_letters = answer_letters.intersection(option_letters)
    if not correct_letters:
        return None

    return {
        "prompt": "\n".join(question_lines).strip(),
        "options": options,
        "correct_letters": correct_letters,
    }


def main():
    source = Path("src/main/resources/exam2.txt")
    text = source.read_text(encoding="utf-8")
    parts = ANSWER_SPLIT_RE.split(text)

    parsed: list[dict] = []
    skipped = 0
    skipped_samples = []
    remainder = parts[0]

    for i in range(1, len(parts), 2):
        answer_text = parts[i]
        body = remainder
        following = parts[i + 1] if i + 1 < len(parts) else ""
        explanation_raw, remainder = peel_meta_after_answer(following)

        raw = parse_block(body, answer_text)
        if raw is None:
            skipped += 1
            if len(skipped_samples) < 10:
                tail = "\n".join(body.splitlines()[-20:])
                skipped_samples.append(
                    {
                        "index": (i + 1) // 2,
                        "answer": answer_text.strip(),
                        "tail": tail[:800],
                    }
                )
            continue

        expl_raw = explanation_raw.strip() if explanation_raw else None
        expl = _clean_explanation_header(expl_raw) if expl_raw else None
        question_explanation = build_question_explanation(raw["options"], raw["correct_letters"], expl or expl_raw)
        choices = build_choices_for_export(raw["options"], raw["correct_letters"], question_explanation)
        parsed.append(
            {
                "prompt": raw["prompt"],
                "explanation": question_explanation,
                "choices": choices,
            }
        )

    with_expl = sum(1 for q in parsed if q.get("explanation"))
    with_rat = sum(
        1 for q in parsed for c in q["choices"] if (c.get("rationale") or "").strip()
    )

    exam = {
        "creator": {
            "email": "admin@example.com",
            "password": "password123",
            "displayName": "Admin",
        },
        "title": "AWS Developer Practice Exam 2 (Parsed)",
        "description": "Imported from src/main/resources/exam2.txt",
        "timeLimitMinutes": 130,
        "published": True,
        "questions": [
            {
                "prompt": q["prompt"],
                "points": 1,
                "explanation": q.get("explanation"),
                "choices": q["choices"],
            }
            for q in parsed
        ],
    }

    out_path = Path("data/exam2-import.json")
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(json.dumps(exam, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    print(f"answers_found={((len(parts)-1)//2)}")
    print(f"questions_parsed={len(parsed)}")
    print(f"questions_skipped={skipped}")
    print(f"questions_with_explanation={with_expl}")
    print(f"choice_rows_with_rationale={with_rat}")
    if parsed:
        print(f"first_prompt={parsed[0]['prompt'][:120]}")
        print(f"first_choices={len(parsed[0]['choices'])}")
        fe = parsed[0].get("explanation") or ""
        print(f"first_explanation_len={len(fe)}")
    if skipped_samples:
        print("skipped_samples=" + json.dumps(skipped_samples, ensure_ascii=False))
    print(f"output={out_path}")


if __name__ == "__main__":
    main()
