---
title: 운영 환경변수 설정 전략
status: active
last_updated: 2026-04-17
---

# 운영 환경변수 설정 전략

BestDuo 백엔드는 Railway에 배포되며, 운영 중 튜닝이 필요한 값은 **Railway 환경변수 덮어쓰기 + 자동 재배포** 방식으로 관리한다. Spring Cloud Config, DB 설정 테이블 같은 별도 런타임 설정 인프라는 도입하지 않는다 (YAGNI — 서비스 규모 대비 오버엔지니어링).

---

## 1. 기본 원칙

- **yml = 기본값, env var = 운영 오버라이드.**
  - `application.yml`의 값은 로컬/기본 실행 시에만 사용되는 fallback.
  - 운영(`prod` 프로파일)에서 튜닝이 필요한 모든 값은 `${ENV_VAR:기본값}` 형태로 외부화한다.
- **env 변경 = 재배포 = 짧은 다운타임.**
  - Railway는 env 저장 시 자동으로 컨테이너를 재시작한다 (약 20~60초 소요).
  - 트래픽/배치 적은 시간대에 변경하는 것을 원칙으로 한다.
- **값 범위는 `@Validated`로 시작 시 검증한다.**
  - 잘못된 값이 주입되면 앱이 **시작을 거부**하도록 하여 조용한 오작동을 방지한다.

---

## 2. 환경변수 네이밍 컨벤션

- 대문자 + 언더스코어 사용: `PIPELINE_COLLECT_DAILY_BUDGET`
- Spring Boot의 Relaxed Binding이 자동으로 `pipeline.collect-daily-budget`과 매핑한다.
- 접두사는 yml의 property prefix를 따른다 (`pipeline.*` → `PIPELINE_*`, `external.riot.*` → `EXTERNAL_RIOT_*`).

---

## 3. 값 분류

### (A) env var로 운영 중 튜닝 가능한 값

파이프라인 처리량, 예산, 재시도 정책처럼 운영 피드백을 보며 주기적으로 조정하는 값.

| 키 | Env Var | 기본값 | 용도 |
|---|---|---|---|
| `pipeline.seed-daily-budget` | `PIPELINE_SEED_DAILY_BUDGET` | 2000 | Stage 1 SEED 일일 API 호출 상한 |
| `pipeline.collect-daily-budget` | `PIPELINE_COLLECT_DAILY_BUDGET` | 8000 | Stage 2 COLLECT 일일 API 호출 상한 |
| `pipeline.collect-batch-size` | `PIPELINE_COLLECT_BATCH_SIZE` | 20 | Stage 2 한 번에 처리할 summoner 수 |
| `pipeline.ingest-batch-size` | `PIPELINE_INGEST_BATCH_SIZE` | 10 | Stage 3 한 번에 처리할 match 수 |
| `pipeline.polling-interval-ms` | `PIPELINE_POLLING_INTERVAL_MS` | 5000 | 큐가 빌 때 대기 시간 (ms) |
| `pipeline.max-pages-per-division` | `PIPELINE_MAX_PAGES_PER_DIVISION` | 100 | DIA/EME 1 division 최대 page 수 |
| `pipeline.tier-match-count.apex-tiers` | `PIPELINE_TIER_APEX` | 100 | Apex 티어 summoner당 matchIds 수 |
| `pipeline.tier-match-count.diamond-emerald` | `PIPELINE_TIER_DIA_EME` | 100 | DIA/EME 티어 summoner당 matchIds 수 |
| `pipeline.ingest.stale-minutes` | `PIPELINE_INGEST_STALE_MINUTES` | 10 | RUNNING 상태 stale 복구 시간 (분) |
| `pipeline.ingest.error-cooldown-minutes` | `PIPELINE_INGEST_ERROR_COOLDOWN_MINUTES` | 10 | ERROR 재시도 쿨다운 (분) |
| `pipeline.ingest.max-retry` | `PIPELINE_INGEST_MAX_RETRY` | 2 | 최대 재시도 횟수 |
| `pipeline.stage3-priority-tier` | `PIPELINE_STAGE3_PRIORITY_TIER` | (null) | Stage 3 우선 처리 티어 |

### (B) Kill-switch (긴급 제어)

서비스 긴급 중지/재개 스위치. 재배포 없이 빠르게 끄기 위해 **Boolean env var**로 유지한다.

| 키 | Env Var | 기본값 | 용도 |
|---|---|---|---|
| `patch-sync.enabled` | `PATCH_SYNC_ENABLED` | true | 패치 버전 동기화 스케줄러 on/off |
| `pipeline.runner.enabled` | `PIPELINE_RUNNER_ENABLED` | true | 파이프라인 러너 전체 on/off |

