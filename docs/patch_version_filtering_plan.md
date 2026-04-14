# Patch Version Filtering 구현 계획

> 작성일: 2026-04-09
> 대상: match_queue 병목 해소 — 이전 패치 매치 필터링 + Data Dragon 자동 감지

---

## 1. 문제 정의

### 현상

`SeedBootstrapExecutor`와 `RefreshSummonerMatches`가 Riot Match-v5 API에서 matchId를 가져올 때
**시간 필터 없이** 최근 매치를 조회한다. 소환사가 이전 패치(예: 15.22)에서 플레이한 매치도
`match_queue`에 적재되고, `IngestMatchDetail`이 해당 매치를 처리하면 현재 패치(15.23)가 아닌
데이터가 `bottom_duo_raw`에 저장된다.

### 결과

- `match_queue`에 이전 패치 매치가 대량으로 쌓여 **불필요한 Riot API 호출** 발생
- `bottom_duo_raw`에 혼합 패치 데이터가 들어가 **통계 오염**
- Riot API rate limit 예산 낭비로 현재 패치 데이터 수집 속도 저하
- `CoverageBucket.targetMatchCount=100` 조건을 채우는 데 과도한 시간 소요

### 목표

1. 현재 패치 시작 시점을 기준으로 matchId 조회를 필터링한다 (입구 차단)
2. `match_queue`에 patch 정보를 stamp하여 ingest 단계에서도 검증할 수 있게 한다 (안전망)
3. Data Dragon 자동 감지로 새 패치 릴리스를 수동 개입 없이 처리한다 (자동화)

---

## 2. 접근 방식 분석

### 선택: 접근법 D (A + C 결합, match_queue.patch 포함)

| 접근법 | 설명 | 판정 |
|--------|------|------|
| A. startTime 필터링 | Match-v5 API 호출 시 `startTime` 파라미터로 현재 패치 이후만 조회 | ✅ 채택 — 입구 차단 |
| B. enqueue 후 matchId 필터 | match_queue 적재 후 gameVersion 체크 | ❌ 불가 — matchId에 패치 정보 없음, 추가 API 호출 필요 |
| C. ingest 시점 검증 | `IngestMatchDetail`에서 patch 불일치 시 폐기 | ✅ 채택 — 안전망 역할 |
| D. A + C + match_queue.patch | 입구 필터 + queue patch stamp + 출구 안전망 | ✅ **최종 선택** |

접근법 D를 선택한다. `startTime` 필터링(A)이 대부분의 이전 패치 매치를 차단하고,
`match_queue.patch` stamp가 ingest 단계에서 명시적인 기대 패치 정보를 제공하며,
`IngestMatchDetail` 검증(C)이 패치 전환기의 edge case를 처리한다.

---

## 3. match_queue.patch 컬럼 필요성

`WorkItem`은 이미 `patch`와 `tier`를 가진다. `INGEST_MATCH_DETAIL` 타입의 `WorkItem`은
독립적으로 실행되므로, 어떤 패치 문맥(context)에서 처리해야 하는지를 명시해야 한다.

### 현재 흐름 (문제)

```
WorkItem(patch="15.23", tier=GOLD)
  → IngestMatchDetailWorker.execute(workItem)
  → matchIngestWorker.execute(workItem.getBatchLimit(), workItem.getTier())
                                        // ❌ patch가 전달되지 않음
  → queue.pickAndLock() → Item(matchId, tier, priority)
                                        // ❌ Item에 patch 없음
  → ingestMatchDetail.execute(matchId, tier)
                                        // ❌ 어떤 패치 기대값인지 모름
```

### 개선 후 흐름

```
[enqueue 시점]
MatchQueueEnqueuer.enqueueAllIdempotent(matchIds, tier, priority, patch="15.23")
  → match_queue row: {matchId, collectionTier, priority, patch="15.23", ...}

[ingest 시점]
WorkItem(patch="15.23", tier=GOLD)
  → IngestMatchDetailWorker.execute(workItem)
  → matchIngestWorker.execute(batchLimit, tier, workItem.getPatch())
  → queue.pickAndLock() → Item(matchId, tier, priority, patch="15.23")
  → ingestMatchDetail.execute(matchId, tier, expectedPatch="15.23")
  → 매치 gameVersion이 "15.23"인지 검증 → 불일치 시 bottom_duo_raw 폐기
```

