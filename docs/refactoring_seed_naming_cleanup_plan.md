# Refactoring Plan: Seed 패키지 정리 및 네이밍 개선

## 개요

Phase 3 클린업 이후 남은 하위 호환 코드, 중복 상수, 부정확한 네이밍을 제거한다.
핵심 목표는 `seed` 패키지를 `leagueentry`로 전환하고, `SeedBootstrap*` 클래스명을 실제 역할에 맞게 변경하는 것이다.

**영향 범위:** seed, pipeline, coverage, common 패키지 전체  
**브랜치:** refactor/seed-naming-cleanup

---

## Phase 0: Summoner.lastSeenPatch 제거

### 대상

| 파일 | 변경 내용 |
|------|---------|
| `common/infra/persistence/entity/Summoner.java` | `lastSeenPatch` 필드 및 `@Column` 제거 |
| `SummonerJpaRepositoryTest.java` | `.lastSeenPatch(null)` 빌더 코드 제거 |
| `SummonerJpaRepositoryPhase2Test.java` | `.lastSeenPatch(null)` 빌더 코드 제거 |

### 상세

- 엔티티에 `@Column(name = "last_seen_patch")` 필드만 선언되어 있고, setter 메서드·비즈니스 로직·쿼리에서 전혀 사용되지 않음.
- `create()`에서 항상 `null`로 초기화된 후 아무 값도 쓰이지 않음.
- DB 컬럼 제거 시 Flyway 마이그레이션 필요.

### 위험도: 낮음 (읽는 코드 없음)

---

## Phase 1: SeedBootstrapExecutor 데드 코드 제거

### 대상

| 파일 | 변경 내용 |
|------|---------|
| `seed/application/SeedBootstrapExecutor.java` | `SeedBootstrapResult`에서 `matchIdsFetched`, `matchIdsEnqueued` 제거 |
| `common/domain/model/SeedBootstrapCommand.java` | `matchesPerPuuid` 필드 제거 |
| `seed/presentation/api/SeedBootstrapController.java` | 파라미터 정리 |

### 상세

- `matchIdsFetched`, `matchIdsEnqueued`: 주석에 "하위 호환: 항상 0 반환"으로 명시. Stage 2(`CollectMatchIdsRunner`) 분리 이후 의미 없음.
- `matchesPerPuuid`: 프로덕션 코드 어디에서도 읽지 않는 미사용 필드.
- `startPage`/`endPage` → 단일 `page`로 통합: 모든 프로덕션 호출자(`DailySeedRunner`)가 `startPage == endPage`로 호출함. 어드민 컨트롤러에서는 컨트롤러 레이어에서 루프 처리.

### 테스트 정리

- `SeedBootstrapExecutorTest` + `SeedBootstrapExecutorPhase3Test`: 거의 동일한 케이스를 커버함. `Phase3Test`를 기준으로 하나로 합치고 레거시 파일 삭제.
- `matchIdsFieldsAlwaysZero` 테스트 케이스 삭제 (shim 제거와 함께 불필요).

### 위험도: 낮음

---

## Phase 2: SummonerSeedRegistry.registerIfAbsent() 완전 제거

### 대상

| 파일 | 변경 내용 |
|------|---------|
| `seed/application/port/SummonerSeedRegistry.java` | `registerIfAbsent()` 메서드 제거 |
| `seed/infra/persistence/SummonerSeedRegistryImpl.java` | 구현 제거 |
| `seed/application/SeedBootstrapExecutorTest.java` | `never()` 검증 코드 제거 |
| `SummonerSeedRegistryImplTest.java` | `registerIfAbsent` 관련 테스트 삭제 |

### 상세

- 프로덕션 코드에서 호출하는 곳이 없음. 테스트에서만 `never()` 검증용으로 참조됨.
- `PatchVersionService.registerIfAbsent`는 별개이므로 건드리지 않음.

### 위험도: 낮음

---

## Phase 3: APEX_TIERS 상수 중복 제거

### 대상

| 파일 | 현재 상태 |
|------|---------|
| `coverage/infra/persistence/entity/CoverageBucket.java` | `Set<Tier> APEX_TIERS` 정의 |
| `pipeline/application/DailySeedRunner.java` | `List<Tier> APEX_TIERS` 정의 |
| `pipeline/application/CollectMatchIdsRunner.java` | `Set<Tier> APEX_TIERS` 정의 |

### 변경 내용

`common/domain/model/Tier.java`에 아래를 추가:

```java
public static final Set<Tier> APEX_TIERS = Set.of(CHALLENGER, GRANDMASTER, MASTER);

public boolean isApex() {
    return APEX_TIERS.contains(this);
}
```

세 파일의 로컬 상수 제거 후 `Tier.APEX_TIERS` / `tier.isApex()` 참조로 교체.

### 위험도: 낮음

---

## Phase 4: DailySeedRunner 가독성 개선

### 변경 내용

1. **메서드명 변경**: `runDiaEmePage()` → `runNonApexPage()` (`runApexTierChunk()`과 패턴 통일)

2. **헬퍼 추출**: 중복된 patch null-check

```java
// before: lines 84, 111에 동일 로직 중복
if (bucket.getCurrentPatch() == null) { ... }

// after
private Optional<String> currentPatchOrNull(CoverageBucket bucket) { ... }
```

3. **헬퍼 추출**: 중복된 apex tier 루프 (lines 77-81, 101-108)

```java
private Optional<Tier> nextUncompletedApexTier(DailySeedState state) { ... }
```

4. **상태 전이 이동**: `runNonApexPage`의 `entriesFetched == 0` 분기 → `CoverageBucket.advanceAfterSeedCall(int entriesFetched, int cap)`으로 이동