**사용 시나리오:**
- Riot API 장애 / rate limit 폭주 → `PIPELINE_RUNNER_ENABLED=false`
- 패치 동기화 문제 → `PATCH_SYNC_ENABLED=false`

### (C) 환경/보안 값 (변경 빈도 낮음)

| 키 | Env Var | 설명 |
|---|---|---|
| DataSource | `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | DB 연결 정보 |
| Riot API | `EXTERNAL_RIOT_API_KEY` | Riot API 키 (월 1회 갱신) |
| 프로파일 | `SPRING_PROFILES_ACTIVE` | `prod` 고정 |

---

## 4. 운영 절차

### 4.1 튜닝 변경 절차

1. **변경 전**: 파이프라인/트래픽 상태 확인 (Grafana, `/admin/queue` 상태).
2. **Railway Dashboard → Service → Variables**에서 env 수정 후 Save.
3. 자동 재배포 대기 (약 20~60초).
4. `/actuator/health` 및 Grafana로 정상 확인.
5. `docs/operations_tuning_log.md`에 변경 기록 (일시, 키, before → after, 사유, 관찰된 효과).

### 4.2 Kill-switch 발동 절차

1. 이상 감지 (Riot API 5xx 폭주, rate limit 초과 알림 등).
2. `PIPELINE_RUNNER_ENABLED=false`로 변경.
3. 재배포 완료 후 파이프라인이 실제로 멈췄는지 로그/Grafana로 확인.
4. 근본 원인 해결 후 `true`로 복구.

### 4.3 배포 중 유의사항

- env 변경 → 재배포 과정에서 **진행 중인 배치가 강제 종료될 수 있다**.
- `MatchQueue.status = RUNNING`으로 남은 row는 `stale-minutes` 경과 후 자동 복구된다.
- 이를 위해 `server.shutdown: graceful` 설정 및 in-flight 배치의 shutdown hook 대응이 필요하다 (미적용 시 별도 작업 항목).

---

## 5. 구현 규칙

### 5.1 yml 작성 규칙

모든 튜닝 대상 값은 다음 형태로 외부화한다:

```yaml
pipeline:
  seed-daily-budget: ${PIPELINE_SEED_DAILY_BUDGET:2000}
  collect-batch-size: ${PIPELINE_COLLECT_BATCH_SIZE:20}
```

기본값은 yml에 그대로 남겨두어 **env 없이도 실행 가능**하게 한다.

### 5.2 값 범위 검증

`@ConfigurationProperties` 클래스에 `@Validated` + Bean Validation을 적용한다.

```java
@Component
@ConfigurationProperties(prefix = "pipeline")
@Validated
public class PipelineProperties {
  @Min(1) @Max(50000)
  private int seedDailyBudget = 2000;

  @Min(1) @Max(500)
  private int collectBatchSize = 20;
  // ...
}
```

범위를 벗어난 값이 주입되면 앱 시작 시 예외 발생 → Railway 헬스체크 실패 → 이전 배포 유지.

### 5.3 `@Scheduled` 주기 설정

- `fixedRate`(long)는 컴파일 타임 상수만 허용 → env로 못 바꾼다.
- 주기를 env로 바꾸려면 `fixedRateString`을 쓴다:

```java
@Scheduled(fixedRateString = "${pipeline.polling-interval-ms:5000}")
```

### 5.4 로그에 민감값 노출 금지

- `EXTERNAL_RIOT_API_KEY`, DB 패스워드는 로그/예외 메시지에 절대 포함하지 않는다.
- `RiotApiHttpAdapter` 등 외부 호출 로깅 시 URL/헤더 마스킹 필수.

---

## 6. 이 방식의 한계 (수용)

- **변경마다 재배포 필요** → 분 단위 변경이 필요한 A/B 테스트에는 부적합.
- **Railway env 변경 이력이 부실** → 감사 목적이라면 `docs/operations_tuning_log.md`를 성실히 운영해야 한다.
- **다중 인스턴스 동시 반영 보장 안 됨** → Railway 배포 순서에 따라 일시적으로 구/신 설정이 혼재할 수 있다. 현재는 단일 인스턴스이므로 문제 없음.

이 한계가 실제 운영에서 불편해지면 DB 기반 설정 테이블 도입을 재검토한다 (별도 ADR 필요).

---

## 7. 참고

- [architecture.md](./architecture.md) — 전체 아키텍처
- `src/main/resources/application.yml` — 기본값
- `src/main/resources/application-prod.yml` — prod 프로파일 오버라이드
- `src/main/java/com/bestduo_BE/config/PipelineProperties.java` — 파이프라인 설정 바인딩
