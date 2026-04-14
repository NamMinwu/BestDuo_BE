# PR Review: #20 — FEAT : PatchVersion 기반 현재 패치 match 수집 필터링

**Reviewed**: 2026-04-09
**Author**: NamMinwu
**Branch**: feat/patch-version-filtering-phase-2 → dev
**Decision**: APPROVE (with comments)

## Summary

Phase 1/2 구현 전체가 프로젝트 패턴(CoverageBucket 엔티티 스타일, @Builder/@Getter/@NoArgsConstructor(PROTECTED))을 잘 따르고 있으며, 폴백 로직·멱등성·patch stamp 전파까지 설계 의도를 올바르게 구현했습니다. CRITICAL/HIGH 이슈는 없고, MEDIUM 2건(race condition, 중복 DB 쿼리)과 LOW 2건을 발견했습니다.

## Findings

### CRITICAL
없음

### HIGH
없음

### MEDIUM

**M1 — `PatchVersionService.registerIfAbsent` TOCTOU race condition**
- 파일: `PatchVersionService.java:33-39`
- `existsByPatch` 체크 → `save` 사이에 동시 요청이 동일 patch를 먼저 저장하면 unique constraint violation(DataIntegrityViolationException)이 unchecked로 전파됩니다. `CoverageBucketService.create`가 DataIntegrityViolationException을 잡아 도메인 예외로 변환하는 패턴과 동일하게 처리해야 합니다.
- 현재 직접 호출 경로가 Phase 5(DataDragonPatchSyncScheduler, 단일 스케줄러)라면 실질적 위험은 낮지만, 추후 AdminPatchController가 병렬 요청을 받을 수 있으므로 지금 수정이 낫습니다.

```java
public boolean registerIfAbsent(String patch, OffsetDateTime releasedAt) {
  if (patchVersionRepository.existsByPatch(patch)) {
    return false;
  }
  try {
    patchVersionRepository.save(PatchVersion.of(patch, releasedAt));
    return true;
  } catch (DataIntegrityViolationException e) {
    return false; // concurrent insert — already registered
  }
}
```

**M2 — Seed/Refresh 실행마다 patch 조회 DB 쿼리 2회 발생**
- 파일: `SeedBootstrapExecutor.java:126-127`, `RefreshSummonerMatches.java:63,103`
- `currentPatchStartTimeEpochSeconds()`와 `currentPatchVersion()` 각각이 `findTopByOrderByReleasedAtDesc()`를 별도로 호출합니다. puuid 수천 건을 처리하는 Seed/Refresh 루프에서 루프마다 DB hit가 2배가 됩니다.
- 해결: `PatchVersionService`에 `Optional<PatchVersion> current()` 메서드를 추가해 엔티티를 한 번만 조회하고 caller에서 두 값을 모두 꺼내도록 하거나, 두 값을 묶은 record를 반환하는 메서드를 추가합니다.

```java
// PatchVersionService에 추가
public record PatchContext(String version, long startTimeEpochSeconds) {}

public Optional<PatchContext> currentPatchContext() {
  return patchVersionRepository.findTopByOrderByReleasedAtDesc()
      .map(p -> new PatchContext(p.getPatch(), p.releasedAtEpochSeconds()));
}
```

### LOW

**L1 — `SeedBootstrapExecutorTest.limitNumberOfEntriesWhenMaxEntriesConfigured` — patch stub 누락**
- 파일: `SeedBootstrapExecutorTest.java` (limitNumber 테스트)
- `currentPatchVersion()` stub이 없어 Mockito가 `Optional.empty()`를 반환하고 `currentPatch = null`로 enqueue됩니다. 테스트는 통과하지만 다른 patch 관련 테스트와 일관성이 없습니다. `patchVersionService.currentPatchVersion().willReturn(Optional.of("15.23"))` 한 줄 추가를 권장합니다.

**L2 — `PatchVersionJpaRepository.findByPatch` 미사용 선언**
- 파일: `PatchVersionJpaRepository.java:10`
- Phase 1/2에서 사용되지 않고 Phase 4/5를 위해 선언되어 있습니다. 의도적 선언이라면 주석으로 명시하거나 실제 사용 시점에 추가하는 편이 깔끔합니다(YAGNI). Phase 5 PR 전까지는 삭제해도 무방합니다.

## Validation Results

| Check | Result |
|---|---|
| Compile | Pass |
| Tests (./gradlew cleanTest test) | Pass — BUILD SUCCESSFUL |

## Files Reviewed

| 파일 | 변경 |
|---|---|
| `common/application/PatchVersionService.java` | Added |
| `common/application/port/MatchQueueEnqueuer.java` | Modified |
| `common/infra/persistence/MatchQueueEnqueuerImpl.java` | Modified |
| `common/infra/persistence/entity/MatchQueue.java` | Modified |
| `common/infra/persistence/entity/PatchVersion.java` | Added |
| `common/infra/persistence/repository/IngestQueueStatsJpaRepository.java` | Modified |
| `common/infra/persistence/repository/MatchQueueJpaRepository.java` | Modified |
| `common/infra/persistence/repository/PatchVersionJpaRepository.java` | Added |
| `ingest/application/port/MatchQueueDispatcher.java` | Modified |
| `ingest/infra/persistence/MatchQueueDispatcherImpl.java` | Modified |
| `refresh/application/RefreshSummonerMatches.java` | Modified |
| `seed/application/SeedBootstrapExecutor.java` | Modified |
| `workitem/infra/persistence/repository/WorkItemJpaRepository.java` | Modified |
| `test/.../PatchVersionServiceTest.java` | Added |
| `test/.../MatchQueueEnqueuerImplTest.java` | Modified |
| `test/.../MatchQueueTest.java` | Added |
| `test/.../MatchIngestWorkerTest.java` | Modified |
| `test/.../MatchQueueDispatcherImplTest.java` | Modified |
| `test/.../MatchQueuePickerImplTest.java` | Modified |
| `test/.../IngestControllerTest.java` | Modified |
| `test/.../RefreshSummonerMatchesTest.java` | Modified |
| `test/.../SeedBootstrapExecutorTest.java` | Modified |
| `test/resources/application.yml` | Added |
