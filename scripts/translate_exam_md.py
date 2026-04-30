#!/usr/bin/env python3
import argparse
import json
import re
import time
import urllib.parse
import urllib.request
from pathlib import Path


ITEM_SEPARATOR = "\n@@@ITEMSEP@@@\n"
TRANSLATE_URL = "https://translate.googleapis.com/translate_a/single"


GLOSSARY = [
    ("AWS Systems Manager Parameter Store", "에이더블유에스 시스템즈 매니저 파라미터 스토어"),
    ("Systems Manager Parameter Store", "시스템즈 매니저 파라미터 스토어"),
    ("Amazon Kinesis Data Firehose", "아마존 키네시스 데이터 파이어호스"),
    ("Amazon Kinesis Data Streams", "아마존 키네시스 데이터 스트림즈"),
    ("AWS Serverless Application Model", "에이더블유에스 서버리스 애플리케이션 모델"),
    ("Serverless Application Model", "서버리스 애플리케이션 모델"),
    ("Application Load Balancer", "애플리케이션 로드 밸런서"),
    ("Network Load Balancer", "네트워크 로드 밸런서"),
    ("Classic Load Balancer", "클래식 로드 밸런서"),
    ("Elastic Load Balancer", "엘라스틱 로드 밸런서"),
    ("AWS Management Console", "에이더블유에스 매니지먼트 콘솔"),
    ("AWS Elastic Beanstalk", "에이더블유에스 엘라스틱 빈스토크"),
    ("Amazon Elastic Beanstalk", "아마존 엘라스틱 빈스토크"),
    ("Amazon Simple Notification Service", "아마존 심플 노티피케이션 서비스"),
    ("Simple Notification Service", "심플 노티피케이션 서비스"),
    ("Amazon Simple Workflow Service", "아마존 심플 워크플로 서비스"),
    ("Simple Workflow Service", "심플 워크플로 서비스"),
    ("Amazon Simple Storage Service", "아마존 심플 스토리지 서비스"),
    ("Simple Storage Service", "심플 스토리지 서비스"),
    ("Amazon Simple Queue Service", "아마존 심플 큐 서비스"),
    ("Simple Queue Service", "심플 큐 서비스"),
    ("Amazon Elasticsearch Service", "아마존 엘라스틱서치 서비스"),
    ("Amazon OpenSearch Service", "아마존 오픈서치 서비스"),
    ("Kinesis Client Library", "키네시스 클라이언트 라이브러리"),
    ("Application Auto Scaling", "애플리케이션 오토 스케일링"),
    ("AWS Auto Scaling", "에이더블유에스 오토 스케일링"),
    ("Auto Scaling group", "오토 스케일링 그룹"),
    ("Auto Scaling", "오토 스케일링"),
    ("Amazon API Gateway", "아마존 에이피아이 게이트웨이"),
    ("Amazon CloudFront", "아마존 클라우드프론트"),
    ("Amazon CloudWatch Logs", "아마존 클라우드워치 로그"),
    ("Amazon CloudWatch", "아마존 클라우드워치"),
    ("Amazon CloudTrail", "아마존 클라우드트레일"),
    ("Amazon CodeGuru", "아마존 코드구루"),
    ("Amazon Cognito", "아마존 코그니토"),
    ("Amazon DynamoDB", "아마존 다이나모디비"),
    ("Amazon ElastiCache", "아마존 엘라스티캐시"),
    ("Amazon EventBridge", "아마존 이벤트브리지"),
    ("Amazon Kinesis", "아마존 키네시스"),
    ("Amazon Redshift", "아마존 레드시프트"),
    ("Amazon Route 53", "아마존 루트 53"),
    ("Amazon Aurora", "아마존 오로라"),
    ("Amazon Linux", "아마존 리눅스"),
    ("Amazon RDS", "아마존 알디에스"),
    ("Amazon EBS", "아마존 이비에스"),
    ("Amazon EC2", "아마존 이씨투"),
    ("Amazon ECR", "아마존 이씨알"),
    ("Amazon ECS", "아마존 이씨에스"),
    ("Amazon EFS", "아마존 이애프에스"),
    ("Amazon EKS", "아마존 이케이에스"),
    ("Amazon EMR", "아마존 이엠알"),
    ("Amazon S3", "아마존 에스쓰리"),
    ("Amazon SES", "아마존 에스이에스"),
    ("Amazon SNS", "아마존 에스엔에스"),
    ("Amazon SQS", "아마존 에스큐에스"),
    ("AWS AppSync", "에이더블유에스 앱싱크"),
    ("AWS CloudFormation", "에이더블유에스 클라우드포메이션"),
    ("AWS CodeBuild", "에이더블유에스 코드빌드"),
    ("AWS CodeCommit", "에이더블유에스 코드커밋"),
    ("AWS CodeDeploy", "에이더블유에스 코드디플로이"),
    ("AWS CodePipeline", "에이더블유에스 코드파이프라인"),
    ("AWS CodeStar", "에이더블유에스 코드스타"),
    ("AWS Lambda", "에이더블유에스 람다"),
    ("AWS Secrets Manager", "에이더블유에스 시크릿츠 매니저"),
    ("AWS Step Functions", "에이더블유에스 스텝 펑션즈"),
    ("AWS X-Ray", "에이더블유에스 엑스레이"),
    ("AWS CLI", "에이더블유에스 씨엘아이"),
    ("AWS KMS", "에이더블유에스 케이엠에스"),
    ("AWS SAM", "에이더블유에스 샘"),
    ("AWS SDK", "에이더블유에스 에스디케이"),
    ("AWS STS", "에이더블유에스 에스티에스"),
    ("AWS", "에이더블유에스"),
    ("API Gateway", "에이피아이 게이트웨이"),
    ("CloudFront", "클라우드프론트"),
    ("CloudWatch Logs", "클라우드워치 로그"),
    ("CloudWatch", "클라우드워치"),
    ("CloudTrail", "클라우드트레일"),
    ("CodeBuild", "코드빌드"),
    ("CodeCommit", "코드커밋"),
    ("CodeDeploy", "코드디플로이"),
    ("CodePipeline", "코드파이프라인"),
    ("DynamoDB Streams", "다이나모디비 스트림즈"),
    ("DynamoDB stream", "다이나모디비 스트림"),
    ("DynamoDB", "다이나모디비"),
    ("Elastic Beanstalk", "엘라스틱 빈스토크"),
    ("ElastiCache", "엘라스티캐시"),
    ("GenerateDataKey", "제너레이트데이터키"),
    ("Lambda function", "람다 펑션"),
    ("Lambda", "람다"),
    ("Parameter Store", "파라미터 스토어"),
    ("Route 53", "루트 53"),
    ("Secrets Manager", "시크릿츠 매니저"),
    ("Step Functions", "스텝 펑션즈"),
    ("X-Forwarded-For", "엑스 포워디드 포"),
    ("Cache-Control:max-age=0", "캐시 컨트롤 맥스 에이지 0"),
    ("Content-MD5", "콘텐츠 엠디파이브"),
    ("JSON Web Token", "제이슨 웹 토큰"),
    ("OpenID", "오픈아이디"),
    ("Base64", "베이스육십사"),
    ("BCrypt", "비크립트"),
    ("FIFO", "파이포"),
    ("SigV4", "시그브이포"),
    ("signature version 4", "시그니처 버전 포"),
    ("Server-side encryption", "서버 사이드 인크립션"),
    ("server-side encryption", "서버 사이드 인크립션"),
    ("Client-side encryption", "클라이언트 사이드 인크립션"),
    ("client-side encryption", "클라이언트 사이드 인크립션"),
    ("S3-managed keys", "에스쓰리 매니지드 키즈"),
    ("KMS-managed keys", "케이엠에스 매니지드 키즈"),
    ("KMS-managed key", "케이엠에스 매니지드 키"),
    ("Customer Master Key", "커스터머 마스터 키"),
    ("customer master key", "커스터머 마스터 키"),
    ("master key", "마스터 키"),
    ("managed key", "매니지드 키"),
    ("access key id", "액세스 키 아이디"),
    ("secret access key", "시크릿 액세스 키"),
    ("access key", "액세스 키"),
    ("execution role", "익스큐션 롤"),
    ("IAM role", "아이에이엠 롤"),
    ("IAM user", "아이에이엠 유저"),
    ("IAM", "아이에이엠"),
    ("ARN", "에이알엔"),
    ("AMI", "에이엠아이"),
    ("ALB", "에이엘비"),
    ("AZs", "에이지즈"),
    ("AZ", "에이제트"),
    ("CDN", "씨디엔"),
    ("CLI", "씨엘아이"),
    ("CMK", "씨엠케이"),
    ("CORS", "코스"),
    ("DNS", "디엔에스"),
    ("EBS", "이비에스"),
    ("EC2", "이씨투"),
    ("ECR", "이씨알"),
    ("ECS", "이씨에스"),
    ("EFS", "이애프에스"),
    ("EKS", "이케이에스"),
    ("ELB", "이엘비"),
    ("EMR", "이엠알"),
    ("HTTP", "에이치티티피"),
    ("HTTPS", "에이치티티피에스"),
    ("IP address", "아이피 어드레스"),
    ("IPv4", "아이피브이포"),
    ("IP", "아이피"),
    ("JSON", "제이슨"),
    ("JWT", "제이더블유티"),
    ("KCL", "케이씨엘"),
    ("KMS", "케이엠에스"),
    ("KPL", "케이피엘"),
    ("LAMP", "램프"),
    ("MFA", "엠에프에이"),
    ("NAT", "나트"),
    ("OAI", "오에이아이"),
    ("RCUs", "알씨유즈"),
    ("RCU", "알씨유"),
    ("RDS", "알디에스"),
    ("RESTful", "레스트풀"),
    ("REST", "레스트"),
    ("SAM", "샘"),
    ("SDK", "에스디케이"),
    ("SES", "에스이에스"),
    ("SNS", "에스엔에스"),
    ("SQS", "에스큐에스"),
    ("SSL", "에스에스엘"),
    ("STS", "에스티에스"),
    ("TCP", "티씨피"),
    ("TLS", "티엘에스"),
    ("TTL", "티티엘"),
    ("UDP", "유디피"),
    ("URI", "유알아이"),
    ("URL", "유알엘"),
    ("VPC", "브이피씨"),
    ("VPN", "브이피엔"),
    ("WCUs", "더블유씨유즈"),
    ("WCU", "더블유씨유"),
    ("XML", "엑스엠엘"),
    ("YAML", "야믈"),
    ("API", "에이피아이"),
    ("S3", "에스쓰리"),
    ("iOS", "아이오에스"),
    ("Android", "안드로이드"),
    ("Apache", "아파치"),
    ("JavaScript", "자바스크립트"),
    ("Linux", "리눅스"),
    ("MySQL", "마이에스큐엘"),
    ("Node.js", "노드제이에스"),
    ("Python", "파이썬"),
    ("Swagger", "스웨거"),
    ("Tomcat", "톰캣"),
    ("backend application", "백엔드 애플리케이션"),
    ("backend", "백엔드"),
    ("front-end", "프론트엔드"),
    ("frontend", "프론트엔드"),
    ("microservices", "마이크로서비스"),
    ("microservice", "마이크로서비스"),
    ("serverless", "서버리스"),
    ("on-premises", "온프레미스"),
    ("near-real time", "니어 리얼타임"),
    ("real-time", "리얼타임"),
    ("read-heavy", "리드 헤비"),
    ("compute-intensive", "컴퓨트 인텐시브"),
    ("multi-value headers", "멀티 밸류 헤더즈"),
    ("multi-value", "멀티 밸류"),
    ("cross-account", "크로스 어카운트"),
    ("cross-region replication", "크로스 리전 리플리케이션"),
    ("cross-origin requests", "크로스 오리진 리퀘스트"),
    ("event notifications", "이벤트 노티피케이션즈"),
    ("event notification", "이벤트 노티피케이션"),
    ("event-driven", "이벤트 드리븐"),
    ("Event driven", "이벤트 드리븐"),
    ("fan-out", "팬아웃"),
    ("long polling", "롱 폴링"),
    ("short polling", "쇼트 폴링"),
    ("exponential backoff", "익스포넨셜 백오프"),
    ("global secondary index", "글로벌 세컨더리 인덱스"),
    ("local secondary index", "로컬 세컨더리 인덱스"),
    ("primary key", "프라이머리 키"),
    ("partition key", "파티션 키"),
    ("sort key", "소트 키"),
    ("read replica", "리드 레플리카"),
    ("read capacity units", "리드 캐퍼시티 유닛즈"),
    ("read capacity unit", "리드 캐퍼시티 유닛"),
    ("write capacity units", "라이트 캐퍼시티 유닛즈"),
    ("write capacity unit", "라이트 캐퍼시티 유닛"),
    ("provisioned throughput", "프로비저닝드 스루풋"),
    ("provisioned capacity", "프로비저닝드 캐퍼시티"),
    ("on-demand", "온디맨드"),
    ("strongly consistent reads", "스트롱리 컨시스턴트 리드즈"),
    ("eventually consistent reads", "이벤트추얼리 컨시스턴트 리드즈"),
    ("strongly consistent", "스트롱리 컨시스턴트"),
    ("eventually consistent", "이벤트추얼리 컨시스턴트"),
    ("load-balanced", "로드 밸런스드"),
    ("load balancer", "로드 밸런서"),
    ("target group", "타깃 그룹"),
    ("security group", "시큐리티 그룹"),
    ("route table", "라우트 테이블"),
    ("public subnet", "퍼블릭 서브넷"),
    ("private subnet", "프라이빗 서브넷"),
    ("VPC endpoint", "브이피씨 엔드포인트"),
    ("endpoint", "엔드포인트"),
    ("query string parameters", "쿼리 스트링 파라미터즈"),
    ("query string parameter", "쿼리 스트링 파라미터"),
    ("query string", "쿼리 스트링"),
    ("mapping template", "매핑 템플릿"),
    ("request validation", "리퀘스트 밸리데이션"),
    ("integration type", "인티그레이션 타입"),
    ("method request", "메서드 리퀘스트"),
    ("status code", "스테이터스 코드"),
    ("status description", "스테이터스 디스크립션"),
    ("request body", "리퀘스트 바디"),
    ("HTTP header", "에이치티티피 헤더"),
    ("headers", "헤더즈"),
    ("header", "헤더"),
    ("payload", "페이로드"),
    ("metadata", "메타데이터"),
    ("dashboard", "대시보드"),
    ("thumbnail", "섬네일"),
    ("web fonts", "웹 폰트"),
    ("web font", "웹 폰트"),
    ("web application", "웹 애플리케이션"),
    ("mobile application", "모바일 애플리케이션"),
    ("mobile app", "모바일 앱"),
    ("user pool", "유저 풀"),
    ("identity pool", "아이덴티티 풀"),
    ("identity provider", "아이덴티티 프로바이더"),
    ("web identity federation", "웹 아이덴티티 페더레이션"),
    ("federation", "페더레이션"),
    ("custom authorizer", "커스텀 오소라이저"),
    ("authorizer", "오소라이저"),
    ("principal", "프린시펄"),
    ("permissions", "퍼미션즈"),
    ("permission", "퍼미션"),
    ("policy", "폴리시"),
    ("role", "롤"),
    ("bucket policy", "버킷 폴리시"),
    ("lifecycle policy", "라이프사이클 폴리시"),
    ("Origin Protocol Policy", "오리진 프로토콜 폴리시"),
    ("Viewer Protocol Policy", "뷰어 프로토콜 폴리시"),
    ("origin protocol policy", "오리진 프로토콜 폴리시"),
    ("viewer protocol policy", "뷰어 프로토콜 폴리시"),
    ("origin", "오리진"),
    ("viewer", "뷰어"),
    ("distribution", "디스트리뷰션"),
    ("invalidation", "인밸리데이션"),
    ("invalidate", "인밸리데이트"),
    ("cache cluster", "캐시 클러스터"),
    ("caching layer", "캐싱 레이어"),
    ("caching", "캐싱"),
    ("cache", "캐시"),
    ("bucket", "버킷"),
    ("objects", "오브젝트즈"),
    ("object", "오브젝트"),
    ("shards", "샤즈"),
    ("shard", "샤드"),
    ("stream", "스트림"),
    ("queue", "큐"),
    ("message", "메시지"),
    ("batch", "배치"),
    ("worker", "워커"),
    ("standby", "스탠바이"),
    ("repository", "리포지토리"),
    ("source control", "소스 컨트롤"),
    ("bundle", "번들"),
    ("deployment policy", "디플로이먼트 폴리시"),
    ("deployment", "디플로이먼트"),
    ("deployments", "디플로이먼츠"),
    ("deploy", "디플로이"),
    ("redeploy", "리디플로이"),
    ("Rolling with additional batch", "롤링 위드 어디셔널 배치"),
    ("All at once", "올 앳 원스"),
    ("Immutable", "이뮤터블"),
    ("Rolling", "롤링"),
    ("instance metadata", "인스턴스 메타데이터"),
    ("user data", "유저 데이터"),
    ("instance store", "인스턴스 스토어"),
    ("root volume", "루트 볼륨"),
    ("root filesystem", "루트 파일시스템"),
    ("ephemeral disks", "이페머럴 디스크즈"),
    ("ephemeral disk", "이페머럴 디스크"),
    ("volume", "볼륨"),
    ("filesystem", "파일시스템"),
    ("directory", "디렉터리"),
    ("environment variables", "인바이런먼트 베리어블즈"),
    ("environment variable", "인바이런먼트 베리어블"),
    ("environment", "인바이런먼트"),
    ("production", "프로덕션"),
    ("development", "디벨롭먼트"),
    ("stage", "스테이지"),
    ("runtime", "런타임"),
    ("handler", "핸들러"),
    ("thread", "스레드"),
    ("threads", "스레즈"),
    ("connection string", "커넥션 스트링"),
    ("database connection", "데이터베이스 커넥션"),
    ("database", "데이터베이스"),
    ("table", "테이블"),
    ("attributes", "어트리뷰트즈"),
    ("attribute", "어트리뷰트"),
    ("item", "아이템"),
    ("projection", "프로젝션"),
    ("projected attributes", "프로젝티드 어트리뷰트즈"),
    ("throughput", "스루풋"),
    ("latency", "레이턴시"),
    ("encryption", "인크립션"),
    ("encrypted", "인크립티드"),
    ("decrypt", "디크립트"),
    ("decryption", "디크립션"),
    ("in transit", "인 트랜짓"),
    ("at rest", "앳 레스트"),
    ("audit trail", "오딧 트레일"),
    ("versioning", "버저닝"),
    ("retries", "리트라이즈"),
    ("retry", "리트라이"),
    ("source bundle", "소스 번들"),
    ("cron job", "크론 잡"),
    ("pre_build", "프리 빌드"),
]


