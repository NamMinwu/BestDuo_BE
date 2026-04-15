# 리팩토링 종합 계획 (Architecture + Readability)

> 작성일: 2026-04-15
> 범위: bestduo_BE_dev (Spring Boot 4.0.0, Java 21, JPA, PostgreSQL)
> 규모: 약 119 파일 / 5,767 LOC

---

## 0. 방침

- **전면 헥사고날은 오버엔지니어링** → "경량 헥사고날" 채택
  - 외부 I/O 경계(Riot API)만 Port화
  - JPA 엔티티 = 도메인 모델 유지 (이중화 회피)
- **Sealed state는 접근 A(enum)** 채택
  - `@Enumerated(EnumType.STRING)`로 충분
  - sealed type으로 invalid state까지 차단하는 건 현 규모에 과함
- **DB 마이그레이션은 최소화** — 컬럼 값 유지, 코드만 교체

---

## 1. 아키텍처 개선

### A. `Tier.ALL_TIERS` sentinel 제거 — **High**

**현상**
```java
public enum Tier {
    CHALLENGER, GRANDMASTER, MASTER,
    DIAMOND, EMERALD, ...,
    ALL_TIERS;  // ← sentinel이 실제 티어와 섞임
}
```

**문제**
- LSP 위반: `Tier.ALL_TIERS`를 단일 티어처럼 넘기면 런타임 버그
- switch exhaustive 처리 시 누락/오용 위험

**제안**
```java
public enum Tier { CHALLENGER, GRANDMASTER, MASTER, DIAMOND, EMERALD, ... }

// 호출부 시그니처 변경
void process(Optional<Tier> scope);   // empty = 전체
// 또는
void process(TierScope scope);        // sealed: AllTiers | Single(Tier)
```

**Trade-off**
- 호출부 다수 수정 (변경 범위 중)
- 타입 안전성↑, 런타임 버그↓

---

### B. `MatchQueue.status` / `collectionTier` String → Enum — **High**

**현상**
```java
@Column(name = "status", nullable = false)
private String status; // READY/RUNNING/DONE/ERROR

@Column(name = "collection_tier", nullable = false)
private String collectionTier; // Tier enum name
```

**제안 (접근 A)**
```java
public enum QueueStatus { READY, RUNNING, DONE, ERROR }

@Enumerated(EnumType.STRING)
@Column(name = "status", nullable = false)
private QueueStatus status;

@Enumerated(EnumType.STRING)
@Column(name = "collection_tier", nullable = false)
private Tier collectionTier;

public void markRunning() {
    requireStatus(QueueStatus.READY);
    this.status = QueueStatus.RUNNING;
    this.lockedAt = OffsetDateTime.now();
    touch();
}

public void markDone() {
    requireStatus(QueueStatus.RUNNING);
    this.status = QueueStatus.DONE;
    this.lockedAt = null;
    touch();
}

public void markError(String message) {
    this.status = QueueStatus.ERROR;
    this.retryCount += 1;
    this.lastError = truncate(message);
    this.lockedAt = null;
    touch();
}

private void requireStatus(QueueStatus expected) {
    if (this.status != expected) {
        throw new IllegalStateTransitionException(matchId, this.status, expected);
    }
}
```

**Trade-off**
- DB 스키마 변경 불필요 (값은 그대로 문자열로 저장됨)
- 오타 컴파일 차단, switch exhaustive 가능
- 잘못된 전이 런타임에서 즉시 감지

---

### C. `DailyPipelineState.seedCompletedTiers` JSON 문자열 제거 — **Critical**

**현상**
```java
@Column(name = "seed_completed_tiers", nullable = false)
private String seedCompletedTiers = "[]";

public void recordSeedCompletedTier(String tier) {
    if (seedCompletedTiers.contains("\"" + tier + "\"")) return;
    String withoutClose = seedCompletedTiers.substring(0, seedCompletedTiers.length() - 1);
    String separator = seedCompletedTiers.equals("[]") ? "" : ",";
    this.seedCompletedTiers = withoutClose + separator + "\"" + tier + "\"]";
}
```

