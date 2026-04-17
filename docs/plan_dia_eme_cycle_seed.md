# 계획: DIA/EME 사이클 기반 일일 할당량 순환

## 1. 배경 및 문제

### 현재 CoverageBucket 구조

`coverage_bucket` 테이블은 DIA/EME 티어의 Riot API 수집 진행 상태를 관리한다.

| 필드 | 역할 |
|---|---|
| `patch` | 현재 패치 버전 (e.g., "15.7") |
| `tier` | DIAMOND / EMERALD |
| `targetMatchCount` | 커버리지 목표 매치 수 |
| `currentMatchCount` | 현재 수집된 매치 수 |
| `status` | COLLECTING / SUFFICIENT |
| `priority` | 처리 우선순위 |
| `seedPage` | 현재 진행 중인 Riot API 페이지 번호 |
| `seedDivision` | 현재 진행 중인 division (I / II / III / IV) |
| `dailySeedCompleted` | 오늘 완료 여부 (제거 예정) |
| `dailySeedResetAt` | 마지막 리셋 시점 |

### 현재 DailySeedRunner 동작의 문제

**문제 1 — 버킷 생성 로직 없음**

```java
for (Tier tier : DIA_EME_TIERS) {
  Optional<CoverageBucket> opt = coverageBucketRepository.findByPatchAndTier(currentPatch, tier);
  if (opt.isPresent() && !opt.get().isDailySeedCompleted()) {
    return runDiaEmePage(opt.get(), tier);
  }
}
```

`CoverageBucket.create()` 팩토리 메서드는 존재하지만 **프로덕션 코드에서 단 한 곳도 호출하지 않는다.**
버킷이 DB에 없으면 DIA/EME seed가 영원히 실행되지 않는다.

**문제 2 — 플래그 기반의 단순한 종료 조건**

- 빈 페이지가 나오면 `isDailySeedCompleted = true`로 마킹하고 하루 종료
- 하루에 얼마나 돌렸는지 추적하지 않음
- 다음 날 다시 I/1부터 돌리는지, 이어서 돌리는지도 불명확

---

## 2. 원하는 동작

### 사이클 기반 순환

```
I/1 → I/2 → … → I/N (빈 응답) → II/1 → … → IV/N (빈 응답) → I/1 (사이클 완주)
```

- Riot `GET /lol/league/v4/entries/{queue}/{tier}/{division}?page={n}` 호출 시
  응답이 비어있으면(`entriesFetched == 0`) 해당 division 소진으로 판단 → 다음 division으로 이동
- IV division 소진 → I/1로 wrap (사이클 1회 완주)
- `seedPage` / `seedDivision`은 날짜가 바뀌어도 **리셋하지 않음** — 사이클 위치를 영속적으로 유지

### 일일 할당량 (quota)

- 하루에 `diaEmeDailyPageQuota`(설정값)만큼 페이지를 처리하면 그날은 멈춤
- 다음 날: `dailyPagesProcessed`만 0으로 초기화, `seedPage` / `seedDivision`은 유지하고 이어서 진행
- 빈 페이지 응답도 quota 1로 카운트 (무한 루프 방지)

### `maxPagesPerDivision` 역할 변경

- 기존: 페이지 수가 이 값에 도달하면 division 전환 트리거
- 변경 후: **safety cap**으로만 동작
  - 실제 division 전환은 항상 빈 응답이 트리거
  - 단, 비정상적으로 페이지가 무한히 계속되는 경우를 막기 위한 안전 상한선

---

## 3. 결정사항

### 버킷 자동 생성 방식

| 옵션 | 설명 |
|---|---|
| 1. 패치 감지 시 생성 | `DataDragonPatchSyncScheduler`가 새 패치 등록 시 버킷도 함께 생성 |
| **2. runNextChunk 진입 시 생성 (채택)** | 버킷이 없으면 그 자리에서 생성 후 바로 실행 |
| 3. 관리자 API | Admin이 직접 POST 호출로 생성 |

Option 2 채택: 별도 이벤트 훅 없이 단순하게 처리 가능.

### 기타 결정

| 항목 | 결정 | 이유 |
|---|---|---|
| 할당량 단위 | per-bucket (DIA / EME 각각 독립) | 티어별 독립 제어 필요 |
| 빈 페이지 quota 카운트 | O (카운트함) | 무한 루프 방지 |
| `dailySeedCompleted` 제거 시점 | Phase 3 (나중에 제거) | 롤백 안전을 위해 deprecated 유지 |
| `diaEmeDailyPageQuota` 기본값 | 10 | 보수적 설정 |

---

## 4. 구현 계획

### Phase 1: CoverageBucket 자동 생성

#### `PipelineProperties.java`