---

## 4. 아키텍처 개요

```
[PatchVersion 테이블]
    ↑ Phase 5: DataDragonPatchSyncScheduler (6시간마다 자동 감지)
    ↑ Phase 4: AdminPatchController (수동 관리 / 초기 데이터)
    │
    ▼
[PatchVersionService]
    │ currentPatchStartTimeEpochSeconds()
    │ currentPatchVersion()
    │
    ├── Phase 2a: SeedBootstrapExecutor
    │     findMatchIdsSince(puuid, patchStartTime, count)
    │     enqueueAllIdempotent(matchIds, tier, priority, currentPatch)  ← patch stamp
    │
    ├── Phase 2b: RefreshSummonerMatches
    │     startTime = max(lastMatchStartTime, patchStartTime)
    │     enqueueAllIdempotent(matchIds, tier, priority, currentPatch)  ← patch stamp
    │
    └── Phase 3: match_queue.patch → Item.patch → IngestMatchDetail
          execute(matchId, tier, expectedPatch) → 불일치 시 폐기
```

---

## 5. 구현 단계

### Phase 1: PatchVersion 엔티티 인프라 (신규 3파일)

**목적**: 패치 버전과 릴리스 시점을 저장하는 인프라 구축

#### Step 1.1: PatchVersion 엔티티

**파일(신규)**: `src/main/java/com/bestduo_BE/common/infra/persistence/entity/PatchVersion.java`

```java
@Entity
@Table(name = "patch_version")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PatchVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String patch;               // "15.23" (major.minor만)

    @Column(name = "released_at", nullable = false)
    private OffsetDateTime releasedAt;  // 최초 감지 시점 (근사값)

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    /** Riot API startTime 파라미터용 epoch seconds */
    public long releasedAtEpochSeconds() {
        return releasedAt.toEpochSecond();
    }

    public static PatchVersion of(String patch, OffsetDateTime releasedAt) {
        return PatchVersion.builder()
            .patch(patch)
            .releasedAt(releasedAt)
            .createdAt(OffsetDateTime.now())
            .build();
    }
}
```

- 엔티티 패턴: `CoverageBucket`과 동일 (`@Builder`, `@Getter`, `@NoArgsConstructor(PROTECTED)`)
- `patch` 컬럼에 unique 제약으로 중복 방지
- **위험도**: Low | **의존성**: 없음

#### Step 1.2: PatchVersionJpaRepository

**파일(신규)**: `src/main/java/com/bestduo_BE/common/infra/persistence/repository/PatchVersionJpaRepository.java`

```java
public interface PatchVersionJpaRepository extends JpaRepository<PatchVersion, Long> {
    boolean existsByPatch(String patch);
    Optional<PatchVersion> findByPatch(String patch);
    Optional<PatchVersion> findTopByOrderByReleasedAtDesc();
}
```

- `findTopByOrderByReleasedAtDesc()`: 가장 최신 패치 조회
- **위험도**: Low | **의존성**: Step 1.1

#### Step 1.3: PatchVersionService

**파일(신규)**: `src/main/java/com/bestduo_BE/common/application/PatchVersionService.java`

