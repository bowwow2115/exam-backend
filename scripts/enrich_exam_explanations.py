#!/usr/bin/env python3
import json
import re
import sys
from pathlib import Path


LABELS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"

SERVICE_HINTS = [
    (("elasticache", "엘라스티캐시", "redis", "memcached"), "ElastiCache", "인메모리 캐시로 읽기 지연 시간을 줄이고 반복 조회 부하를 데이터베이스에서 분리하는 데 적합합니다."),
    (("dynamodb", "다이나모db", "다이나모디비"), "DynamoDB", "완전관리형 키-값/문서 데이터베이스로 짧은 지연 시간, 자동 확장, 조건부 쓰기, TTL, 스트림 같은 서버리스 데이터 처리에 적합합니다."),
    (("s3", "simple storage service", "오브젝트", "버킷"), "Amazon S3", "객체 스토리지 서비스로 정적 파일, 대용량 객체, 이벤트 알림, 수명 주기 관리, 암호화 저장에 적합합니다."),
    (("sns", "simple notification service"), "Amazon SNS", "pub/sub 메시징과 구독 필터 정책으로 여러 소비자에게 메시지를 팬아웃하거나 조건별 라우팅할 때 적합합니다."),
    (("sqs", "simple queue service"), "Amazon SQS", "비동기 작업 대기열로 생산자와 소비자를 분리하고 재시도, DLQ, 순서 보장(FIFO)을 처리할 때 적합합니다."),
    (("lambda", "람다"), "AWS Lambda", "서버 관리 없이 이벤트 기반 코드를 실행하며 짧은 작업, 자동 확장, 서비스 이벤트 처리에 적합합니다."),
    (("sam ", "sam cli", "serverless application model"), "AWS SAM", "서버리스 애플리케이션을 로컬 테스트, 패키징, 배포하기 위한 프레임워크와 CLI를 제공합니다."),
    (("api gateway", "api게이트웨이"), "API Gateway", "HTTP/API 엔드포인트, 인증, throttling, Lambda 프록시 연동을 관리하는 진입점입니다."),
    (("eventbridge", "cloudwatch events"), "Amazon EventBridge", "이벤트 버스와 규칙 기반 라우팅으로 서비스 이벤트를 느슨하게 연결할 때 적합합니다."),
    (("kinesis",), "Amazon Kinesis", "실시간 스트림 수집·처리, 샤드 기반 처리량, 순서 보존이 필요한 데이터 스트림에 적합합니다."),
    (("rds", "relational database", "관계형"), "Amazon RDS", "관리형 관계형 데이터베이스로 트랜잭션, SQL, Multi-AZ, 읽기 복제본 같은 기능이 필요한 경우 적합합니다."),
    (("aurora",), "Amazon Aurora", "AWS 관리형 관계형 데이터베이스로 고성능, 복제, 서버리스 옵션, 자동 백업이 필요한 워크로드에 적합합니다."),
    (("cognito",), "Amazon Cognito", "사용자 가입, 로그인, 토큰 발급, ID 페더레이션을 관리하는 인증 서비스입니다."),
    (("iam", "identity and access management"), "AWS IAM", "사용자, 역할, 정책으로 권한을 최소 권한 원칙에 맞게 제어합니다."),
    (("kms", "key management service"), "AWS KMS", "관리형 키 생성·보관·사용 권한 제어를 통해 암호화 키를 중앙에서 관리합니다."),
    (("secrets manager",), "AWS Secrets Manager", "DB 비밀번호 같은 보안 정보를 저장하고 자동 교체가 필요할 때 적합합니다."),
    (("parameter store", "systems manager parameter"), "SSM Parameter Store", "구성 값과 보안 문자열을 중앙 저장하고 애플리케이션에서 조회할 때 적합합니다."),
    (("cloudwatch",), "Amazon CloudWatch", "메트릭, 로그, 알람, 대시보드로 운영 상태를 관찰하고 경보를 생성합니다."),
    (("x-ray", "xray"), "AWS X-Ray", "분산 추적으로 요청 흐름, 지연 구간, 서비스 간 호출 문제를 분석합니다."),
    (("step functions", "스텝 함수"), "AWS Step Functions", "여러 작업을 상태 머신으로 오케스트레이션하고 재시도, 분기, 오류 처리를 선언적으로 구성합니다."),
    (("codedeploy",), "AWS CodeDeploy", "EC2, ECS, Lambda 배포 자동화와 blue/green, canary, linear 배포 전략을 제공합니다."),
    (("codecommit",), "AWS CodeCommit", "Git 저장소 서비스이며 애플리케이션 런타임 성능이나 데이터 처리 문제를 직접 해결하지 않습니다."),
    (("codebuild",), "AWS CodeBuild", "소스 빌드와 테스트를 수행하는 CI 빌드 서비스입니다."),
    (("codepipeline",), "AWS CodePipeline", "소스, 빌드, 배포 단계를 연결하는 릴리스 자동화 서비스입니다."),
    (("cloudfront",), "Amazon CloudFront", "전 세계 엣지 캐시와 TLS 종료로 정적/동적 콘텐츠 전송 지연 시간을 줄입니다."),
    (("route 53", "route53"), "Amazon Route 53", "DNS, 상태 확인, 라우팅 정책을 제공하며 애플리케이션 내부 처리 로직을 대체하지 않습니다."),
    (("cloudformation",), "AWS CloudFormation", "인프라를 템플릿으로 선언하고 반복 가능하게 프로비저닝합니다."),
    (("cdk", "cloud development kit"), "AWS CDK", "프로그래밍 언어로 인프라를 정의하고 CloudFormation으로 합성합니다."),
    (("ecr", "elastic container registry"), "Amazon ECR", "컨테이너 이미지를 저장하고 배포 파이프라인에서 참조하는 레지스트리입니다."),
    (("ecs", "elastic container service"), "Amazon ECS", "컨테이너 오케스트레이션 서비스로 태스크와 서비스를 관리합니다."),
    (("eks", "kubernetes"), "Amazon EKS", "관리형 Kubernetes 제어 플레인을 제공합니다."),
    (("elastic beanstalk", "beanstalk"), "AWS Elastic Beanstalk", "애플리케이션 배포 환경을 관리하지만 세부 인프라 제어는 제한됩니다."),
    (("opensearch", "elasticsearch"), "Amazon OpenSearch Service", "검색, 로그 분석, 텍스트 질의가 필요한 워크로드에 적합합니다."),
    (("appsync", "graphql"), "AWS AppSync", "관리형 GraphQL API와 실시간 구독, 데이터 소스 연동을 제공합니다."),
]

