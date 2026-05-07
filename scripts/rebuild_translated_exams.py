#!/usr/bin/env python3
"""
DB의 시험·응시 데이터를 비우고, exam.md / exam2.txt 기준으로 재번역한 뒤 다시 임포트합니다.

전제:
  - PostgreSQL에 접속 가능(psql 또는 libpq 환경변수)
  - 한 번 `sh gradlew compileJava` 로 JDBC jar 가 내려받혀 있음
  - Google 번역 API(translate.googleapis.com)에 네트워크로 접근 가능

순서:
  1) exam2-import.json 생성 (exam2.txt 파싱, 문항 explanation·선지 rationale 포함)
  2) exam-import.seed.json 생성 (exam.md + 템플릿, 문항 explanation·선지 rationale 포함)
  3) 두 시험 재번역 JSON (--json-only)
  4) 시험 관련 테이블 TRUNCATE
  5) Spring boot 한 번으로 시드 JSON 두 개 임포트 후 종료
  6) 재번역 JSON으로 UPDATE (--db-only)

사용:
  export PGPASSWORD=exam   # 등
  python3 scripts/rebuild_translated_exams.py

JDBC URL은 src/main/resources/application.properties 의 기본값에서 호스트/DB를 읽거나,
환경변수 PGHOST, PGPORT, PGUSER, PGDATABASE, PGPASSWORD 를 직접 설정할 수 있습니다.
"""
from __future__ import annotations

import os
import re
import subprocess
import sys
from pathlib import Path


def repo_root() -> Path:
    return Path(__file__).resolve().parent.parent


def parse_jdbc_defaults() -> tuple[str, str, str, str, str]:
    props_path = repo_root() / "src/main/resources/application.properties"
    text = props_path.read_text(encoding="utf-8")
    url_m = re.search(r"spring\.datasource\.url=\$\{DB_URL:([^}]*)\}", text)
    user_m = re.search(r"spring\.datasource\.username=\$\{DB_USERNAME:([^}]*)\}", text)
    pass_m = re.search(r"spring\.datasource\.password=\$\{DB_PASSWORD:([^}]*)\}", text)
    jdbc = (url_m.group(1) if url_m else "").strip()
    user = (user_m.group(1) if user_m else "exam").strip()
    password = (pass_m.group(1) if pass_m else "exam").strip()
    jm = re.match(r"jdbc:postgresql://([^:/]+)(?::(\d+))?/([^?]+)", jdbc)
    if not jm:
        raise SystemExit(f"JDBC URL을 파싱할 수 없습니다: {jdbc!r}")
    host, port, db = jm.group(1), jm.group(2) or "5432", jm.group(3)
    return host, port, db, user, password


def psql_env() -> dict[str, str]:
    env = os.environ.copy()
    if env.get("PGHOST"):
        return env
    host, port, db, user, password = parse_jdbc_defaults()
    env.setdefault("PGHOST", host)
    env.setdefault("PGPORT", port)
    env.setdefault("PGDATABASE", db)
    env.setdefault("PGUSER", user)
    env.setdefault("PGPASSWORD", password)
    return env


def run(cmd: list[str], *, cwd: Path, env: dict[str, str] | None = None) -> None:
    print("+", " ".join(cmd), flush=True)
    subprocess.run(cmd, cwd=str(cwd), env=env, check=True)


def main() -> None:
    root = repo_root()
    py = sys.executable

    # exam2-import.json 을 먼저 만들어 두면 retranslate --json-only 가 exam2 번역에 사용합니다.
    run([py, str(root / "scripts" / "parse_exam2_to_import.py")], cwd=root)
    run([py, str(root / "scripts" / "retranslate_exam_ko.py"), "--write-seed-import"], cwd=root)
    run([py, str(root / "scripts" / "retranslate_exam_ko.py"), "--json-only"], cwd=root)

    sql = root / "scripts" / "db_reset_exams.sql"
    env = psql_env()
    run(["psql", "-v", "ON_ERROR_STOP=1", "-f", str(sql)], cwd=root, env=env)

    boot_env = os.environ.copy()
    boot_env["APP_IMPORT_EXAM_JSON"] = str((root / "data" / "exam-import.seed.json").resolve())
    boot_env["APP_IMPORT_EXAM_JSON_ADDITIONAL"] = str((root / "data" / "exam2-import.json").resolve())
    boot_env["APP_IMPORT_SHUTDOWN_AFTER_IMPORT"] = "true"
    run(
        ["sh", "gradlew", "bootRun", "--args=--spring.main.web-application-type=none"],
        cwd=root,
        env=boot_env,
    )

    run([py, str(root / "scripts" / "retranslate_exam_ko.py"), "--db-only"], cwd=root)
    print("done: DB cleared, exams re-imported, Korean text applied from retranslated JSON.", flush=True)


if __name__ == "__main__":
    main()