**문제**
- substring/concat 기반 JSON 수동 조립 — 극도로 취약
- 파싱/직렬화 에러 발생 시 디버깅 어려움

**제안 A (권장): `@ElementCollection`**
```java
@ElementCollection(fetch = FetchType.EAGER)
@CollectionTable(
    name = "daily_pipeline_state_seed_completed_tiers",
    joinColumns = @JoinColumn(name = "state_id")
)
@Enumerated(EnumType.STRING)
@Column(name = "tier")
private Set<Tier> seedCompletedTiers = new HashSet<>();

public void recordSeedCompletedTier(Tier tier) {
    seedCompletedTiers.add(tier);
}

public boolean isSeedCompleted(Tier tier) {
    return seedCompletedTiers.contains(tier);
}
```

**제안 B (대안): PostgreSQL `jsonb` + Hibernate `JsonType`**

**Trade-off**
- A: 쿼리 명확, 조인 1회 비용 소. SQL로 직접 조회 가능. **권장**
- B: 스키마 단순, 하지만 매퍼 라이브러리 의존성↑
- 마이그레이션 필요: 기존 `"[]"` 문자열 → 별도 테이블로 이관

---

### D. `IngestMatchDetail` — Match 저장과 Raw 필터 불일치 — **Critical**

**현상**
```java
matchSaver.save(matchId, match);  // ← 먼저 저장
List<BottomDuoRaw> raws = extractor.extract(matchId, match, tier);
if (expectedPatch != null) {
    List<BottomDuoRaw> filtered = raws.stream()
        .filter(r -> expectedPatch.equals(r.patch()))
        .toList();
    // raws 버리지만 Match는 이미 저장됨 → 고아 Match
}
```

**문제**
- 패치 불일치 시 `Match`는 남고 `BottomDuoRaw`는 사라짐 → 데이터 정합성 깨짐
- 집계 시 orphan Match 집계 대상 애매

**제안 (정책 결정 필요)**
1. **사전 필터** — Match 가져온 직후 패치 확인, 불일치면 저장 자체 skip
2. **트랜잭션 일치** — 사후 필터 유지하되 불일치 시 Match도 rollback
3. **명시적 마킹** — Match에 `ingested=false` 플래그로 저장만 하고 집계 제외

→ **Open Question #1로 확정 필요**

---

### E. `BottomDuoExtractor`를 Spring Bean으로 — **Medium**

**현상**
```java
// IngestMatchDetail.java
private final BottomDuoExtractor extractor = new BottomDuoExtractor();
```

**제안**
```java
@Component
public class BottomDuoExtractor { ... }

// IngestMatchDetail
private final BottomDuoExtractor extractor;
// 생성자 주입
```

**Trade-off**
- 없음 (순수 개선)
- 테스트 시 mock/fake 교체 가능

---

### F. `PipelineRunner` graceful shutdown — **Medium**

**현상**
```java
@PreDestroy
public void stop() {
    running = false;
    if (executor != null) executor.shutdownNow();  // ← interrupt만
}
```

**제안**
```java
@PreDestroy
public void stop() {
    running = false;
    if (executor == null) return;
    executor.shutdown();
    try {
        if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }
    } catch (InterruptedException e) {
        executor.shutdownNow();
        Thread.currentThread().interrupt();
    }
}
```

**Trade-off**
- 배포 시 종료 시간 최대 30초↑
- 진행 중 작업 데이터 정합성 보장

---

### G. Riot API Port 경량 추출 — **Medium (경량 헥사고날)**

**현상**: Riot API 호출이 여러 곳에서 직접 이루어짐