GLOSSARY_PATTERNS = [
    (
        re.compile(r"(?<![A-Za-z0-9_])" + re.escape(term) + r"(?![A-Za-z0-9_])", re.IGNORECASE),
        reading,
    )
    for term, reading in sorted(GLOSSARY, key=lambda item: len(item[0]), reverse=True)
]


CHOICE_PATTERN = re.compile(r"^- \[(?P<mark>[ x])\] (?P<text>.*)$")
CODE_PATTERN = re.compile(r"`[^`]*`|https?://[^\s`]+|s3://[^\s`]+", re.IGNORECASE)


def parse_exam_markdown(path):
    questions = []
    current_question = None
    current_choice = None

    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.rstrip()
        if line.startswith("### "):
            if current_question is not None:
                questions.append(current_question)
            current_question = {
                "prompt_lines": [line[4:].strip()],
                "choices": [],
            }
            current_choice = None
            continue

        if current_question is None:
            continue

        if "Back to Top" in line:
            current_choice = None
            continue

        choice_match = CHOICE_PATTERN.match(line)
        if choice_match:
            current_choice = {
                "text_lines": [choice_match.group("text").strip()],
                "correct": choice_match.group("mark") == "x",
            }
            current_question["choices"].append(current_choice)
            continue

        stripped = line.strip()
        if not stripped:
            continue

        if current_choice is not None and (raw_line.startswith(" ") or raw_line.startswith("\t")):
            current_choice["text_lines"].append(stripped)
        else:
            current_question["prompt_lines"].append(stripped)
            current_choice = None

    if current_question is not None:
        questions.append(current_question)

    normalized = []
    for number, question in enumerate(questions, start=1):
        choices = [
            {
                "text": "\n".join(choice["text_lines"]).strip(),
                "correct": choice["correct"],
            }
            for choice in question["choices"]
        ]
        if len(choices) < 2:
            raise ValueError(f"Question {number} has fewer than 2 choices")
        if not any(choice["correct"] for choice in choices):
            raise ValueError(f"Question {number} has no correct choice")
        normalized.append(
            {
                "number": number,
                "prompt": "\n".join(question["prompt_lines"]).strip(),
                "choices": choices,
            }
        )
    return normalized