```java
// 추가
/** 자동 생성되는 DIA/EME CoverageBucket의 목표 매치 수 */
private long diaEmeCoverageTarget = 500_000L;
```

#### `DailySeedRunner.java`

`hasUncompletedDiaEmeBucket()` — 버킷 없으면 "작업 있음"으로 취급:

```java
private boolean hasUncompletedDiaEmeBucket() {
  String currentPatch = patchVersionService.currentPatchVersion().orElse(null);
  if (currentPatch == null) return false;

  for (Tier tier : DIA_EME_TIERS) {
    Optional<CoverageBucket> opt = coverageBucketRepository.findByPatchAndTier(currentPatch, tier);
    if (opt.isEmpty() || !opt.get().isDailySeedCompleted()) {
      return true;
    }
  }
  return false;
}
```

`runNextChunk()` DIA/EME 루프 — 버킷 없으면 생성 후 실행:

```java
for (Tier tier : DIA_EME_TIERS) {
  CoverageBucket bucket = getOrCreateBucket(currentPatch, tier);
  if (!bucket.isDailySeedCompleted()) {
    return runDiaEmePage(bucket, tier);
  }
}
```

새 헬퍼 메서드 추가:

```java
private CoverageBucket getOrCreateBucket(String patch, Tier tier) {
  return coverageBucketRepository.findByPatchAndTier(patch, tier)
      .orElseGet(() -> {
        int priority = DIA_EME_TIERS.indexOf(tier) + 1;
        CoverageBucket newBucket = CoverageBucket.create(
            patch, tier, props.getDiaEmeCoverageTarget(), priority);
        log.info("CoverageBucket 자동 생성: patch={} tier={}", patch, tier);
        return coverageBucketRepository.save(newBucket);
      });
}
```

#### `DailySeedRunnerTest.java` — 추가 테스트

- `hasWorkToday` — 버킷이 없으면 true 반환
- `runNextChunk` — 버킷이 없으면 `save` 호출 후 DIA 페이지 실행

---

### Phase 2: 사이클 기반 일일 할당량

> Phase 1 안정화 후 진행

#### DB 마이그레이션

```sql
ALTER TABLE coverage_bucket
  ADD COLUMN daily_pages_processed INT NOT NULL DEFAULT 0,
  ADD COLUMN daily_cycle_count     INT NOT NULL DEFAULT 0;
```

#### `CoverageBucket.java` — 필드 추가

```java
@Builder.Default
@Column(name = "daily_pages_processed", nullable = false)
private int dailyPagesProcessed = 0;

@Builder.Default
@Column(name = "daily_cycle_count", nullable = false)
private int dailyCycleCount = 0;
```

#### `CoverageBucket.java` — 메서드 변경 및 추가

**`advanceToNextDivision()`** (신규) — 빈 응답 시 division 전환:

```java
/**
 * 빈 페이지 응답 시 호출.
 * 현재 division이 소진됐으므로 다음 division의 1페이지로 이동.
 * IV → I wrap 발생 시 사이클 카운트 증가.
 */
public void advanceToNextDivision() {
  int idx = DIVISIONS.indexOf(this.seedDivision);
  boolean cycleCompleted = (idx == DIVISIONS.size() - 1);
  this.seedDivision = DIVISIONS.get((idx + 1) % DIVISIONS.size());
  this.seedPage = 1;
  if (cycleCompleted) {
    this.dailyCycleCount++;
  }
  this.updatedAt = OffsetDateTime.now();
}
```

**`advanceSeedState()`** — 정상 응답 시 page만 증가, safety cap 도달 시 division 전환:

```java
public void advanceSeedState(int maxPagesPerDivision) {
  if (APEX_TIERS.contains(this.tier)) {
    this.seedPage++;
  } else if (this.seedPage >= maxPagesPerDivision) {
    // safety cap 도달: 실제로는 빈 응답이 먼저 오는 것이 정상
    advanceToNextDivision();
  } else {
    this.seedPage++;
  }
  this.updatedAt = OffsetDateTime.now();
}
```

**`hasRemainingDailyQuota()`** (신규):

```java
public boolean hasRemainingDailyQuota(int quota) {
  return dailyPagesProcessed < quota;
}
```

**`incrementDailyPagesProcessed()`** (신규):

```java
public void incrementDailyPagesProcessed() {
  this.dailyPagesProcessed++;
  this.updatedAt = OffsetDateTime.now();
}
```

**`resetDailySeedIfNeeded()`** — `dailyPagesProcessed`만 리셋, `seedPage` / `seedDivision` 유지:

