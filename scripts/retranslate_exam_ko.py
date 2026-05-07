#!/usr/bin/env python3
"""
exam.md(Associate) + data/exam2-import.json → 한국어 재번역 → data/*.retranslated.json + PostgreSQL 반영.

  python3 scripts/retranslate_exam_ko.py                 # Associate + Exam2 번역 + DB
  python3 scripts/retranslate_exam_ko.py --json-only     # 번역·JSON만 (두 시험)
  python3 scripts/retranslate_exam_ko.py --db-only       # 기존 JSON으로 DB만
  python3 scripts/retranslate_exam_ko.py --write-seed-import  # exam.md → data/exam-import.seed.json
"""
from __future__ import annotations

import argparse
from collections import defaultdict
import json
import os
import re
import subprocess
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

TRANSLATE_URL = "https://translate.googleapis.com/translate_a/single"
SEP = "\n@@@SEP@@@\n"

# prompts + choices 원본 스크립트 용어 합집합 (영문 키 기준 dedupe, 긴 토큰 우선 치환)
_CORE_RAW = [
    ("AWS Serverless Application Model", "AWS 서버리스 애플리케이션 모델"),
    ("Application Load Balancer", "애플리케이션 로드 밸런서"),
    ("AWS Elastic Beanstalk", "AWS 엘라스틱 빈스톡"),
    ("Amazon Elastic Beanstalk", "아마존 엘라스틱 빈스톡"),
    ("Amazon API Gateway", "아마존 API 게이트웨이"),
    ("Amazon CloudFront", "아마존 CloudFront"),
    ("Amazon CloudWatch Logs", "아마존 CloudWatch Logs"),
    ("Amazon CloudWatch", "아마존 CloudWatch"),
    ("Amazon CloudTrail", "아마존 CloudTrail"),
    ("Amazon Cognito", "아마존 Cognito"),
    ("Amazon DynamoDB", "아마존 DynamoDB"),
    ("Amazon ElastiCache", "아마존 ElastiCache"),
    ("Amazon EventBridge", "아마존 EventBridge"),
    ("Amazon Kinesis", "아마존 Kinesis"),
    ("Amazon Route 53", "아마존 Route 53"),
    ("Amazon Aurora", "아마존 Aurora"),
    ("Amazon Linux", "아마존 Linux"),
    ("Amazon RDS", "아마존 RDS"),
    ("Amazon EC2", "아마존 EC2"),
    ("Amazon ECR", "아마존 ECR"),
    ("Amazon ECS", "아마존 ECS"),
    ("Amazon EFS", "아마존 EFS"),
    ("Amazon EKS", "아마존 EKS"),
    ("Amazon EMR", "아마존 EMR"),
    ("Amazon S3", "아마존 S3"),
    ("Amazon SES", "아마존 SES"),
    ("Amazon SNS", "아마존 SNS"),
    ("Amazon SQS", "아마존 SQS"),
    ("AWS CloudFormation", "AWS CloudFormation"),
    ("AWS CodeBuild", "AWS CodeBuild"),
    ("AWS CodeCommit", "AWS CodeCommit"),
    ("AWS CodeDeploy", "AWS CodeDeploy"),
    ("AWS CodePipeline", "AWS CodePipeline"),
    ("AWS Lambda", "AWS Lambda"),
    ("AWS Secrets Manager", "AWS Secrets Manager"),
    ("AWS Step Functions", "AWS Step Functions"),
    ("AWS X-Ray", "AWS X-Ray"),
    ("AWS CLI", "AWS CLI"),
    ("AWS KMS", "AWS KMS"),
    ("AWS SAM", "AWS SAM"),
    ("AWS SDK", "AWS SDK"),
    ("AWS STS", "AWS STS"),
    ("AWS", "AWS"),
]
_merged: dict[str, str] = {}
for eng, kor in _CORE_RAW:
    _merged[eng] = kor
CORE_TERMS = sorted(_merged.items(), key=lambda x: len(x[0]), reverse=True)

QUESTION_PATTERN = re.compile(r"^### ")
CHOICE_LINE_PATTERN = re.compile(r"^- \[[ x]\] (?P<text>.*)$")
CHOICE_SKIP_PATTERN = re.compile(r"^- \[[ x]\] ")
CODE_PATTERN = re.compile(r"`[^`]*`|https?://[^\s`]+|s3://[^\s`]+", re.IGNORECASE)