CONSTRAINT_HINTS = [
    (("least operational", "operational overhead", "운영 오버헤드", "운영 부담", "최소한의 운영"), "운영 오버헤드를 줄여야 하므로 관리형 기능이나 서비스 내장 기능을 우선해야 합니다."),
    (("least development", "minimum development", "최소한의 개발", "개발 노력"), "개발 노력을 최소화해야 하므로 직접 구현보다 AWS가 제공하는 기본 기능을 선택해야 합니다."),
    (("highly available", "고가용", "가용성"), "고가용성이 요구되므로 단일 장애 지점이 생기는 구성보다 관리형·다중 AZ 구성이 적합합니다."),
    (("cost", "비용", "least expensive", "가장 저렴"), "비용 조건이 있으므로 불필요한 상시 실행 리소스나 과도한 서비스를 피해야 합니다."),
    (("latency", "지연", "throughput", "처리량", "performance", "성능"), "성능 조건이 있으므로 지연 시간을 줄이거나 처리량 병목을 제거하는 선택지가 유리합니다."),
    (("secure", "security", "암호화", "보안", "least privilege", "최소 권한"), "보안 요구가 있으므로 최소 권한, 관리형 암호화, 안전한 비밀 정보 관리가 중요합니다."),
    (("local", "로컬"), "로컬 테스트 요구가 있으므로 클라우드 배포 없이 CLI나 로컬 에뮬레이션으로 검증할 수 있어야 합니다."),
    (("route", "routing", "filter", "라우팅", "필터"), "조건별 라우팅 요구가 있으므로 메시지 속성이나 이벤트 패턴을 서비스가 직접 필터링하는 구성이 적합합니다."),
    (("real-time", "실시간", "stream", "스트림"), "실시간 처리 요구가 있으므로 스트림 수집과 병렬 소비를 지원하는 서비스가 적합합니다."),
    (("retry", "dlq", "dead-letter", "재시도", "데드레터"), "실패 처리 요구가 있으므로 재시도와 DLQ를 서비스 수준에서 제공하는 구성이 적합합니다."),
]


def normalized(text):
    return (text or "").casefold()


def labels_for(question):
    labels = []
    for idx, choice in enumerate(question.get("choices") or []):
        if choice.get("correct") is True:
            labels.append(LABELS[idx])
    return labels


def service_notes(text):
    low = normalized(text)
    notes = []
    seen = set()
    for keys, name, note in SERVICE_HINTS:
        if any(k in low for k in keys) and name not in seen:
            notes.append((name, note))
            seen.add(name)
    return notes


def constraint_notes(prompt):
    low = normalized(prompt)
    notes = []
    for keys, note in CONSTRAINT_HINTS:
        if any(k in low for k in keys):
            notes.append(note)
    return notes[:3]


def strip_generic(text):
    value = (text or "").strip()
    generic_markers = [
        "이 보기는 요구사항을 가장 잘 충족하지 않습니다",
        "시나리오와 제약을 고려할 때 최선의 솔루션이 아닙니다",
        "공개 정답은",
        "영어 원문 시험입니다",
        "질문 조건에 부합하는 선택",
    ]
    if any(marker in value for marker in generic_markers):
        return ""
    return value


def sentence_join(parts):
    cleaned = []
    for part in parts:
        text = re.sub(r"\s+", " ", (part or "").strip())
        if text:
            cleaned.append(text)
    return " ".join(cleaned)


def dedupe_sentences(text):
    value = re.sub(r"\s+", " ", (text or "").strip())
    if not value:
        return ""
    pieces = re.split(r"(?:(?<=[.!?])|(?<=다\.))\s+", value)
    result = []
    seen = set()
    for piece in pieces:
        key = piece.strip()
        if not key or key in seen:
            continue
        result.append(key)
        seen.add(key)
    return " ".join(result)