```java
@Service
@RequiredArgsConstructor
public class PatchVersionService {

    private final PatchVersionJpaRepository patchVersionRepository;

    /** 최신 패치의 릴리스 시점 (epoch seconds). 데이터 없으면 Optional.empty() */
    public Optional<Long> currentPatchStartTimeEpochSeconds() {
        return patchVersionRepository.findTopByOrderByReleasedAtDesc()
            .map(PatchVersion::releasedAtEpochSeconds);
    }

    /** 최신 패치 문자열 (e.g., "15.23"). 데이터 없으면 Optional.empty() */
    public Optional<String> currentPatchVersion() {
        return patchVersionRepository.findTopByOrderByReleasedAtDesc()
            .map(PatchVersion::getPatch);
    }

    /** 새 패치 등록 (멱등: 이미 존재하면 false 반환) */
    public boolean registerIfAbsent(String patch, OffsetDateTime releasedAt) {
        if (patchVersionRepository.existsByPatch(patch)) {
            return false;
        }
        patchVersionRepository.save(PatchVersion.of(patch, releasedAt));
        return true;
    }
}
```

- **위험도**: Low | **의존성**: Step 1.1, 1.2

---

### Phase 2: match_queue에 patch 컬럼 추가 + Match ID 조회 필터링 (기존 5파일 수정)

**목적**: enqueue 시점에 patch를 stamp하고, matchId 조회 범위를 현재 패치로 제한

#### Step 2.1: MatchQueue 엔티티에 patch 컬럼 추가

**파일(수정)**: `src/main/java/com/bestduo_BE/common/infra/persistence/entity/MatchQueue.java`

- `patch` 필드 추가 (`String`, nullable 허용 — 마이그레이션 기간 중 기존 행 대응)
- `newReady()` 팩토리 메서드에 `patch` 파라미터 추가

```java
// 변경 전
public static MatchQueue newReady(String matchId, String collectionTier, int priority)

// 변경 후
@Column(name = "patch")
private String patch;  // nullable (기존 행 호환)

public static MatchQueue newReady(String matchId, String collectionTier, int priority, String patch) {
    OffsetDateTime now = OffsetDateTime.now();
    return MatchQueue.builder()
        .matchId(matchId)
        .status("READY")
        .priority(priority)
        .collectionTier(collectionTier)
        .patch(patch)
        .retryCount(0)
        .lastError(null)
        .lockedAt(null)
        .createdAt(now)
        .updatedAt(now)
        .build();
}
```

- **위험도**: Low (`ddl-auto: update`로 컬럼 자동 추가, nullable이므로 기존 행 영향 없음)
- **의존성**: 없음

#### Step 2.2: MatchQueueEnqueuer 인터페이스에 patch 파라미터 추가

**파일(수정)**: `src/main/java/com/bestduo_BE/common/application/port/MatchQueueEnqueuer.java`

```java
// 변경 전
void enqueueAllIdempotent(List<String> matchIds, Tier tier, int priority);

// 변경 후
void enqueueAllIdempotent(List<String> matchIds, Tier tier, int priority, String patch);
```

#### Step 2.3: MatchQueueEnqueuerImpl 구현체 수정

**파일(수정)**: `src/main/java/com/bestduo_BE/common/infra/persistence/MatchQueueEnqueuerImpl.java`

```java
@Override
@Transactional
public void enqueueAllIdempotent(List<String> matchIds, Tier tier, int priority, String patch) {
    for (String matchId : matchIds) {
        if (repo.existsById(matchId)) continue;
        repo.save(MatchQueue.newReady(matchId, tier.name(), priority, patch));
    }
}
```

#### Step 2.4: MatchQueueDispatcher.Item에 patch 필드 추가

**파일(수정)**: `src/main/java/com/bestduo_BE/ingest/application/port/MatchQueueDispatcher.java`

```java
// 변경 전
record Item(String matchId, Tier tier, int priority) {}

// 변경 후
record Item(String matchId, Tier tier, int priority, String patch) {}
```

**파일(수정)**: `src/main/java/com/bestduo_BE/ingest/infra/persistence/MatchQueueDispatcherImpl.java`

```java
// toItems() 메서드 수정
private List<Item> toItems(List<MatchQueue> list) {
    List<Item> out = new ArrayList<>();
    for (MatchQueue mq : list) {
        Tier tier = Tier.valueOf(mq.getCollectionTier());
        out.add(new Item(mq.getMatchId(), tier, mq.getPriority(), mq.getPatch()));
    }
    return out;
}
```