def add_marker(replacements, value):
    marker = f"__TERM{len(replacements):04d}__"
    replacements[marker] = value
    return marker


def protect_text(text):
    replacements = {}

    def protect_code(match):
        return add_marker(replacements, match.group(0))

    protected = CODE_PATTERN.sub(protect_code, text)

    for pattern, reading in GLOSSARY_PATTERNS:
        def protect_term(match, reading=reading):
            original = match.group(0)
            return add_marker(replacements, f"{reading}({original})")

        protected = pattern.sub(protect_term, protected)

    return protected, replacements


def restore_text(text, replacements):
    restored = text
    for marker, replacement in replacements.items():
        restored = restored.replace(marker, replacement)
    return normalize_translation(restored)


def normalize_translation(text):
    text = text.replace(" .", ".")
    text = text.replace(" ?", "?")
    text = text.replace(" !", "!")
    text = text.replace(" ,", ",")
    text = re.sub(r"\s+\n", "\n", text)
    text = re.sub(r"\n\s+", "\n", text)
    text = re.sub(r"[ \t]{2,}", " ", text)
    return text.strip()


def google_translate_batch(items, retries=4):
    if not items:
        return []

    query = urllib.parse.urlencode(
        {
            "client": "gtx",
            "sl": "en",
            "tl": "ko",
            "dt": "t",
            "q": ITEM_SEPARATOR.join(items),
        }
    )
    url = f"{TRANSLATE_URL}?{query}"

    for attempt in range(retries):
        try:
            with urllib.request.urlopen(url, timeout=30) as response:
                payload = json.loads(response.read().decode("utf-8"))
            translated = "".join(part[0] for part in payload[0] if part and part[0])
            parts = [part.strip() for part in translated.split(ITEM_SEPARATOR.strip())]
            if len(parts) == len(items):
                return parts
            if len(items) == 1:
                return [translated.strip()]
            translated_items = []
            for item in items:
                translated_items.extend(google_translate_batch([item], retries=retries))
                time.sleep(0.05)
            return translated_items
        except Exception:
            if attempt == retries - 1:
                raise
            time.sleep(0.8 * (attempt + 1))

    raise RuntimeError("translation failed")


