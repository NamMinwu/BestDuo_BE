# PR Review: #21 — feat: Phase 3 — ingest 단계 patch 검증 안전망

**Reviewed**: 2026-04-09
**Author**: NamMinwu
**Branch**: feat/patch-version-filtering-phase-3 → dev
**Decision**: APPROVE

## Summary

Phase 3 구현이 계획(`patch_version_filtering_plan.md`)과 일치하며 로직이 정확하다. 안전망 필터링(`expectedPatch != null` 조건), 하위 호환(`null` pass-through), warn 로그 모두 설계 의도에 맞게 구현됐다. 테스트도 3가지 케이스(일치/불일치/null)를 명확하게 커버하고 있다.

## Findings

### CRITICAL
None

### HIGH
None

### MEDIUM

**[M1] `MatchIngestWorker.execute(limit, tier, expectedPatch)`의 `expectedPatch` 파라미터가 묵시적으로 무시됨**

- 파일: `MatchIngestWorker.java:35-37`
- `IngestMatchDetailWorker`가 `workItem.getPatch()`를 넘기지만, 실제 필터링은 `item.patch()`(match_queue stamp) 기준으로 수행한다. 이 설계는 계획의 의도와 일치하지만, 외부에서 이 메서드를 보면 `expectedPatch`가 실제로 사용되는 것처럼 보여 혼란을 줄 수 있다.
- 제안: 파라미터명을 `ignoredExpectedPatch`로 변경하거나, 현재 주석을 Javadoc(`@param`)으로 격상해 의도를 더 명확히 드러내는 것을 고려할 것.

```java
// 현재
/** WorkItem의 patch를 수신하나, 실제 patch 필터링은 item.patch() (match_queue stamp) 기준으로 수행한다. */
public Result execute(int limit, Tier requestedTier, String expectedPatch) {
    return execute(limit, requestedTier);
}

// 제안 예시
/**
 * WorkItem 체인에서 호출되는 진입점.
 * @param expectedPatch WorkItem.patch — 이 레이어에서는 사용되지 않으며,
 *   실제 patch 필터링은 각 queue Item에 stamp된 item.patch() 기준으로 수행한다.
 */
public Result execute(int limit, Tier requestedTier, String expectedPatch) {
    return execute(limit, requestedTier);
}
```

### LOW

**[L1] `IngestMatchDetailPatchFilterTest` 테스트 데이터의 MetadataDto matchId 불일치**

- 파일: `IngestMatchDetailPatchFilterTest.java:107` (`sampleMatchWithGameVersion`)
- `MetadataDto` 생성 시 matchId가 `"KR_1"`로 고정되어, `"KR_2"`, `"KR_3"` 케이스와 불일치한다. 기능 동작에는 영향이 없으나 테스트 데이터의 일관성이 떨어진다.
- 제안: `sampleMatchWithGameVersion(String gameVersion, String matchId)` 형태로 오버로드하거나 파라미터로 matchId를 받도록 수정.

**[L2] `saveMatch`가 patch 필터링 전에 호출됨 — 의도 확인 권장**

- 파일: `IngestMatchDetail.java:34`
- `saveMatch(matchId, match)`는 raw 필터링과 무관하게 항상 원본 match를 저장한다. 이는 설계상 의도된 것(match 자체는 저장, raw만 걸러냄)으로 보이나, 코드에 명시적인 주석이 없어 향후 혼란 가능성이 있다.
- 제안: 관련 주석 1줄 추가 (`// match 자체는 항상 저장; patch 필터링은 raw에만 적용`).

## Validation Results

| Check | Result |
|---|---|
| Tests | Pass — BUILD SUCCESSFUL |
| Build | Pass — BUILD SUCCESSFUL |
| Lint | Skipped (no Checkstyle configured) |
| Type check | N/A (Java, 컴파일 통과) |

## Files Reviewed

| 파일 | 변경 |
|------|------|
| `IngestMatchDetail.java` | Modified — expectedPatch 필터링 추가 |
| `MatchIngestWorker.java` | Modified — 3-param overload + item.patch() 전달 |
| `IngestMatchDetailWorker.java` | Modified — workItem.getPatch() 전달 |
| `IngestController.java` | Modified — null 하위 호환 |
| `IngestMatchDetailPatchFilterTest.java` | Added — 신규 테스트 3케이스 |
| `IngestMatchDetailTest.java` | Modified — null 전달로 기존 테스트 호환 |
| `MatchIngestWorkerTest.java` | Modified — patch 전달 검증 추가 |
