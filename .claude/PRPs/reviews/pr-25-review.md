# PR Review: #25 — feat: Daily Pipeline Phase 3~7 — PipelineRunner 직접 실행 루프 구현

**Reviewed**: 2026-04-14
**Author**: NamMinwu
**Branch**: feat/daily-pipeline-phase-3-7 → dev
**Decision**: REQUEST CHANGES

## Summary

전체적으로 설계가 명확하고 테스트 커버리지도 충실합니다.
단, `DailySeedRunner.runApexTierChunk`에서 `recordSeedCompletedTier` 변경이 DB에 저장되지 않는 HIGH 버그가 있어 수정 전 merge를 권장하지 않습니다.

---

## Findings

### CRITICAL
None

---

### HIGH

#### [H1] `DailySeedRunner.runApexTierChunk` — `recordSeedCompletedTier` 변경이 DB에 저장되지 않음

**File**: `src/main/java/com/bestduo_BE/pipeline/application/DailySeedRunner.java:123–124`

```java
state.recordSeedCompletedTier(tier.name());   // ← in-memory 수정만
budgetTracker.recordSeedCall(result.pagesProcessed() > 0 ? result.pagesProcessed() : 1);
```

`state`는 `@Transactional` 없이 반환된 detached 엔티티입니다.
`recordSeedCompletedTier`는 `seedCompletedTiers` 문자열을 in-memory에서만 수정하고,
`budgetTracker.recordSeedCall`은 DB에서 **새 인스턴스를 다시 조회**해 `seedApiCallsUsed`만 저장합니다.
결과적으로 `seedCompletedTiers` 업데이트는 항상 유실됩니다.

**영향**: `hasWorkToday()` 다음 호출 시 CHALLENGER(또는 해당 apex 티어)가 완료되지 않은 것으로 판단되어 무한 재처리됩니다.

**수정 방향** — `DailyBudgetTracker`에 메서드 추가:
```java
public void recordSeedCall(int count, String completedTier) {
    DailyPipelineState state = getOrCreateTodayState();
    state.incrementSeedCalls(count);
    if (completedTier != null) {
        state.recordSeedCompletedTier(completedTier);
    }
    stateRepository.save(state);
}
```
그리고 `runApexTierChunk`에서:
```java
budgetTracker.recordSeedCall(
    result.pagesProcessed() > 0 ? result.pagesProcessed() : 1,
    tier.name()
);
```

---

### MEDIUM

#### [M1] `DailySeedRunner` — `currentPatch` null 시 DIA/EME 작업 묵시적 스킵

**File**: `DailySeedRunner.java:84–90`, `107–114`

`currentPatch`가 null이면 `findByPatchAndTier(null, tier)`는 빈 Optional을 반환하고,
DIA/EME seed 작업이 경고 없이 전부 건너뜁니다.
patch 정보가 없을 때의 동작을 명시적으로 결정하거나 최소한 로그를 남겨야 합니다.

```java
// 예시
if (currentPatch == null) {
    log.warn("DIA/EME seed 스킵: 현재 패치 정보 없음");
    return ChunkResult.noWork();
}
```

#### [M2] `DailyBudgetTracker` — 틱마다 `getOrCreateTodayState()` 중복 호출

**File**: `DailySeedRunner.java:54–61`

`hasWorkToday()`에서 `budgetTracker.canSeed()` → `getOrCreateTodayState()` 1회,
이어서 `budgetTracker.getOrCreateTodayState()` 1회 추가 호출하여 총 2번 DB를 조회합니다.
빠른 루프에서는 단일 조회로 통합하면 DB 부하를 절반으로 줄일 수 있습니다.

#### [M3] `SummonerJpaRepository.upsertSeeded` — `created_at`에 `seededAt` 사용

**File**: `SummonerJpaRepository.java` (신규 쿼리)

```sql
INSERT INTO summoner (..., created_at, updated_at)
VALUES (..., :seededAt, :seededAt)
```

신규 등록 시 `created_at`이 DB 레코드 생성 시각이 아닌 `seededAt`으로 설정됩니다.
`created_at`은 레코드 생성 시점을 의미하는 관례이므로 `now()`로 변경하는 것이 일관성 있습니다.

```sql
INSERT INTO summoner (..., created_at, updated_at)
VALUES (..., now(), :seededAt)
```

---

### LOW

#### [L1] `PipelineRunnerTest.executeTick_whenNothingInQueue_sleepsPollingInterval` — 타이밍 의존 테스트