```java
public void resetDailySeedIfNeeded(OffsetDateTime lastResetAt, LocalDate today) {
  boolean needsReset = lastResetAt == null || lastResetAt.toLocalDate().isBefore(today);
  if (!needsReset) return;
  this.dailySeedCompleted = false;      // deprecated 예정
  this.dailyPagesProcessed = 0;         // 오늘 처리량만 리셋
  this.dailySeedResetAt = today.atStartOfDay().atOffset(OffsetDateTime.now().getOffset());
  this.updatedAt = OffsetDateTime.now();
  // seedPage, seedDivision은 건드리지 않음 — 사이클 위치 유지
}
```

#### `PipelineProperties.java`

```java
// 추가
/** DIA/EME 버킷당 하루에 처리할 최대 페이지 수 */
private int diaEmeDailyPageQuota = 10;
```

#### `DailySeedRunner.java` — quota 기반으로 전환

`hasUncompletedDiaEmeBucket()` → quota 잔여 여부로 판단:

```java
for (Tier tier : DIA_EME_TIERS) {
  Optional<CoverageBucket> opt = coverageBucketRepository.findByPatchAndTier(currentPatch, tier);
  if (opt.isEmpty() || opt.get().hasRemainingDailyQuota(props.getDiaEmeDailyPageQuota())) {
    return true;
  }
}
```

`runNextChunk()` DIA/EME 루프:

```java
for (Tier tier : DIA_EME_TIERS) {
  CoverageBucket bucket = getOrCreateBucket(currentPatch, tier);
  if (bucket.hasRemainingDailyQuota(props.getDiaEmeDailyPageQuota())) {
    return runDiaEmePage(bucket, tier);
  }
}
```

`runDiaEmePage()` — 빈 응답 시 division 전환, 항상 quota 카운트:

```java
if (result.entriesFetched() == 0) {
  bucket.advanceToNextDivision();                          // 빈 응답 = division 소진
} else {
  bucket.advanceSeedState(props.getMaxPagesPerDivision()); // 정상: page 증가
}
bucket.incrementDailyPagesProcessed();                     // 빈 페이지 포함 항상 카운트
coverageBucketRepository.save(bucket);
// markDailySeedCompleted() 호출 제거
```

#### `DailySeedRunnerTest.java` — 추가 테스트 시나리오

- `hasWorkToday` — quota 소진 시 false 반환
- `runNextChunk` — 정상 응답 시 page 증가, quota 카운트
- `runNextChunk` — 빈 응답 시 `advanceToNextDivision` 호출, quota 카운트
- `runNextChunk` — quota 소진 시 NO_WORK 반환

#### `CoverageBucketTest.java` — 추가 테스트 시나리오

- `advanceToNextDivision` — I → II, II → III, III → IV 전환
- `advanceToNextDivision` — IV → I wrap 시 `dailyCycleCount` 증가
- `resetDailySeedIfNeeded` — `dailyPagesProcessed`는 0으로 리셋, `seedPage` / `seedDivision`은 유지
- `hasRemainingDailyQuota` — quota 미소진 시 true, 소진 시 false

---

### Phase 3: 정리 (추후)

- `dailySeedCompleted` 필드 및 `daily_seed_completed` 컬럼 제거
- `markDailySeedCompleted()` 메서드 제거

---

## 5. 리스크

| 리스크 | 완화 방안 |
|---|---|
| 빈 응답이 연속 발생해 quota를 낭비 | 빈 응답도 quota 1 카운트 + division 즉시 전환 |
| IV → I wrap 감지 오류로 `dailyCycleCount` 부정확 | `advanceToNextDivision` 단위 테스트로 각 transition 검증 |
| `diaEmeDailyPageQuota`가 seed 예산보다 과도하게 크면 다른 Stage 영향 | 기본값 10으로 보수적 설정 |
| 자정 리셋 호출 누락 시 `dailyPagesProcessed`가 무한 유지 | `resetDailySeedIfNeeded` 호출 경로 전수 확인 |

---

## 6. 성공 기준

### Phase 1
- [ ] DIA/EME 버킷이 없으면 `runNextChunk` 최초 진입 시 자동 생성된다
- [ ] `hasWorkToday`가 버킷 없음을 "작업 있음"으로 올바르게 인식한다
- [ ] 자동 생성된 버킷이 DB에 저장(`save`)된다

### Phase 2
- [ ] 하루 최대 `diaEmeDailyPageQuota` 페이지만 처리하고 멈춘다
- [ ] 빈 응답 발생 시 `seedDivision`이 다음 division으로 전환되고 `seedPage`는 1로 초기화된다
- [ ] IV/N 빈 응답 시 I/1로 wrap되고 `dailyCycleCount`가 증가한다
- [ ] 자정 경계를 넘으면 `dailyPagesProcessed`만 0으로 리셋되고, `seedPage` / `seedDivision`은 유지된다
- [ ] `markDailySeedCompleted()` 호출이 제거된다
- [ ] 신규/수정 테스트 모두 통과, `@DisplayName` 한글 작성