**제안**
```java
// application/port/out/
public interface RiotApiPort {
    List<String> fetchMatchIds(String puuid, RiotApiQuery query);
    Match fetchMatchDetail(String matchId);
    List<LeagueEntry> fetchLeagueEntries(Tier tier, Division div, int page);
}

// adapter/out/riot/
@Component
public class RiotApiHttpAdapter implements RiotApiPort { ... }
```

**Trade-off**
- 파일 +2~3개 증가
- 테스트에서 `FakeRiotApiAdapter`로 교체 가능 → 통합 테스트 속도↑

---

## 2. 가독성 개선

| # | 위치 | 이슈 | 개선 |
|---|---|---|---|
| R1 | `MatchIngestRunner` | `STALE_MINUTES=10`, `ERROR_COOLDOWN_MINUTES=10`, `MAX_RETRY=2` 하드코딩 | `@ConfigurationProperties("pipeline.ingest")`로 외부화 |
| R2 | `MatchIngestRunner.shorten()` | 500자 truncate vs entity 컬럼 255자 | 엔티티 기준 상수 1개로 통일 |
| R3 | `PatchVersionService` | `currentPatchStartTimeEpochSeconds` / `currentPatchVersion` / `currentPatch` 3중 게터 | `resolveEffectivePatchContext()` 중심 통합, 나머지 `@Deprecated` |
| R4 | `CollectMatchIdsRunner` | `catch (Exception)` 후 budget 차감 | 정책 재검토 (실패 시 무과금 or 별도 에러 카운터) |
| R5 | `MatchIngestRunner` | `execute()` / `executeWithPriority()` 중복 | 하나로 통합, priority는 파라미터 |

---

## 3. 실행 순서 (Phase)

### Phase 1 — 저위험·즉시 (1~2일)
동작 변경 없음, DB 마이그레이션 없음

- [ ] E. `BottomDuoExtractor` @Component
- [ ] R1. Ingest 상수 `@ConfigurationProperties`
- [ ] R2. truncate 길이 통일
- [ ] R3. `PatchVersionService` 게터 통합
- [ ] R5. `execute()` / `executeWithPriority()` 병합

### Phase 2 — 타입 안전성 (2~3일)
코드만 교체, DB 값 유지

- [ ] B. `MatchQueue.status` → `QueueStatus` enum
- [ ] B. `MatchQueue.collectionTier` → `Tier` enum
- [ ] B. `requireStatus()` 전이 가드 추가
- [ ] F. Graceful shutdown 구현

### Phase 3 — 정합성·타입 (3~5일)

- [ ] D. Match/Raw 정합성: **사전 필터** 구현 (D1)
- [ ] A. `Tier.ALL_TIERS` sentinel 제거 (호출부 전체 수정)
- [ ] R4. CollectMatchIds 예외-예산 정책 재설계

### Phase 4 — 마이그레이션 수반 (3~4일)
DB 변경 포함

- [ ] C. `seedCompletedTiers` → `@ElementCollection`
- [ ] C. Flyway/Liquibase 마이그레이션 스크립트
- [ ] C. 기존 JSON 문자열 → 별도 테이블로 데이터 이관

### Phase 5 — 경량 헥사고날 (선택, 2~3일)
- [ ] G. `RiotApiPort` 인터페이스 추출
- [ ] G. `RiotApiHttpAdapter` 이동
- [ ] G. 테스트용 `FakeRiotApiAdapter` 추가

---

## 4. 결정 사항 (Decisions)

### D1. `IngestMatchDetail` 패치 불일치 Match 처리 정책 → **(a) 사전 필터**

Match 데이터 받자마자 패치 확인, 불일치면 저장 자체를 skip.

**결정 이유**
- 고아 레코드 없음 → 데이터 모델 가장 깔끔
- `MatchQueue`가 `matchId` PK로 dedup 이미 처리 → `markDone()` 해두면 재수집 없음
- 이 프로젝트는 "현재 패치 기준" 통계 — 다른 패치 데이터 보관 가치 낮음
- 집계 쿼리에 `WHERE ingested=true` 같은 세금 없음

