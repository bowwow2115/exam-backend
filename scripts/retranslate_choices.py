#!/usr/bin/env python3
import json
import re
import time
import urllib.parse
import urllib.request
from pathlib import Path


TRANSLATE_URL = "https://translate.googleapis.com/translate_a/single"
SEP = "\n@@@SEP@@@\n"

CORE_TERMS = [
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

QUESTION_PATTERN = re.compile(r"^### ")
CHOICE_PATTERN = re.compile(r"^- \[[ x]\] (?P<text>.*)$")
CODE_PATTERN = re.compile(r"`[^`]*`|https?://[^\s`]+|s3://[^\s`]+", re.IGNORECASE)


def parse_choices(md_path: Path):
    questions = []
    current_choices = []
    current_choice = None

    for raw in md_path.read_text(encoding="utf-8").splitlines():
        line = raw.rstrip()
        if QUESTION_PATTERN.match(line):
            if current_choices:
                questions.append(current_choices)
            current_choices = []
            current_choice = None
            continue

        m = CHOICE_PATTERN.match(line)
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

    if len(questions) != 387:
        raise RuntimeError(f"Expected 387 questions but got {len(questions)}")

    rows = []
    for q_idx, choices in enumerate(questions, start=1):
        if len(choices) < 2:
            raise RuntimeError(f"Question {q_idx} has fewer than 2 choices")
        for c_idx, text in enumerate(choices, start=1):
            rows.append({"questionSortOrder": q_idx, "choiceSortOrder": c_idx, "text": text})
    return rows


def protect(text: str):
    repl = {}

    def marker(value):
        key = f"__M{len(repl):04d}__"
        repl[key] = value
        return key

    text = CODE_PATTERN.sub(lambda m: marker(m.group(0)), text)
    for eng, kor in sorted(CORE_TERMS, key=lambda x: len(x[0]), reverse=True):
        pattern = re.compile(r"(?<![A-Za-z0-9_])" + re.escape(eng) + r"(?![A-Za-z0-9_])")
        text = pattern.sub(lambda m: marker(f"{kor}({m.group(0)})"), text)
    return text, repl


def restore(text: str, repl):
    for k, v in repl.items():
        text = text.replace(k, v)
    text = text.replace(" ?", "?").replace(" !", "!").replace(" .", ".").replace(" ,", ",")
    text = re.sub(r"[ \t]{2,}", " ", text)
    return text.strip()


def translate_batch(items):
    query = urllib.parse.urlencode({
        "client": "gtx",
        "sl": "en",
        "tl": "ko",
        "dt": "t",
        "q": SEP.join(items),
    })
    url = f"{TRANSLATE_URL}?{query}"
    with urllib.request.urlopen(url, timeout=30) as resp:
        payload = json.loads(resp.read().decode("utf-8"))
    translated = "".join(part[0] for part in payload[0] if part and part[0])
    parts = [p.strip() for p in translated.split(SEP.strip())]
    if len(parts) != len(items):
        if len(items) == 1:
            return [translated.strip()]
        out = []
        for i in items:
            out.extend(translate_batch([i]))
            time.sleep(0.03)
        return out
    return parts


def main():
    rows = parse_choices(Path("src/main/resources/exam.md"))
    protected = []
    repls = []
    for row in rows:
        p, r = protect(row["text"])
        protected.append(p)
        repls.append(r)

    translated = []
    batch_size = 18
    for i in range(0, len(protected), batch_size):
        translated.extend(translate_batch(protected[i:i + batch_size]))
        print(f"translated choices {min(i + batch_size, len(protected))}/{len(protected)}", flush=True)
        time.sleep(0.08)

    out = []
    for row, t, r in zip(rows, translated, repls):
        out.append({
            "questionSortOrder": row["questionSortOrder"],
            "choiceSortOrder": row["choiceSortOrder"],
            "text": restore(t, r),
        })

    Path("data").mkdir(parents=True, exist_ok=True)
    Path("data/choices.retranslated.json").write_text(
        json.dumps(out, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print("wrote data/choices.retranslated.json")


if __name__ == "__main__":
    main()
