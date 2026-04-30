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

CHOICE_PATTERN = re.compile(r"^- \[[ x]\] ")
CODE_PATTERN = re.compile(r"`[^`]*`|https?://[^\s`]+|s3://[^\s`]+", re.IGNORECASE)


def parse_prompts(md_path: Path):
    prompts = []
    lines = md_path.read_text(encoding="utf-8").splitlines()
    current = None
    for line in lines:
        if line.startswith("### "):
            if current is not None:
                prompts.append(current.strip())
            current = line[4:].strip()
            continue
        if current is None:
            continue
        if CHOICE_PATTERN.match(line):
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


def protect(text: str):
    replacements = {}

    def add_marker(value: str):
        marker = f"__M{len(replacements):04d}__"
        replacements[marker] = value
        return marker

    def code_repl(match):
        return add_marker(match.group(0))

    protected = CODE_PATTERN.sub(code_repl, text)
    for eng, kor in sorted(CORE_TERMS, key=lambda x: len(x[0]), reverse=True):
        pattern = re.compile(r"(?<![A-Za-z0-9_])" + re.escape(eng) + r"(?![A-Za-z0-9_])")
        protected = pattern.sub(lambda m: add_marker(f"{kor}({m.group(0)})"), protected)
    return protected, replacements


def restore(text: str, replacements):
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


def translate_batch(items):
    query = urllib.parse.urlencode(
        {
            "client": "gtx",
            "sl": "en",
            "tl": "ko",
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
        out = []
        for item in items:
            out.extend(translate_batch([item]))
            time.sleep(0.03)
        return out
    return parts


def main():
    prompts = parse_prompts(Path("src/main/resources/exam.md"))
    if len(prompts) != 387:
        raise RuntimeError(f"Expected 387 prompts but got {len(prompts)}")

    protected = []
    replacement_list = []
    for prompt in prompts:
        text, replacements = protect(prompt)
        protected.append(text)
        replacement_list.append(replacements)

    translated = []
    batch_size = 15
    total = len(protected)
    for i in range(0, total, batch_size):
        batch = protected[i:i + batch_size]
        translated.extend(translate_batch(batch))
        print(f"translated prompts {min(i + batch_size, total)}/{total}", flush=True)
        time.sleep(0.08)

    restored = [restore(t, reps) for t, reps in zip(translated, replacement_list)]
    out = [{"sortOrder": i + 1, "prompt": text} for i, text in enumerate(restored)]
    Path("data/prompts.retranslated.json").parent.mkdir(parents=True, exist_ok=True)
    Path("data/prompts.retranslated.json").write_text(
        json.dumps(out, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print("wrote data/prompts.retranslated.json")


if __name__ == "__main__":
    main()