EXPECTED_QUESTIONS = 387
EXPECTED_CHOICES = 1619


def translate_batch(items: list[str], *, sl: str = "en", tl: str = "ko") -> list[str]:
    query = urllib.parse.urlencode(
        {
            "client": "gtx",
            "sl": sl,
            "tl": tl,
            "dt": "t",
            "q": SEP.join(items),
        }
    )
    url = f"{TRANSLATE_URL}?{query}"
    with urllib.request.urlopen(url, timeout=30) as response:
        payload = json.loads(response.read().decode("utf-8"))
    translated = "".join(part[0] for part in payload[0] if part and part[0])
    parts = [part.strip() for part in translated.split(SEP.strip())]
    if len(parts) != len(items):
        if len(items) == 1:
            return [translated.strip()]
        out: list[str] = []
        for item in items:
            out.extend(translate_batch([item], sl=sl, tl=tl))
            time.sleep(0.03)
        return out
    return parts


def parse_prompts(md_path: Path) -> list[str]:
    prompts: list[str] = []
    lines = md_path.read_text(encoding="utf-8").splitlines()
    current: str | None = None
    for line in lines:
        if line.startswith("### "):
            if current is not None:
                prompts.append(current.strip())
            current = line[4:].strip()
            continue
        if current is None:
            continue
        if CHOICE_SKIP_PATTERN.match(line):
            continue
        if "Back to Top" in line:
            continue
        if line.strip().startswith("!["):
            continue
        if not line.strip():
            continue
        if line.startswith(" ") or line.startswith("\t"):
            continue
        current += "\n" + line.strip()
    if current is not None:
        prompts.append(current.strip())
    return prompts


def parse_choices(md_path: Path) -> list[dict]:
    questions: list[list[str]] = []
    current_choices: list[str] = []
    current_choice: str | None = None

    for raw in md_path.read_text(encoding="utf-8").splitlines():
        line = raw.rstrip()
        if QUESTION_PATTERN.match(line):
            if current_choices:
                questions.append(current_choices)
            current_choices = []
            current_choice = None
            continue

        m = CHOICE_LINE_PATTERN.match(line)
        if m:
            current_choice = m.group("text").strip()
            current_choices.append(current_choice)
            continue

        if current_choice is not None and (raw.startswith(" ") or raw.startswith("\t")) and line.strip():
            current_choices[-1] = current_choices[-1] + "\n" + line.strip()
            continue

        if "Back to Top" in line:
            current_choice = None

    if current_choices:
        questions.append(current_choices)

    if len(questions) != EXPECTED_QUESTIONS:
        raise RuntimeError(f"Expected {EXPECTED_QUESTIONS} questions but got {len(questions)}")

    rows: list[dict] = []
    for q_idx, choices in enumerate(questions, start=1):
        if len(choices) < 2:
            raise RuntimeError(f"Question {q_idx} has fewer than 2 choices")
        for c_idx, text in enumerate(choices, start=1):
            rows.append({"questionSortOrder": q_idx, "choiceSortOrder": c_idx, "text": text})
    return rows


def protect_prompt(text: str) -> tuple[str, dict[str, str]]:
    replacements: dict[str, str] = {}

    def add_marker(value: str) -> str:
        marker = f"__M{len(replacements):04d}__"
        replacements[marker] = value
        return marker

    protected = CODE_PATTERN.sub(lambda m: add_marker(m.group(0)), text)
    for eng, kor in CORE_TERMS:
        pattern = re.compile(r"(?<![A-Za-z0-9_])" + re.escape(eng) + r"(?![A-Za-z0-9_])")
        protected = pattern.sub(lambda m: add_marker(f"{kor}({m.group(0)})"), protected)
    return protected, replacements


def restore_prompt(text: str, replacements: dict[str, str]) -> str:
    out = text
    for marker, value in replacements.items():
        out = out.replace(marker, value)
    out = out.replace(" ?", "?").replace(" !", "!").replace(" .", ".").replace(" ,", ",")
    out = re.sub(r"\s+\n", "\n", out)
    out = re.sub(r"[ \t]{2,}", " ", out)
    out = out.replace("아마존 AWS", "AWS")
    out = out.replace("A Developer", "개발자")
    out = out.replace("What should", "무엇을 해야")
    return out.strip()