#### Step 2.5: SeedBootstrapExecutor — startTime 필터링 + patch stamp

**파일(수정)**: `src/main/java/com/bestduo_BE/seed/application/SeedBootstrapExecutor.java`

- 생성자에 `PatchVersionService` 주입
- `enqueueRecentMatches()` 수정:

```java
private void enqueueRecentMatches(String puuid, SeedBootstrapCommand cmd, SeedBootstrapProgress progress) {
    Optional<Long> patchStartTime = patchVersionService.currentPatchStartTimeEpochSeconds();
    String currentPatch = patchVersionService.currentPatchVersion().orElse(null);

    List<String> matchIds;
    if (patchStartTime.isPresent()) {
        matchIds = matchIdsFinder.findMatchIdsSince(puuid, patchStartTime.get(), cmd.matchesPerPuuid());
    } else {
        // 폴백: PatchVersion 데이터 없으면 기존 동작 유지
        matchIds = matchIdsFinder.findRecentMatchIds(puuid, cmd.matchesPerPuuid());
    }

    matchQueueEnqueuer.enqueueAllIdempotent(matchIds, cmd.tier(), cmd.priority(), currentPatch);
    // ... 기존 progress 업데이트 로직
}
```

- **폴백**: PatchVersion 데이터 없으면 기존 동작(시간 필터 없이 조회) 유지
- **위험도**: Medium — 기존 동작 변경, 단 폴백으로 안전
- **의존성**: Phase 1, Step 2.1~2.3

#### Step 2.6: RefreshSummonerMatches — startTime 하한 적용 + patch stamp

**파일(수정)**: `src/main/java/com/bestduo_BE/refresh/application/RefreshSummonerMatches.java`

- 생성자에 `PatchVersionService` 주입
- `loadMatchIds()` 수정:

```java
private List<String> loadMatchIds(String puuid, Long lastMatchStartTimeSecOrNull) {
    Optional<Long> patchStart = patchVersionService.currentPatchStartTimeEpochSeconds();
    long effectiveStartTime = patchStart.orElse(0L);

    // lastMatchStartTime과 patchStartTime 중 더 큰 값 사용
    if (lastMatchStartTimeSecOrNull != null && lastMatchStartTimeSecOrNull > effectiveStartTime) {
        effectiveStartTime = lastMatchStartTimeSecOrNull;
    }

    if (effectiveStartTime > 0) {
        return matchIdsFinder.findMatchIdsSince(puuid, effectiveStartTime, FETCH_COUNT);
    }
    return matchIdsFinder.findRecentMatchIds(puuid, FETCH_COUNT);
}
```

- enqueue 호출 시 `currentPatch`를 함께 전달
- **핵심**: 패치가 바뀌면 자동으로 새 패치 시작 시점부터 조회
- **위험도**: Medium | **의존성**: Phase 1, Step 2.1~2.3

---

### Phase 3: Ingest 단계 patch 검증 안전망 (기존 3파일 수정)

**목적**: match_queue.patch를 통해 기대 패치를 IngestMatchDetail까지 전달하고, 불일치 시 폐기

#### Step 3.1: MatchIngestWorker에 patch 파라미터 추가

**파일(수정)**: `src/main/java/com/bestduo_BE/ingest/application/MatchIngestWorker.java`

```java
// 변경 전
public Result execute(int limit, Tier requestedTier)

// 변경 후
public Result execute(int limit, Tier requestedTier, String expectedPatch)

// 내부 처리
for (MatchQueueDispatcher.Item item : items) {
    var r = ingestMatchDetail.execute(item.matchId(), item.tier(), item.patch());
    // item.patch()가 match_queue에 stamp된 패치 (enqueue 시점의 current patch)
    ...
}
```

#### Step 3.2: IngestMatchDetailWorker에서 patch 전달

**파일(수정)**: `src/main/java/com/bestduo_BE/workitem/application/worker/IngestMatchDetailWorker.java`

```java
@Override
public void execute(WorkItem workItem) {
    // workItem.getPatch()를 matchIngestWorker에 전달
    matchIngestWorker.execute(workItem.getBatchLimit(), workItem.getTier(), workItem.getPatch());
}
```

