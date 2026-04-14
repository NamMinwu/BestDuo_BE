# Stage 3 Priority Tier 환경 변수 설정 계획

## 요구사항

Stage 3(`MatchIngestWorker.executeWithPriority`)에서 그날 먼저 처리할 tier를
환경 변수(`PIPELINE_STAGE3_PRIORITY_TIER`)로 동적으로 지정할 수 있게 한다.
현재 코드에서 `Tier.ALL_TIERS`로 하드코딩된 부분을 설정값으로 교체한다.

---

## 현재 구조

```
PipelineRunner.executeTick()
  └─ matchIngestWorker.executeWithPriority(
         props.getIngestBatchSize(),
         currentPatch              ← requestedTier = ALL_TIERS 하드코딩
     )
  └─ MatchQueueDispatcher.pickAndLockWithPriority(
         limit, maxRetry, cooldown,
         requestedTier=ALL_TIERS,  ← null로 변환되어 tier 필터 없음
         currentPatch
     )
```

DB 쿼리(`pickReadyWithPriorityAndLock`) 정렬:
`CHALLENGER(0) → GRANDMASTER(1) → MASTER(2) → DIAMOND(3) → EMERALD(4) → 기타(5)`

**문제**: tier 우선순위가 SQL 내에 하드코딩되어 있어, 당일 처리 우선 tier를 동적으로 바꿀 수 없다.

---

## 구현 전략

`requestedTier`를 설정값으로 노출한다.
- `ALL_TIERS`이면 현재 동작 유지 (전체, SQL 정렬 기준 처리)
- 특정 tier이면 해당 tier를 `requestedTier`로 전달하여 해당 tier 우선 처리

`executeWithPriority(int, Tier, String)` 오버로드가 이미 존재하므로 추가 구현 불필요.

---

## Phase 1 — PipelineProperties 필드 추가

**파일**: `src/main/java/com/bestduo_BE/config/PipelineProperties.java`

```java
/** Stage 3(INGEST)에서 그날 먼저 처리할 tier. ALL_TIERS이면 기존 우선순위 유지. */
private Tier stage3PriorityTier = Tier.ALL_TIERS;
```

- Spring Boot `@ConfigurationProperties`가 YAML → Enum 자동 변환
- 기본값 `ALL_TIERS` → 기존 동작과 완전히 동일

---

## Phase 2 — application.yml 환경 변수 매핑

**파일**: `src/main/resources/application.yml`

```yaml
pipeline:
  stage3-priority-tier: ${PIPELINE_STAGE3_PRIORITY_TIER:ALL_TIERS}
```

**파일**: `src/test/resources/application.yml`

```yaml
pipeline:
  stage3-priority-tier: ALL_TIERS
```

---

## Phase 3 — PipelineRunner Stage 3 호출부 수정

**파일**: `src/main/java/com/bestduo_BE/pipeline/application/PipelineRunner.java`

현재:
```java
MatchIngestWorker.Result result = matchIngestWorker.executeWithPriority(
    props.getIngestBatchSize(), currentPatch);
```

변경 후:
```java
Tier priorityTier = props.getStage3PriorityTier();
MatchIngestWorker.Result result = matchIngestWorker.executeWithPriority(
    props.getIngestBatchSize(), priorityTier, currentPatch);
```

---

## Phase 4 — 테스트 수정/추가

**파일**: `src/test/java/com/bestduo_BE/pipeline/application/PipelineRunnerTest.java`

| 테스트 케이스 | 설명 |
|---|---|
| `stage3_ALL_TIERS이면_executeWithPriority에_ALL_TIERS_전달` | 기본 동작 유지 확인 |
| `stage3_CHALLENGER_설정시_CHALLENGER_tier로_executeWithPriority_호출` | 특정 tier 전달 확인 |

---

## 변경 파일 요약

| 파일 | 변경 종류 |
|---|---|
| `config/PipelineProperties.java` | 필드 1개 추가 |
| `resources/application.yml` (main) | 설정 키 1개 추가 |
| `resources/application.yml` (test) | 설정 키 1개 추가 |
| `pipeline/application/PipelineRunner.java` | `executeTick()` 2줄 수정 |
| `pipeline/application/PipelineRunnerTest.java` | 테스트 케이스 추가 |

---

## 리스크 분석

| 항목 | 수준 | 비고 |
|---|---|---|
| 기존 동작 변경 | 없음 | 기본값 `ALL_TIERS` = 현재 동작과 동일 |
| `executeWithPriority(int, Tier, String)` 시그니처 이미 존재 | 없음 | 추가 구현 불필요 |
| Enum 문자열 오타 시 앱 기동 실패 | 낮음 | Spring Boot 기동 시점에 즉시 감지됨 |
| 특정 tier 지정 시 해당 큐가 비면 폴링 대기 발생 가능 | 낮음 | 의도된 동작 (해당 tier 소진 후 폴링) |
