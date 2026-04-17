---
title: 운영 전환 준비 계획
status: active
last_updated: 2026-04-17
---

# 운영 전환 준비 계획

BestDuo 백엔드를 Railway(Hobby plan) 위에서 안정적으로 운영하기 위한 변경 로드맵. 현재 `main` 브랜치는 이미 배포되어 있고 Vercel에 프론트엔드(Next.js App Router, Server Component 기반)가 연결된 상태다.

본 문서는 **무엇을 / 왜 / 어떤 순서로** 바꿀지에 대한 합의 문서이며, 상세 구현은 각 단계에서 별도 PR로 진행한다.

---

## 0. 현재 운영 상황 스냅샷

| 항목 | 현재 |
|---|---|
| 백엔드 | Spring Boot 4.0.0 / Java 21, Railway Hobby plan 단일 인스턴스 |
| 프론트엔드 | Next.js App Router, Vercel 배포, Server Component에서 Railway로 서버-간 fetch |
| Railway 노출 방식 | **Public Networking** (인터넷에서 직접 접근 가능) |
| DB 마이그레이션 | `ddl-auto: update` (prod 포함) |
| Admin 엔드포인트 | `/admin/queue`, `/admin/patch`, `/admin/coverage` **인증 없이 공개** |
| Actuator | `/health,/metrics,/prometheus` 전부 공개, `health.show-details=always` |
| 설정 튜닝 | `application.yml` 하드코딩 값 다수, env 외부화 미완 |
| 관측성 | Actuator + Micrometer + Prometheus registry 의존성만 추가된 상태, 수집기/대시보드 없음 |
| 알림 | 없음 |
| 부하 테스트 | 없음 |
| 종료 처리 | graceful shutdown 미적용 |

### 이미 결정된 운영 전략
- 환경변수 튜닝 방식: [operations_env_config.md](./operations_env_config.md) 참조 (Railway env var 덮어쓰기 → 자동 재배포)
- Admin 엔드포인트는 **제거하지 않고 인증을 붙여 유지** (운영 중 비상 도구로 사용)

---

## 1. 위험도 평가

### 🔴 Critical — 인터넷에 공격면이 열려 있음
1. **Admin API 무인증 노출** — 외부에서 큐 조작(`POST /admin/queue/work`), 패치 강제 등록 가능.
2. **Actuator 정보 유출** — `/actuator/health?show-details=always` → DB 커넥션, 디스크 상태, 구성요소 내부가 그대로 노출.
3. **`ddl-auto: update` in prod** — 앱 재시작 시 엔티티 변경 내용이 DB에 자동 반영. 실수로 `@Column` 삭제 → 프로덕션 컬럼 drop 위험.

### 🟠 High — 운영 안정성 저해
4. 하드코딩된 파이프라인 수치. env 오버라이드 없음 → 튜닝할 때마다 코드 수정/배포 필요.
5. `@ConfigurationProperties`에 값 범위 검증 부재 → 잘못된 env 주입 시 조용히 동작(음수 budget 등).
6. Graceful shutdown 부재 → 재배포 시 in-flight 배치 강제 종료, `RUNNING` stale row 발생.

### 🟡 Medium — 장기적으로 필요
7. 관측성 부재(로그/메트릭/에러 추적/알림) → 장애 발생 후 원인 파악 어려움.
8. Resilience 패턴 부재 → Riot API 일시 장애가 파이프라인 전체 중단으로 번질 수 있음.
9. 부하 특성 미측정 → Hobby plan 한계 예측 불가.

### 🟢 Low — 현재는 문제 없음
10. CORS가 `/v3/api-docs,/swagger-ui`에만 설정됨 — 프론트가 Server Component라 서버-간 호출로 bypass됨. 추후 Client Component fetch 도입 시 수정.
11. 다중 인스턴스 동시성(ShedLock 등) — Hobby plan 단일 인스턴스라 해당 없음.

---

## 2. Phase 1 — 보안 & 스키마 안전장치 (최우선)

**목표**: 인터넷 공격면을 닫고, 스키마 변경으로 데이터를 잃지 않도록 안전장치 설치.

| # | 작업 | 변경 파일 | 비고 |
|---|---|---|---|
| 1.1 | **Admin API Key 인증** | 신규 `AdminApiKeyInterceptor` + `AdminApiKeyProperties`, `WebConfig`, yml | `X-Admin-Key` 헤더 검증, 불일치 시 401. `@Validated @NotBlank`로 빈 키면 앱 시작 거부 |
| 1.2 | **Actuator 축소** | `application.yml`, `application-prod.yml` | exposure = `health,prometheus`, `show-details: never` |
| 1.3 | **Flyway 도입** | `build.gradle`, 신규 `db/migration/V1__baseline.sql`, yml | `ddl-auto: validate`, 기존 DB는 `baselineOnMigrate: true`로 흡수 |
| 1.4 | **Pipeline 값 env 외부화** | `application.yml`, `PipelineProperties` | 현재 [operations_env_config.md](./operations_env_config.md) (A) 표에 정의된 키 전부 `${ENV:default}` 형태로 |
| 1.5 | **`@Validated` + `@Min/@Max`** | `PipelineProperties`, `AdminApiKeyProperties` | 잘못된 env 주입 시 앱 시작 거부 → Railway 헬스체크 실패 → 이전 배포 유지 |
| 1.6 | **Graceful shutdown** | `application.yml` | `server.shutdown: graceful`, `spring.lifecycle.timeout-per-shutdown-phase: 30s` |
| 1.7 | **로그 민감값 마스킹 점검** | `RiotApiHttpAdapter` 등 외부 호출 로깅 | API Key 헤더 마스킹 강제 |