```java
// CoverageBucket에 추가
public void advanceAfterSeedCall(int entriesFetched, int cap) {
    if (entriesFetched == 0) {
        advanceSeedDivision();
    } else {
        advanceSeedState(entriesFetched, cap);
    }
}
```

### 위험도: 낮음~중간 (엔티티 테스트 업데이트 필요)

---

## Phase 5: DB 컬럼명 변경 (선택)

### 변경 내용

| 현재 | 변경 후 |
|------|--------|
| `daily_seed_reset_at` (컬럼) | `daily_pages_reset_at` |
| `dailySeedResetAt` (필드) | `dailyPagesResetAt` |

- `dailySeedCompleted` 제거 이후 "seed"라는 이름이 `dailyPagesProcessed` 리셋 마커를 가리키는 맥락과 맞지 않음.
- Flyway 마이그레이션 스크립트 필요.

### 위험도: 중간 (DB 마이그레이션)

---

## Phase 6: 기타 정리

### CoverageBucketResponse 보강

현재 `(id, patch, tier)`만 노출. 어드민 디버깅에 실제 필요한 상태는:

```java
public record CoverageBucketResponse(
    Long id,
    String patch,
    String tier,
    int currentPage,        // 추가
    String currentDivision, // 추가
    int dailyPagesProcessed,// 추가
    int dailyCycleCount     // 추가
) {}
```

사용되지 않는 엔드포인트라면 삭제.

### PipelineProperties 상수 이동

`PipelineRunner`의 `RATE_LIMIT_SLEEP_MS = 60_000` → `PipelineProperties`로 이동.

### 위험도: 낮음

---

## Phase 7: 네이밍 및 패키지 개선

### 7-1. seed 패키지 → leagueentry 패키지

```
com.bestduo_BE.leagueentry          →  com.bestduo_BE.leagueentry
```

영향 파일:
- `seed/application/SeedBootstrapExecutor.java`
- `seed/application/port/LeagueEntriesSeedLoader.java`
- `seed/application/port/SummonerSeedRegistry.java`
- `seed/infra/persistence/SummonerSeedRegistryImpl.java`
- `seed/presentation/api/SeedBootstrapController.java`
- 테스트 디렉토리 동일 구조

> IntelliJ: 패키지 우클릭 → Refactor → Rename Package로 import 일괄 처리

### 7-2. SeedBootstrap* 클래스명 변경

| 현재 | 변경 후 | 위치 |
|------|--------|------|
| `SeedBootstrapExecutor` | `LeagueEntriesFetcher` | `leagueentry/application/` |
| `SeedBootstrapCommand` | `LeagueEntriesFetchCommand` | `common/domain/model/` |
| `SeedBootstrapResult` | `LeagueEntriesFetchResult` | (Executor 내부 record) |
| `SeedBootstrapController` | `LeagueEntriesController` | `leagueentry/presentation/api/` |

### 7-3. CoverageBucket 필드명 변경

| 현재 | 변경 후 | 이유 |
|------|--------|------|
| `seedPage` | `currentPage` | 순회 위치인데 "seed"가 붙는 게 어색함 |
| `seedDivision` | `currentDivision` | 동일 |
| `dailyCycleCount` | `divisionCycleCount` | IV→I 순환이 "division cycle"임을 명확화 |

DB 컬럼도 함께 변경 (`seed_page` → `current_page` 등), Flyway 마이그레이션 필요.

### 7-4. Runner/Worker 네이밍 통일

| 현재 | 변경 후 |
|------|--------|
| `MatchIngestWorker` | `MatchIngestRunner` |
| `DailySeedRunner` | `DailyLeagueEntriesRunner` |

파이프라인 단계 전체가 `*Runner` 패턴으로 통일됨:
- `DailyLeagueEntriesRunner` (Stage 1)
- `CollectMatchIdsRunner` (Stage 2)
- `MatchIngestRunner` (Stage 3)

### 위험도: 중간 (영향 범위 넓음, import 대규모 수정)

---

## 실행 순서

```
Phase 1 (데드 코드) → Phase 2 (미사용 메서드) → Phase 3 (상수 중복)
→ Phase 4 (DailySeedRunner) → Phase 6 (기타) → Phase 7 (네이밍/패키지)
→ Phase 5 (DB 마이그레이션, 별도 PR 권장)
```

> Phase 7은 import 변경 범위가 크기 때문에 마지막에 한 번에 처리.  
> Phase 5는 DB 변경이 포함되어 있으므로 별도 PR 또는 별도 스프린트.

---

## 테스트 전략

- 각 Phase 완료 후 `./gradlew test` 실행
- `@DisplayName` 값은 한글로 작성
- 커버리지 80% 이상 유지
- Phase 4 완료 후 `CoverageBucketCycleTest` 업데이트 필수
- Phase 7 완료 후 컨트롤러 테스트 경로 확인

---

## 성공 기준

- [ ] `Summoner.lastSeenPatch` 완전 제거
- [ ] `SeedBootstrapResult`에 하위 호환 필드 없음
- [ ] `SeedBootstrapCommand`에 `matchesPerPuuid` 없음, `startPage`/`endPage` → `page`
- [ ] `SummonerSeedRegistry.registerIfAbsent()` 완전 제거
- [ ] `APEX_TIERS` 상수가 `Tier` enum에만 존재
- [ ] `seed` 패키지가 `leagueentry`로 이전 완료
- [ ] 파이프라인 단계가 모두 `*Runner`로 통일
- [ ] `./gradlew build` && `./gradlew test` 통과