def correct_reason(choice, prompt, existing):
    choice_text = choice.get("text") or ""
    existing = strip_generic(existing)
    if existing.startswith("정답입니다."):
        return dedupe_sentences(existing)
    if existing:
        base = existing
    else:
        notes = service_notes(choice_text) or service_notes(prompt)
        if notes:
            base = " ".join(note for _, note in notes[:2])
        else:
            base = "문제에서 요구한 조건과 제약을 직접 만족하는 선택지입니다."
    constraints = constraint_notes(prompt)
    return dedupe_sentences(sentence_join([
        "정답입니다.",
        base,
        " ".join(constraints[:2]),
        "따라서 이 보기는 문제의 핵심 요구사항을 가장 직접적으로 충족합니다.",
    ]))


def wrong_reason(choice, question, idx, correct_labels, correct_choices):
    choice_text = choice.get("text") or ""
    existing = strip_generic(choice.get("rationale"))
    if existing.startswith("오답입니다."):
        return dedupe_sentences(existing)
    if existing:
        base = existing
    else:
        choice_services = service_notes(choice_text)
        correct_services = []
        for correct_choice in correct_choices:
            correct_services.extend(service_notes(correct_choice.get("text") or ""))
        correct_names = {name for name, _ in correct_services}

        if choice_services:
            service_name, service_note = choice_services[0]
            if service_name not in correct_names:
                base = f"{service_name} 자체의 용도는 {service_note} 하지만 이 문제의 조건에서 요구한 핵심 기능을 직접 제공하지 못합니다."
            else:
                base = f"{service_name}를 언급하지만, 선택지의 구성 방식이 문제의 제약을 만족하는 정답 구성과 다릅니다."
        else:
            base = "보기의 접근 방식은 문제의 핵심 조건을 직접 해결하지 못하거나 불필요한 구현·운영 부담을 추가합니다."

    labels = ", ".join(correct_labels) if correct_labels else "표시된 정답"
    constraints = " ".join(constraint_notes(question.get("prompt") or "")[:2])
    return dedupe_sentences(sentence_join([
        "오답입니다.",
        f"정답은 {labels}번입니다.",
        base,
        constraints,
        "따라서 이 보기는 정답 보기보다 요구사항 적합성이 낮습니다.",
    ]))


def question_explanation(question):
    prompt = question.get("prompt") or ""
    correct_labels = labels_for(question)
    choices = question.get("choices") or []
    correct_choices = [c for c in choices if c.get("correct") is True]
    labels_text = ", ".join(correct_labels) if correct_labels else "없음"

    constraints = constraint_notes(prompt)
    prompt_services = service_notes(prompt)
    correct_lines = []
    wrong_lines = []

    for idx, choice in enumerate(choices):
        label = LABELS[idx]
        text = re.sub(r"\s+", " ", (choice.get("text") or "").strip())
        if choice.get("correct") is True:
            reason = dedupe_sentences(choice.get("rationale") or correct_reason(choice, prompt, ""))
            correct_lines.append(f"- ({label}) {text}: {reason}")
        else:
            reason = dedupe_sentences(choice.get("rationale") or wrong_reason(choice, question, idx, correct_labels, correct_choices))
            wrong_lines.append(f"- ({label}) {text}: {reason}")

    parts = [f"정답은 {labels_text}번입니다."]
    if constraints:
        parts.append("\n\n[핵심 판단]\n" + "\n".join(f"- {note}" for note in constraints))
    elif prompt_services:
        parts.append("\n\n[핵심 판단]\n" + "\n".join(f"- {name}: {note}" for name, note in prompt_services[:3]))
    else:
        parts.append("\n\n[핵심 판단]\n- 문제의 요구사항, 제약 조건, 운영 부담을 기준으로 가장 직접적인 AWS 관리형 기능을 선택해야 합니다.")

    parts.append("\n\n[정답 해설]\n" + "\n".join(correct_lines))
    if wrong_lines:
        parts.append("\n\n[오답 해설]\n" + "\n".join(wrong_lines))
    return "".join(parts)


def enrich_file(path):
    data = json.loads(path.read_text(encoding="utf-8"))
    for question in data.get("questions") or []:
        correct_labels = labels_for(question)
        correct_choices = [c for c in question.get("choices") or [] if c.get("correct") is True]
        for idx, choice in enumerate(question.get("choices") or []):
            if choice.get("correct") is True:
                choice["rationale"] = correct_reason(choice, question.get("prompt") or "", choice.get("rationale"))
            else:
                choice["rationale"] = wrong_reason(choice, question, idx, correct_labels, correct_choices)
        question["explanation"] = question_explanation(question)

    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main():
    if len(sys.argv) < 2:
        raise SystemExit("Usage: enrich_exam_explanations.py <import-json> [<import-json>...]")
    for raw in sys.argv[1:]:
        path = Path(raw)
        enrich_file(path)
        print(f"enriched {path}")


if __name__ == "__main__":
    main()