def protect_choice(text: str) -> tuple[str, dict[str, str]]:
    repl: dict[str, str] = {}

    def marker(value: str) -> str:
        key = f"__M{len(repl):04d}__"
        repl[key] = value
        return key

    t = CODE_PATTERN.sub(lambda m: marker(m.group(0)), text)
    for eng, kor in CORE_TERMS:
        pattern = re.compile(r"(?<![A-Za-z0-9_])" + re.escape(eng) + r"(?![A-Za-z0-9_])")
        t = pattern.sub(lambda m: marker(f"{kor}({m.group(0)})"), t)
    return t, repl


def restore_choice(text: str, repl: dict[str, str]) -> str:
    for k, v in repl.items():
        text = text.replace(k, v)
    text = text.replace(" ?", "?").replace(" !", "!").replace(" .", ".").replace(" ,", ",")
    text = re.sub(r"[ \t]{2,}", " ", text)
    return text.strip()


def write_prompts_json(
    root: Path,
    prompts: list[str],
    *,
    expected: int | None = None,
    out_relative: str = "prompts.retranslated.json",
    source_lang: str = "en",
) -> None:
    exp = EXPECTED_QUESTIONS if expected is None else expected
    if len(prompts) != exp:
        raise RuntimeError(f"Expected {exp} prompts but got {len(prompts)}")

    protected: list[str] = []
    replacement_list: list[dict[str, str]] = []
    for prompt in prompts:
        text, replacements = protect_prompt(prompt)
        protected.append(text)
        replacement_list.append(replacements)

    translated: list[str] = []
    batch_size = 15
    total = len(protected)
    for i in range(0, total, batch_size):
        batch = protected[i : i + batch_size]
        translated.extend(translate_batch(batch, sl=source_lang))
        print(f"translated prompts {min(i + batch_size, total)}/{total}", flush=True)
        time.sleep(0.08)

    restored = [restore_prompt(t, reps) for t, reps in zip(translated, replacement_list)]
    out = [{"sortOrder": i + 1, "prompt": text} for i, text in enumerate(restored)]
    out_path = root / "data" / out_relative
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(json.dumps(out, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {out_path.relative_to(root)}", flush=True)


def write_choices_json(
    root: Path,
    rows: list[dict],
    *,
    expected: int | None = None,
    out_relative: str = "choices.retranslated.json",
    source_lang: str = "en",
) -> None:
    exp = EXPECTED_CHOICES if expected is None else expected
    if len(rows) != exp:
        raise RuntimeError(f"Expected {exp} choices but got {len(rows)}")

    protected: list[str] = []
    repls: list[dict[str, str]] = []
    for row in rows:
        p, r = protect_choice(row["text"])
        protected.append(p)
        repls.append(r)

    translated: list[str] = []
    batch_size = 18
    for i in range(0, len(protected), batch_size):
        translated.extend(translate_batch(protected[i : i + batch_size], sl=source_lang))
        print(f"translated choices {min(i + batch_size, len(protected))}/{len(protected)}", flush=True)
        time.sleep(0.08)

    out = []
    for row, t, r in zip(rows, translated, repls):
        out.append(
            {
                "questionSortOrder": row["questionSortOrder"],
                "choiceSortOrder": row["choiceSortOrder"],
                "text": restore_choice(t, r),
            }
        )
    out_path = root / "data" / out_relative
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(json.dumps(out, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {out_path.relative_to(root)}", flush=True)


def _clip_seed_text(s: str, max_len: int) -> str:
    if len(s) <= max_len:
        return s
    return s[: max_len - 1] + "…"


def enrich_associate_explanations(questions: list, prompts_en: list[str]) -> None:
    """
    exam.md에는 문항별 서술형 해설이 없으므로, 영어 스템 + 정답 보기를 묶어
    문항 explanation·선지 rationale(정답 확인용)을 채웁니다.
    """
    for qi, q in enumerate(questions):
        prompt_en = prompts_en[qi].strip()
        chs = q.get("choices")
        if not isinstance(chs, list):
            continue
        letters_correct = [chr(ord("A") + j) for j, c in enumerate(chs) if c.get("correct")]
        lab = ", ".join(letters_correct)
        correct_bullets = "\n".join(
            f"- ({chr(ord('A') + j)}) {c['text']}" for j, c in enumerate(chs) if c.get("correct")
        )
        q["explanation"] = _clip_seed_text(
            "[해설] 영어 원문 시험입니다. 정답은 공개 키 기준입니다.\n\n"
            "[Scenario]\n"
            + prompt_en
            + "\n\n[정답 보기]\n"
            + correct_bullets
            + "\n\n위 정답 보기가 질문에서 제시한 요구(서비스·제약·운영 목표)에 가장 잘 맞습니다.",
            12_000,
        )
        for j, c in enumerate(chs):
            letter = chr(ord("A") + j)
            txt = c.get("text") or ""
            if c.get("correct"):
                c["rationale"] = _clip_seed_text(
                    f"({letter}) 정답입니다. 질문 조건에 부합하는 선택이며, 보기 내용은 다음과 같습니다.\n\n{txt}",
                    7900,
                )
            else:
                c["rationale"] = _clip_seed_text(
                    f"({letter}) 오답입니다. 공개 정답은 {lab}번입니다. "
                    f"이 보기는 요구사항을 가장 잘 충족하지 않습니다.\n\n[선택한 보기]\n{txt}",
                    7900,
                )


def write_seed_import_from_md(root: Path) -> None:
    """exam.md 영어 본문 + 기존 translated JSON의 정답 플래그로 임포트용 시드 JSON을 만듭니다."""
    md = root / "src" / "main" / "resources" / "exam.md"
    template_path = root / "data" / "exam-import.translated.json"
    if not md.is_file():
        raise SystemExit(f"소스 마크다운이 없습니다: {md}")
    if not template_path.is_file():
        raise SystemExit(
            "data/exam-import.translated.json 이 필요합니다(문항 수·정답 구조 템플릿). "
            "저장소에 포함된 파일을 유지하거나 복원하세요."
        )

    prompts = parse_prompts(md)
    rows = parse_choices(md)
    if len(prompts) != EXPECTED_QUESTIONS or len(rows) != EXPECTED_CHOICES:
        raise RuntimeError(
            f"exam.md 파싱 결과가 기대와 다릅니다: prompts={len(prompts)} choices={len(rows)}"
        )

    by_q: dict[int, list[str]] = defaultdict(list)
    for r in sorted(rows, key=lambda x: (x["questionSortOrder"], x["choiceSortOrder"])):
        by_q[r["questionSortOrder"]].append(r["text"])

    data = json.loads(template_path.read_text(encoding="utf-8"))
    questions = data.get("questions")
    if not isinstance(questions, list) or len(questions) != len(prompts):
        raise RuntimeError("템플릿 questions 길이가 exam.md와 일치하지 않습니다.")

    for qi, q in enumerate(questions):
        q["prompt"] = prompts[qi]
        texts = by_q[qi + 1]
        chs = q.get("choices")
        if not isinstance(chs, list) or len(chs) != len(texts):
            raise RuntimeError(f"문항 {qi + 1}: 선택지 개수 불일치 template={len(chs)} md={len(texts)}")
        for j, c in enumerate(chs):
            c["text"] = texts[j]

    enrich_associate_explanations(questions, prompts)

    out_path = root / "data" / "exam-import.seed.json"
    out_path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {out_path.relative_to(root)}", flush=True)


def run_translate_exam2(root: Path) -> None:
    """data/exam2-import.json 문항·선지를 자동 감지 원어(sl=auto) 기준 한국어로 번역합니다."""
    imp = root / "data" / "exam2-import.json"
    if not imp.is_file():
        raise SystemExit(f"먼저 생성하세요: python3 scripts/parse_exam2_to_import.py ({imp})")

    data = json.loads(imp.read_text(encoding="utf-8"))
    questions = data.get("questions")
    if not isinstance(questions, list) or not questions:
        raise SystemExit("exam2-import.json 에 questions 가 없습니다.")

    prompts = [q["prompt"] for q in questions]
    rows: list[dict] = []
    for qi, q in enumerate(questions, start=1):
        chs = q.get("choices")
        if not isinstance(chs, list):
            raise SystemExit(f"문항 {qi}: choices 형식 오류")
        for ci, ch in enumerate(chs, start=1):
            rows.append({"questionSortOrder": qi, "choiceSortOrder": ci, "text": ch["text"]})

    write_prompts_json(
        root,
        prompts,
        expected=len(prompts),
        out_relative="exam2-prompts.retranslated.json",
        source_lang="auto",
    )
    write_choices_json(
        root,
        rows,
        expected=len(rows),
        out_relative="exam2-choices.retranslated.json",
        source_lang="auto",
    )


def find_postgresql_jar() -> Path | None:
    roots: list[Path] = []
    gh = os.environ.get("GRADLE_USER_HOME")
    if gh:
        roots.append(Path(gh))
    roots.extend([Path.home() / ".gradle", Path("/tmp/gradle")])

    jars: list[Path] = []
    for base in roots:
        if not base.is_dir():
            continue
        try:
            for p in base.rglob("postgresql-*.jar"):
                if "sources" in p.name:
                    continue
                normalized = str(p).replace("\\", "/")
                if "/org.postgresql/postgresql/" not in normalized:
                    continue
                jars.append(p)
        except OSError:
            continue
    if not jars:
        return None
    return max(jars, key=lambda p: (p.stat().st_mtime, len(str(p))))


def apply_db(root: Path) -> None:
    pg = find_postgresql_jar()
    if not pg:
        raise SystemExit(
            "PostgreSQL JDBC jar를 찾을 수 없습니다. "
            "한 번 `sh gradlew compileJava` 또는 의존성 다운로드를 실행한 뒤 다시 시도하세요."
        )

    out_dir = root / "build" / "script-classes"
    out_dir.mkdir(parents=True, exist_ok=True)
    sources = [root / "scripts" / "UpdateExamTranslatedTexts.java"]
    subprocess.run(
        ["javac", "-encoding", "UTF-8", "-cp", str(pg), "-d", str(out_dir)]
        + [str(s) for s in sources],
        cwd=str(root),
        check=True,
    )
    cp = f"{out_dir}:{pg}"
    jobs: list[tuple[str, str, str]] = [
        (
            "AWS Developer Associate Practice Exam - Korean",
            "data/prompts.retranslated.json",
            "data/choices.retranslated.json",
        ),
        (
            "AWS Developer Practice Exam 2 (Parsed)",
            "data/exam2-prompts.retranslated.json",
            "data/exam2-choices.retranslated.json",
        ),
    ]
    for title, pj, cj in jobs:
        p_path = root / pj
        c_path = root / cj
        if not p_path.is_file() or not c_path.is_file():
            print(f"skip DB update (missing files): {pj} / {cj}", flush=True)
            continue
        print(f"running UpdateExamTranslatedTexts for {title!r}...", flush=True)
        subprocess.run(
            ["java", "-cp", cp, "UpdateExamTranslatedTexts", title, str(p_path), str(c_path)],
            cwd=str(root),
            check=True,
        )


def run_translate(root: Path) -> None:
    md = root / "src" / "main" / "resources" / "exam.md"
    if not md.is_file():
        raise SystemExit(f"소스 마크다운이 없습니다: {md}")

    prompts = parse_prompts(md)
    rows = parse_choices(md)
    if len(rows) != EXPECTED_CHOICES:
        raise RuntimeError(f"Expected {EXPECTED_CHOICES} choices but got {len(rows)}")

    write_prompts_json(root, prompts)
    write_choices_json(root, rows)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="exam.md·exam2 한국어 재번역 + JSON 저장 + (기본) DB 반영을 한 번에 처리합니다."
    )
    parser.add_argument(
        "--json-only",
        action="store_true",
        help="번역·JSON만 수행하고 PostgreSQL 업데이트는 건너뜁니다.",
    )
    parser.add_argument(
        "--db-only",
        action="store_true",
        help="data/*.retranslated.json 으로 DB만 갱신합니다(있는 시험 JSON만 반영).",
    )
    parser.add_argument(
        "--write-seed-import",
        action="store_true",
        help="exam.md + data/exam-import.translated.json → data/exam-import.seed.json 만 생성합니다.",
    )
    parser.add_argument(
        "--skip-exam2",
        action="store_true",
        help="Associate(exam.md)만 번역·반영하고 Exam2는 건너뜁니다.",
    )
    parser.add_argument(
        "--root",
        type=Path,
        default=None,
        help="저장소 루트(기본: 이 스크립트의 상위 디렉터리)",
    )
    args = parser.parse_args()
    if args.json_only and args.db_only:
        parser.error("--json-only 와 --db-only 는 함께 쓸 수 없습니다.")
    if args.write_seed_import and (args.json_only or args.db_only):
        parser.error("--write-seed-import 는 단독으로 사용하세요.")

    root = (args.root or Path(__file__).resolve().parent.parent).resolve()

    if args.write_seed_import:
        write_seed_import_from_md(root)
        return

    if args.db_only:
        apply_db(root)
        return

    run_translate(root)
    if not args.skip_exam2:
        run_translate_exam2(root)
    if not args.json_only:
        apply_db(root)


if __name__ == "__main__":
    main()