**구현 스케치**
```java
public void ingest(String matchId, Tier tier, String expectedPatch) {
    Match match = riotApiPort.fetchMatchDetail(matchId);

    // 사전 필터: 패치 불일치면 저장 자체를 skip
    if (expectedPatch != null && !expectedPatch.equals(match.patch())) {
        log.debug("Skip match: id={}, patch={}, expected={}",
            matchId, match.patch(), expectedPatch);
        return;  // MatchQueue는 상위에서 markDone
    }

    matchSaver.save(matchId, match);
    List<BottomDuoRaw> raws = extractor.extract(matchId, match, tier);
    rawRepository.saveAll(raws);
}
```

---

### D2. `seedCompletedTiers` 마이그레이션 방식 → **(a) `@ElementCollection`**

별도 테이블로 분리.

**결정 이유**
- SQL 직접 조회 가능
- 라이브러리 의존성 불필요 (jsonb는 Hibernate JsonType 필요)
- 조인 1회 비용은 이 용도에선 무시 가능

**구현 스케치**
```java
@ElementCollection(fetch = FetchType.EAGER)
@CollectionTable(
    name = "daily_pipeline_state_seed_completed_tiers",
    joinColumns = @JoinColumn(name = "state_id")
)
@Enumerated(EnumType.STRING)
@Column(name = "tier")
private Set<Tier> seedCompletedTiers = new HashSet<>();
```

**마이그레이션**
1. Flyway 스크립트로 `daily_pipeline_state_seed_completed_tiers` 테이블 생성
2. 기존 JSON 문자열 파싱 → 새 테이블로 row 이관
3. 기존 `seed_completed_tiers` 컬럼 drop

---

### D3. 이번 리팩토링 스코프 → **(b) 리팩토링 + 테스트 보강**

80% 커버리지 목표.

**결정 이유**
- Phase 2~4는 동작 변경이 포함됨 (enum 전환, 정합성 정책, DB 마이그레이션) → 회귀 방지 테스트 필수
- 리팩토링의 안전망 없이는 대규모 변경 위험
- `tdd-guide` 에이전트 적극 활용

**적용 원칙**
- 각 Phase 착수 전 해당 영역 기존 동작을 테스트로 고정
- 서비스/도메인 로직 중심 (trivial getter/config 제외)
- 통합 테스트는 Testcontainers로 실제 PostgreSQL 사용

---

## 5. 에이전트 팀 구성

| 에이전트 | 역할 | 투입 Phase |
|---|---|---|
| **architect** | A/B/C/D 설계 검증 | Phase 2, 3, 4 착수 전 |
| **java-reviewer** | 각 PR 리뷰 | 모든 Phase |
| **database-reviewer** | C 마이그레이션 스크립트 검토 | Phase 4 |
| **tdd-guide** | 테스트 선행 작성 | Phase 2~5 (Q3 답변에 따라) |
| **build-error-resolver** | 빌드 실패 시 즉시 투입 | 필요시 |

---

## 6. 리스크

| 리스크 | 완화 |
|---|---|
| Phase 3 A(Tier sentinel) 호출부 수정 누락 | 컴파일러가 강제, IDE 전체 검색 병행 |
| Phase 4 C 마이그레이션 중 데이터 유실 | 이관 스크립트 dry-run + 백업 선행 |
| Phase 2 Enum 전환 시 기존 DB 값 불일치 | `@Enumerated(STRING)` + 기존 값 그대로 사용, 값 리네이밍 금지 |
| 장기 Phase 동안 dev 브랜치 충돌 | Phase 단위로 작은 PR 반복 병합 |

---

## 7. 승인 필요 항목

1. 이 계획의 **Phase 1부터 즉시 진행**해도 되는지
2. Phase 5(경량 헥사고날) 포함 여부