#### Step 3.3: IngestMatchDetail에 expectedPatch 파라미터 추가 + 검증

**파일(수정)**: `src/main/java/com/bestduo_BE/ingest/application/IngestMatchDetail.java`

```java
// 변경 전
public IngestResult execute(String matchId, Tier tier)

// 변경 후
@Transactional
public IngestResult execute(String matchId, Tier tier, String expectedPatch) {
    RiotMatchDto match = loadMatch(matchId);
    saveMatch(matchId, match);
    List<BottomDuoRaw> raws = extractBottomDuoRaws(matchId, match, tier);

    // 안전망: 기대 패치와 불일치하는 row 폐기
    if (expectedPatch != null) {
        List<BottomDuoRaw> filtered = raws.stream()
            .filter(r -> expectedPatch.equals(r.patch()))
            .toList();
        if (filtered.size() < raws.size()) {
            log.warn("[PatchFilter] Discarded {} raws for matchId={} (expected={}, actual varied)",
                raws.size() - filtered.size(), matchId, expectedPatch);
        }
        raws = filtered;
    }

    saveBottomDuoRaws(raws);
    expandParticipants(match);
    Long startSec = extractMatchStartTimeSec(match);
    return new IngestResult(raws.size(), startSec);
}
```

- **핵심**: `BottomDuoExtractor.toPatch()`가 이미 `gameVersion`을 `"15.23"` 형식으로 정규화하므로 문자열 비교로 충분
- `expectedPatch == null` (기존 match_queue 행 또는 PatchVersion 데이터 없는 경우)이면 필터링 없이 기존 동작
- **위험도**: Low | **의존성**: Step 2.4

---

### Phase 4: 초기 데이터 + Admin API (신규 2파일)

**목적**: 패치 버전 수동 관리 및 초기 시드 데이터 투입 경로 제공

#### Step 4.1: AdminPatchController

**파일(신규)**: `src/main/java/com/bestduo_BE/common/presentation/api/AdminPatchController.java`

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/patch")
public class AdminPatchController {
    private final PatchVersionService patchVersionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PatchVersionResponse register(
        @RequestParam String patch,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime releasedAt
    ) { ... }

    @GetMapping("/current")
    public PatchVersionResponse current() { ... }
}
```

- 기존 `AdminCoverageController` 패턴 준수 (`/admin/{domain}`, `@RequestParam`)
- **위험도**: Low | **의존성**: Phase 1

#### Step 4.2: 초기 시드 데이터

Phase 5 자동 감지 전, Admin API로 현재 패치를 수동 등록:

```bash
curl -X POST '/admin/patch?patch=15.23&releasedAt=2026-04-02T00:00:00Z'
```

Phase 5가 배포되면 이후 패치는 자동 등록된다.

---

### Phase 5: Data Dragon 자동 패치 감지 (필수, 신규 1파일 + 설정 수정)

**목적**: 새 패치 릴리스를 수동 개입 없이 자동 감지 및 등록

#### Step 5.1: DataDragonPatchSyncScheduler

**파일(신규)**: `src/main/java/com/bestduo_BE/common/infra/champion/DataDragonPatchSyncScheduler.java`

**패키지 선택 근거**: 기존 `DataDragonChampionDataSource`가 `common.infra.champion` 패키지에 있으며,
동일한 외부 시스템(Data Dragon API)에 대한 인프라 관심사이므로 같은 패키지에 배치한다.

```java
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "patch-sync.enabled", havingValue = "true", matchIfMissing = true)
public class DataDragonPatchSyncScheduler {

    private static final String VERSIONS_URL = "https://ddragon.leagueoflegends.com/api/versions.json";

    private final RestTemplate restTemplate;
    private final PatchVersionService patchVersionService;