def translate_texts(texts, cache, batch_size):
    result = {}
    pending = []
    protected_by_original = {}

    for text in texts:
        if text in cache:
            result[text] = cache[text]
            continue
        protected, replacements = protect_text(text)
        protected_by_original[text] = (protected, replacements)
        pending.append(text)

    total = len(pending)
    for start in range(0, total, batch_size):
        originals = pending[start:start + batch_size]
        protected_items = [protected_by_original[text][0] for text in originals]
        translated_items = google_translate_batch(protected_items)
        if len(translated_items) != len(originals):
            raise RuntimeError("translator returned an unexpected number of items")

        for original, translated in zip(originals, translated_items):
            replacements = protected_by_original[original][1]
            final_text = restore_text(translated, replacements)
            cache[original] = final_text
            result[original] = final_text

        print(f"translated {min(start + batch_size, total)}/{total}", flush=True)
        time.sleep(0.12)

    return result


def build_import_json(questions, translations):
    return {
        "creator": {
            "email": "admin@example.com",
            "password": "password123",
            "displayName": "Admin",
        },
        "title": "AWS Developer Associate Practice Exam - Korean",
        "description": "Translated import generated from src/main/resources/exam.md.",
        "timeLimitMinutes": 130,
        "published": True,
        "questions": [
            {
                "prompt": translations[question["prompt"]],
                "points": 1,
                "explanation": None,
                "choices": [
                    {
                        "text": translations[choice["text"]],
                        "correct": choice["correct"],
                    }
                    for choice in question["choices"]
                ],
            }
            for question in questions
        ],
    }


def load_cache(path):
    if not path.exists():
        return {}
    return json.loads(path.read_text(encoding="utf-8"))


def save_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(data, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", default="src/main/resources/exam.md")
    parser.add_argument("--output", default="data/exam-import.translated.json")
    parser.add_argument("--cache", default="data/translation-cache.json")
    parser.add_argument("--batch-size", type=int, default=12)
    parser.add_argument("--no-translate", action="store_true")
    args = parser.parse_args()

    questions = parse_exam_markdown(Path(args.source))
    texts = []
    for question in questions:
        texts.append(question["prompt"])
        texts.extend(choice["text"] for choice in question["choices"])

    print(f"parsed questions: {len(questions)}")
    print(f"parsed choices: {sum(len(question['choices']) for question in questions)}")
    print(f"unique translatable texts: {len(set(texts))}")

    cache_path = Path(args.cache)
    cache = load_cache(cache_path)
    if args.no_translate:
        translations = {text: text for text in texts}
    else:
        translations = translate_texts(list(dict.fromkeys(texts)), cache, args.batch_size)
        save_json(cache_path, cache)

    output = build_import_json(questions, translations)
    save_json(Path(args.output), output)
    print(f"wrote {args.output}")


if __name__ == "__main__":
    main()