### PR 분할 제안
- **PR-1**: 1.1 Admin 인증 + 1.2 Actuator 축소 (같은 보안 성격, 작은 범위)
- **PR-2**: 1.3 Flyway (별도 — 스키마는 리스크가 달라서 분리)
- **PR-3**: 1.4 + 1.5 + 1.6 (운영 튜닝 인프라 묶음)
- **PR-4**: 1.7 로그 점검 (필요 시)

### 완료 기준
- 외부에서 `curl https://.../admin/queue/stats` → 401
- `curl https://.../actuator/health` → `{"status":"UP"}` 만 반환
- `ADMIN_API_KEY` 미설정 상태에서 앱 시작 시 즉시 실패
- 신규 엔티티 추가 후 Flyway 마이그레이션 없이 재배포 → 앱 시작 실패(의도적)

---

## 3. Phase 2 — 관측성 & 알림

**목표**: 장애를 "알아차리고 원인을 추적"할 수 있게 한다.

> 도구 선정 근거: [관측성 스택 선정 — Trade-off 분석](./decisions/observability_tradeoffs.md)

| # | 작업 | 도입 도구 | 비용 |
|---|---|---|---|
| 2.1 | **구조화 로그(JSON)** | `logstash-logback-encoder` + MDC 상관관계 ID | 무료 |
| 2.2 | **에러 추적** | Sentry (Spring Boot starter) | Developer 무료 플랜 (5K events/month) |
| 2.3 | **메트릭 원격 저장** | Grafana Cloud Prometheus remote_write (또는 Agent push) | Free tier (10K series) |
| 2.4 | **커스텀 메트릭** | Micrometer `Counter`/`Timer` — 파이프라인 stage별 처리량, Riot API 응답시간, 큐 길이 | — |
| 2.5 | **알림 채널** | Discord Webhook (Grafana Alert → Webhook) | 무료 |
| 2.6 | **외부 헬스체크** | UptimeRobot (5분 간격 `/actuator/health`) | 무료 |
| 2.7 | **대시보드** | Grafana Cloud 대시보드 1개 (파이프라인 지표 + JVM + HTTP) | 무료 |

### PR 분할 제안
- **PR-5**: 2.1 + 2.4 (로깅/메트릭 계측 — 코드 변경)
- **PR-6**: 2.2 Sentry 도입
- **PR-7**: Grafana Cloud / UptimeRobot / Discord 설정 (코드 변경 없음, 운영 작업)

### 완료 기준
- Grafana 대시보드에서 "Stage별 처리량 / 큐 길이 / Riot API 실패율" 실시간 확인 가능
- 앱 에러 → Sentry 이슈 자동 생성 → Discord에 메시지
- 서비스 다운 → UptimeRobot이 5분 내 감지 → Discord 알림

---

## 4. Phase 3 — 회복탄력성 & 용량 계획

**목표**: 외부 의존성 장애 시 전체 중단을 막고, Hobby plan 한계를 미리 파악한다.

| # | 작업 | 내용 |
|---|---|---|
| 3.1 | **Resilience4j Retry + CircuitBreaker** | Riot API 호출부에 적용. 5xx 연속 발생 시 차단, 점진적 복구 |
| 3.2 | **Riot API rate limit 가드** | 현재 수동 관리 중인 budget을 Bucket4j / Resilience4j RateLimiter로 전환 검토 |
| 3.3 | **k6 부하 테스트** | 조회 API(GetBottomDuoStats 등)에 대해 Hobby plan CPU/메모리 한계 측정 |
| 3.4 | **DB 백업 전략** | Railway PostgreSQL 자동 백업 정책 확인 + 수동 덤프 스크립트 |
| 3.5 | **운영 런북(Runbook)** | `docs/runbook.md` — 대표 장애 시나리오별 조치 순서 |

### PR 분할 제안
- **PR-8**: 3.1 Resilience4j (코드 변경)
- **PR-9**: 3.3 k6 시나리오 (`/loadtest/*.js`)
- 3.2 / 3.4 / 3.5 는 문서/운영 작업

---

## 5. 일정 감각 (참고)

본 일정은 제안이며, 실제 속도에 맞춰 조정한다.

| 구간 | 기간 감각 | 산출물 |
|---|---|---|
| Phase 1 | 1~2주 | PR-1 ~ PR-4 merge, 운영 환경에 보안/안전장치 적용 |
| Phase 2 | 1~2주 | Grafana 대시보드 + Discord 알림 working |
| Phase 3 | 필요 시 | Resilience4j, 부하테스트, 런북 |

---

## 6. 범위 밖 (현재 의사결정)

다음 항목은 **의식적으로 지금 도입하지 않는다** (YAGNI):

- Spring Cloud Config / DB 기반 설정 테이블 — Railway env var로 충분
- ShedLock / 분산 락 — 단일 인스턴스
- Kubernetes / 자체 인프라 — Railway로 충분
- E2E 자동화 테스트 — 수동 smoke로 충당
- CI/CD 고도화 — Railway 자동 배포로 충당

상황이 바뀌면(멀티 인스턴스 필요, 분/초 단위 설정 반영 필요 등) 해당 시점에 ADR로 재검토.

---

## 7. 참고

- [architecture.md](./architecture.md) — 전체 아키텍처
- [operations_env_config.md](./operations_env_config.md) — 환경변수 전략
- `src/main/resources/application.yml` — 기본 설정
- `src/main/resources/application-prod.yml` — prod 오버라이드