    /**
     * 6시간마다 Data Dragon versions.json을 폴링하여 새 패치를 감지한다.
     * 패치 주기가 보통 2주이므로 6시간은 충분히 빠른 감지 주기이다.
     */
    @Scheduled(fixedDelayString = "${patch-sync.interval-ms:21600000}")
    public void syncLatestPatch() {
        try {
            String[] versions = restTemplate.getForObject(VERSIONS_URL, String[].class);
            if (versions == null || versions.length == 0) {
                log.warn("[PatchSync] versions.json returned empty");
                return;
            }

            // versions.json은 최신 순으로 정렬. 첫 번째가 최신 버전.
            String latestFullVersion = versions[0];
            String normalizedPatch = normalizePatch(latestFullVersion);

            if (normalizedPatch == null) {
                log.warn("[PatchSync] Failed to normalize version: {}", latestFullVersion);
                return;
            }

            boolean registered = patchVersionService.registerIfAbsent(
                normalizedPatch,
                OffsetDateTime.now()  // 근사값: 최초 감지 시점을 releasedAt으로 사용
            );

            if (registered) {
                log.info("[PatchSync] New patch registered: {} (from {})",
                    normalizedPatch, latestFullVersion);
            } else {
                log.debug("[PatchSync] Patch already exists: {}", normalizedPatch);
            }

        } catch (Exception e) {
            // Data Dragon 장애 시에도 기존 패치 데이터로 계속 운영
            log.error("[PatchSync] Failed to sync from Data Dragon", e);
        }
    }

    /**
     * "15.23.1" → "15.23" (major.minor만 추출)
     * BottomDuoExtractor.toPatch()와 동일한 정규화 로직.
     */
    static String normalizePatch(String fullVersion) {
        if (fullVersion == null || fullVersion.isBlank()) {
            return null;
        }
        String[] parts = fullVersion.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        return parts[0] + "." + parts[1];
    }
}
```

**주요 설계 결정**

| 항목 | 결정 | 근거 |
|------|------|------|
| API | `ddragon.leagueoflegends.com/api/versions.json` | 공식 버전 목록, 최신순 정렬 |
| 정규화 | `15.23.1` → `15.23` | `BottomDuoExtractor.toPatch()`와 동일 로직 |
| 스케줄링 | `@Scheduled(fixedDelay)` | 기존 `CoverageScheduler` 패턴 동일 |
| 주기 | 6시간 (21,600,000ms) | 패치 주기 ~2주 대비 충분 |
| 중복 방지 | `PatchVersionService.registerIfAbsent()` | `existsByPatch()` 체크 후 저장 (멱등) |
| `releasedAt` | 최초 감지 시점 | Data Dragon API에 릴리스 시각 정보 없음 |
| 실패 처리 | catch + log, 다음 주기 재시도 | 장애 시에도 기존 패치로 계속 운영 |
| Feature flag | `@ConditionalOnProperty(patch-sync.enabled)` | 환경별 on/off 가능 |
| RestTemplate | 생성자 주입 | 테스트 용이성 (기존 `DataDragonChampionDataSource`는 `new RestTemplate()` 사용하나 개선) |

**`releasedAt` 근사값 한계**

최초 감지 시점을 `releasedAt`으로 사용하므로 실제 릴리스보다 최대 6시간 늦을 수 있다.
- 영향: 패치 전환 직후 6시간 이내의 신규 패치 매치가 일부 누락될 수 있으나, Phase 3 안전망이 방어
- 보정: Admin API(`/admin/patch`)로 정확한 `releasedAt`을 수동 등록하면 override 가능

#### Step 5.2: application.yml에 patch-sync 설정 추가

**파일(수정)**: `src/main/resources/application.yml`

```yaml
patch-sync:
  enabled: ${PATCH_SYNC_ENABLED:true}
  interval-ms: ${PATCH_SYNC_INTERVAL_MS:21600000}  # 6시간