**File**: `PipelineRunnerTest.java:100–111`

100ms sleep을 80ms 기준으로 검증합니다. CI 환경의 부하에 따라 간헐적으로 실패할 수 있습니다.
`Thread.sleep` 호출 여부는 behavior보다 side-effect에 가깝고, 현재 구조에서 직접 검증하기 어렵습니다.
Sleeper 인터페이스 주입 방식으로 추출하거나, 이 케이스는 테스트 목적을 주석으로 명시하고 flakiness를 인정하는 것이 좋습니다.

#### [L2] `CollectMatchIdsRunner.collectForSummoner` — null 방어 코드 과잉

**File**: `CollectMatchIdsRunner.java:109–111`

```java
if (matchIds == null || matchIds.isEmpty()) {
```

`findMatchIdsSince` / `findRecentMatchIds` 포트 계약이 null을 반환하지 않는다면
`matchIds == null` 체크는 불필요합니다. 포트 JavaDoc에 non-null 계약을 명시하고 null 체크는 제거할 수 있습니다.

#### [L3] `DailySeedRunner.runApexTierChunk` — apex 티어 1페이지 고정

**File**: `DailySeedRunner.java:119`

```java
SeedBootstrapCommand cmd = new SeedBootstrapCommand(QUEUE, tier.name(), "I", tier, 1, 1, 0, 0);
```

CHALLENGER/GRANDMASTER/MASTER는 단일 페이지로 전체 목록이 반환되는 API 구조이므로 현재는 정상이지만,
이 가정을 주석으로 명시하면 미래 변경 시 혼란을 방지할 수 있습니다.

---

## Fix Status (커밋 03ee208)

| 항목 | 상태 | 비고 |
|------|------|------|
| [H1] apex 티어 완료 DB 미저장 | **FIXED** | `recordSeedCall(int, String)` 오버로드 추가, detached 엔티티 직접 수정 제거 |
| [M1] currentPatch null 묵시적 스킵 | **FIXED** | null guard + warn 로그 추가 |
| [M2] created_at에 seededAt 사용 | **FIXED** | `now()`로 변경 |
| [L1] 타이밍 의존 테스트 | 미수정 (허용) | 현재 CI 환경에서 안정적으로 통과 중 |
| [L2] null 방어 코드 과잉 | 미수정 (허용) | 포트 계약 변경 없음 |
| [L3] apex 1페이지 고정 가정 | 미수정 (허용) | 코드 내 주석으로 대체 |

## Validation Results

| Check       | Result  |
|-------------|---------|
| 컴파일      | PASS    |
| 단위 테스트 | PASS (221 tests) |
| 빌드        | PASS    |

---

## Files Reviewed

| File | 변경 |
|------|------|
| `MatchQueueJpaRepository.java` | Modified — pickReadyWithPriorityAndLock 추가 |
| `SummonerJpaRepository.java` | Modified — upsertSeeded 추가 |
| `DailyBudgetTracker.java` | Added |
| `PipelineProperties.java` | Added |
| `MatchIngestWorker.java` | Modified — executeWithPriority 추가, processItems 추출 |
| `MatchQueueDispatcher.java` | Modified — pickAndLockWithPriority 포트 추가 |
| `MatchQueueDispatcherImpl.java` | Modified — pickAndLockWithPriority 구현 |
| `CollectMatchIdsRunner.java` | Added |
| `DailySeedRunner.java` | Added |
| `PipelineRunner.java` | Added |
| `SeedBootstrapExecutor.java` | Modified — matchIds enqueue 제거, upsertSeeded 추가 |
| `SummonerSeedRegistry.java` | Modified — upsertSeeded 포트 추가 |
| `SummonerSeedRegistryImpl.java` | Modified — upsertSeeded 구현 |
| `application.yml` (main) | Modified — pipeline.* 설정 추가 |
| `DailyBudgetTrackerTest.java` | Added |
| `MatchQueueDispatcherPhase5Test.java` | Modified — Tier 파라미터 반영 |
| `CollectMatchIdsRunnerTest.java` | Added |
| `DailySeedRunnerTest.java` | Added |
| `PipelineRunnerTest.java` | Added |
| `SeedBootstrapExecutorPhase3Test.java` | Added |
| `SeedBootstrapExecutorTest.java` | Modified |
| `application.yml` (test) | Modified — pipeline.runner.enabled=false |
