# PR Review: #23 — refactor: Phase 1 — workitem/refresh/10명 증폭 완전 제거

**리뷰 일자**: 2026-04-13
**작성자**: NamMinwu
**브랜치**: refactor/daily-pipeline-phase-1 → dev
**결정**: APPROVE

## 요약

~2629줄의 WorkItem/refresh/expansion 인프라를 제거하는 명확한 범위의 삭제 PR. 빌드 성공, 전체 147개 테스트 통과. 남아있는 Medium 이슈는 PR 본문에 명시된 "DB/API 호환성 유지"를 위한 의도적 트레이드오프이며 실수가 아님. Phase 2에서 고아 파라미터 체인을 정리해야 함.

## 발견 사항

### CRITICAL
없음

### HIGH
없음

### MEDIUM

**M1 — refresh 파라미터가 4개 레이어를 통과하지만 어디에도 deprecated 표시 없음**

`refreshRatio`, `refreshLimit`, `runRefresh`가 전체 호출 체인을 따라 전달됨:
`AdminRunController` → `ExecutionCreateRequest` → `ExecutionRequestService` → `ExecutionRequestWorker` → `ExecutionOrchestrator`

...그런데 `ExecutionOrchestrator.allocateBudgets()`에서 조용히 무시됨. `ExecutionOrchestrator.run()`에는 Javadoc 주석이 있지만, 체인의 나머지 4개 파일에는 `// TODO: Phase N에서 제거` 또는 `@Deprecated` 마커가 없음. 나중에 refresh가 "동작하지 않는" 버그를 디버깅하는 개발자가 파일 4개를 거쳐서야 비어있음을 알게 됨.

관련 파일:
- `DailyRunProperties.java:18-21` — `runRefresh`, `refreshRatio`, `refreshLimit` 여전히 활성 필드
- `ExecutionRequestService.java:51-67` — refresh 파라미터를 resolve/저장하지만 사용되지 않음
- `ExecutionRequestWorker.java:59` — `refreshRatio={}`, `refreshLimit={}`를 의미 있는 값처럼 로깅

**Phase 2 제안**: 각 지점에 `// TODO Phase 2: DB 마이그레이션 후 제거` 추가하거나 전체 체인을 한 번에 정리.

**M2 — `ExecutionRequestWorker` 로그에서 `refreshEnqueued`가 항상 0 출력**

`src/main/java/com/bestduo_BE/orchestration/application/ExecutionRequestWorker.java:89`

```java
"... refreshEnqueued={} ..."
result.refreshEnqueued(),   // ExecutionPipeline.Result에서 제거됐으므로 항상 0
```

`ExecutionLog.ExecutionResult.refreshEnqueued()`는 이제 항상 `0`임(`buildSuccessResult`에서 하드코딩). 로그 필드가 노이즈를 생성하고 운영 디버깅 시 혼란을 줄 수 있음.

### LOW

**L1 — `DailyRunProperties.runRefresh`가 어디서도 읽히지 않음**

`DailyRunProperties.java:18` — `runRefresh = true`가 현재 코드 어디에도 참조되지 않음. `ExecutionRequestService`는 `refreshRatio`/`refreshLimit`만 사용하고 `runRefresh`는 무시. 완전히 죽은 필드.

**L2 — `BudgetAllocation` 레코드 정상 업데이트**

`private record BudgetAllocation(int seedBudget, int ingestBudget)` — `refreshBudget`이 올바르게 제거됨.

**L3 — `IngestMatchDetail` 정리**

private 래퍼 메서드(`loadMatch`, `saveMatch`, `extractBottomDuoRaws`, `saveBottomDuoRaws`)가 제거되고 직접 인라인화됨. 한 줄짜리 래퍼를 없앤 것은 적절한 단순화.

## 검증 결과

| 항목 | 결과 |
|---|---|
| 컴파일 (`./gradlew compileJava`) | 통과 |
| 테스트 (`./gradlew test`) | 통과 (147/147) |
| 빌드 | 통과 |

## 리뷰된 파일

| 파일 | 변경 내용 |
|---|---|
| `workitem/**` (13개 파일) | 삭제 |
| `refresh/**` (6개 파일) | 삭제 |
| `coverage/application/CoverageScheduler.java` | 삭제 |
| `coverage/application/CoverageSchedulingService.java` | 삭제 |
| `common/application/port/SummonerExpansionQueue.java` | 삭제 |
| `common/infra/persistence/SummonerExpansionQueueImpl.java` | 삭제 |
| `common/infra/riot/LeagueEntriesRefreshLoaderImpl.java` | 삭제 |
| `config/WorkItemProperties.java` | 삭제 |
| `ingest/application/IngestMatchDetail.java` | 수정 — expansion 제거, private 래퍼 인라인화 |
| `orchestration/application/ExecutionOrchestrator.java` | 수정 — refresh 제거, 호환성용 파라미터 유지 |
| `orchestration/application/ExecutionPipeline.java` | 수정 — REFRESH 페이즈 enum/상태/프로파일링 제거 |
| `resources/application.yml` | 수정 — `work-item:` 블록 제거 |
| 테스트 파일 (17개 삭제, 3개 수정) | 삭제/수정 |