```

#### Step 5.3: DataDragonChampionDataSource의 하드코딩 VERSION 개선 (선택)

**파일(수정)**: `src/main/java/com/bestduo_BE/common/infra/champion/DataDragonChampionDataSource.java`

현재 `VERSION = "15.23.1"`이 하드코딩되어 있다. `DataDragonPatchSyncScheduler`가 versions.json을
이미 호출하므로, 챔피언 데이터 로딩도 동적 버전을 사용하도록 개선할 수 있다.

---

## 6. 파일 변경 요약

Base 경로: `src/main/java/com/bestduo_BE/`

| # | 파일 경로 | 작업 | Phase |
|---|-----------|------|-------|
| 1 | `common/infra/persistence/entity/PatchVersion.java` | **신규** | 1 |
| 2 | `common/infra/persistence/repository/PatchVersionJpaRepository.java` | **신규** | 1 |
| 3 | `common/application/PatchVersionService.java` | **신규** | 1 |
| 4 | `common/infra/persistence/entity/MatchQueue.java` | **수정** — patch 컬럼 추가 | 2 |
| 5 | `common/application/port/MatchQueueEnqueuer.java` | **수정** — patch 파라미터 추가 | 2 |
| 6 | `common/infra/persistence/MatchQueueEnqueuerImpl.java` | **수정** — patch 전달 | 2 |
| 7 | `ingest/application/port/MatchQueueDispatcher.java` | **수정** — Item에 patch 추가 | 2 |
| 8 | `ingest/infra/persistence/MatchQueueDispatcherImpl.java` | **수정** — toItems()에 patch 포함 | 2 |
| 9 | `seed/application/SeedBootstrapExecutor.java` | **수정** — startTime 필터 + patch stamp | 2 |
| 10 | `refresh/application/RefreshSummonerMatches.java` | **수정** — startTime 하한 + patch stamp | 2 |
| 11 | `ingest/application/MatchIngestWorker.java` | **수정** — expectedPatch 파라미터 추가 | 3 |
| 12 | `workitem/application/worker/IngestMatchDetailWorker.java` | **수정** — patch 전달 | 3 |
| 13 | `ingest/application/IngestMatchDetail.java` | **수정** — patch 검증 안전망 | 3 |
| 14 | `common/presentation/api/AdminPatchController.java` | **신규** | 4 |
| 15 | `common/infra/champion/DataDragonPatchSyncScheduler.java` | **신규** | 5 |
| 16 | `src/main/resources/application.yml` | **수정** — patch-sync 설정 | 5 |
| 17 | `common/infra/champion/DataDragonChampionDataSource.java` | **수정** (선택) — 동적 버전 | 5 |

---

## 7. 테스트 전략

### Phase 1

| 파일 | 유형 | 검증 항목 |
|------|------|-----------|
| `PatchVersionServiceTest.java` | Unit | `currentPatchStartTimeEpochSeconds()`, `registerIfAbsent()` 멱등성, 데이터 없을 때 `Optional.empty()` |
| `PatchVersionJpaRepositoryTest.java` | Integration | `findTopByOrderByReleasedAtDesc()` 정렬 정확성, unique 제약 |

### Phase 2

| 파일 | 유형 | 검증 항목 |
|------|------|-----------|
| `MatchQueueTest.java` | Unit | `newReady()` patch 포함 생성 |
| `MatchQueueEnqueuerImplTest.java` | Integration | patch가 DB에 저장되는지 |
| `SeedBootstrapExecutorTest.java` | Unit | PatchVersion 있을 때 `findMatchIdsSince()` 호출, 없을 때 폴백 |
| `RefreshSummonerMatchesTest.java` | Unit | `max(lastMatchStartTime, patchStartTime)` 로직 |

### Phase 3

| 파일 | 유형 | 검증 항목 |
|------|------|-----------|
| `IngestMatchDetailTest.java` | Unit | `expectedPatch="15.23"`에서 `gameVersion="15.22.x"` 매치 폐기, `null` patch 시 필터 없음 |
| `MatchIngestWorkerTest.java` | Unit | item.patch()가 `ingestMatchDetail.execute()`에 올바르게 전달되는지 |

### Phase 5

| 파일 | 유형 | 검증 항목 |
|------|------|-----------|
| `DataDragonPatchSyncSchedulerTest.java` | Unit | `normalizePatch("15.23.1")` → `"15.23"`, API 실패 시 예외 미전파, 중복 패치 스킵 |

### 정규화 일관성 테스트 (중요)

`BottomDuoExtractor.toPatch()`와 `DataDragonPatchSyncScheduler.normalizePatch()`가 동일한
입력에 대해 동일한 결과를 내는지 파라미터화 테스트로 교차 검증한다.

```java
@ParameterizedTest
@CsvSource({
    "15.23.1, 15.23",
    "15.23.456.7890, 15.23",
    "14.1.0, 14.1"
})
void normalizationIsConsistent(String fullVersion, String expected) {
    assertThat(DataDragonPatchSyncScheduler.normalizePatch(fullVersion)).isEqualTo(expected);
    // BottomDuoExtractor.toPatch()도 같은 결과여야 함
}
```

---

## 8. 구현 순서 및 배포 전략

```
Phase 1 (PatchVersion 인프라)
    ↓
Phase 4 (Admin API) — 초기 데이터 투입 경로 먼저 확보
    ↓
Phase 5 (Data Dragon 자동 감지) — 자동화 구축
    ↓
Phase 2 (match_queue.patch + startTime 필터링) — 핵심 병목 해소
    ↓
Phase 3 (Ingest 안전망) — patch 정보 흐름 완성
```

각 Phase는 독립 배포 가능하다. Phase 2 배포 전에 Phase 4/5로 `PatchVersion` 데이터를 투입해야
필터링이 즉시 작동한다. PatchVersion 데이터가 없어도 모든 곳에 폴백이 있어 기존 파이프라인은 유지된다.

---

## 9. 위험 및 완화

| 위험 | 심각도 | 완화 |
|------|--------|------|
| PatchVersion 데이터 없이 Phase 2 배포 | Medium | `Optional` 폴백으로 기존 동작 유지. Phase 4/5를 먼저 배포 |
| `releasedAt` 근사값으로 패치 전환기 데이터 누락/혼입 | Medium | Phase 3 안전망 방어. Admin API로 정확한 시각 수동 보정 가능 |
| Data Dragon API 다운타임 | Low | catch + log, 다음 주기 재시도. 기존 패치 데이터로 계속 운영 |
| 정규화 로직 불일치 (`toPatch` vs `normalizePatch`) | High | 교차 검증 파라미터화 테스트. 향후 공통 유틸 추출 고려 |
| `match_queue.patch` null (기존 행) | Low | `IngestMatchDetail`에서 `expectedPatch == null` 체크로 필터 생략 |
| 기존 테스트 컴파일 오류 (`MatchQueue.newReady` 시그니처 변경) | Medium | 기존 호출부에 `null` 또는 현재 패치 값 전달로 수정 |

---

## 10. 성공 기준

- [ ] `PatchVersion` 테이블이 생성되고 Admin API로 CRUD 가능하다
- [ ] `DataDragonPatchSyncScheduler`가 6시간마다 새 패치를 자동 감지하여 등록한다
- [ ] `match_queue` 행에 `patch` 컬럼이 존재하고, enqueue 시 현재 패치가 stamp된다
- [ ] `SeedBootstrapExecutor`가 현재 패치 시작 시점 이후의 matchId만 조회한다
- [ ] `RefreshSummonerMatches`가 `max(lastMatchStartTime, patchStartTime)` 이후만 조회한다
- [ ] `IngestMatchDetailWorker` → `MatchIngestWorker` → `IngestMatchDetail`로 patch 정보가 전달된다
- [ ] `IngestMatchDetail`이 `expectedPatch`와 불일치하는 `bottom_duo_raw`를 폐기하고 로그에 기록한다
- [ ] PatchVersion 데이터가 없어도 기존 파이프라인이 정상 동작한다 (폴백)
- [ ] `normalizePatch("15.23.1")` → `"15.23"` 교차 검증 테스트가 통과한다
- [ ] 모든 Phase 단위 테스트가 통과한다
