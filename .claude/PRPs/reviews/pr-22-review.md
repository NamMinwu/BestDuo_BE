# PR Review: #22 — feat: Phase 4-5 — AdminPatchController + DataDragonPatchSyncScheduler

**Reviewed**: 2026-04-09
**Author**: NamMinwu
**Branch**: `feat/patch-version-filtering-phase-4-5` → `dev`
**Decision**: APPROVE

## Summary

Phase 4(Admin API)와 Phase 5(DataDragon 자동 감지)를 깔끔하게 구현했습니다. 기존 프로젝트 패턴(AdminCoverageController, CoverageScheduler)을 잘 따르고 있으며, TDD 사이클(RED→GREEN)이 커밋 히스토리로 검증됩니다. CRITICAL/HIGH 이슈 없음. 두 가지 MEDIUM 개선 사항을 남깁니다.

## Findings

### CRITICAL
None

### HIGH
None

### MEDIUM

**[M1] `OffsetDateTime.now()` — 시스템 타임존 의존**
- 파일: `DataDragonPatchSyncScheduler.java:61`
- 현상: `OffsetDateTime.now()`는 JVM 기본 타임존을 사용한다. 컨테이너 배포 환경에서 타임존이 다를 경우 `releasedAt` 값이 일관성 없이 저장된다.
- 수정:
  ```java
  // Before
  OffsetDateTime.now()
  // After
  OffsetDateTime.now(ZoneOffset.UTC)
  ```

**[M2] `patch` 파라미터 포맷 검증 없음**
- 파일: `AdminPatchController.java:29`
- 현상: `"foo bar"`, `""` 같은 임의 문자열도 DB에 저장된다. `BottomDuoExtractor.toPatch()`의 정규화 결과가 항상 `\d+\.\d+` 형식이므로 입력도 같은 형식이어야 한다.
- 수정 예시:
  ```java
  if (!patch.matches("\\d+\\.\\d+")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "Invalid patch format (expected major.minor, e.g. 15.23): " + patch);
  }
  ```

### LOW

**[L1] `PatchVersionService.currentPatch()` — 엔티티 노출**
- 파일: `PatchVersionService.java`
- `Optional<PatchVersion>` (JPA 엔티티)를 서비스 레이어 밖으로 반환한다. 프레젠테이션 레이어에서 엔티티를 직접 사용하는 레이어 혼합 패턴이다. 기존 코드베이스 규모에서는 실용적으로 허용 가능하나, 향후 `PatchVersionInfo` DTO를 두는 것을 고려할 수 있다.

**[L2] `@Autowired` 필요성 주석 부재**
- 파일: `DataDragonPatchSyncScheduler.java:30`
- `@Autowired`가 생성자 두 개 중 어느 것을 쓸지 Spring에게 알려주기 위해 필요한 이유가 주석에 없다. 다음 기여자가 "왜 붙어있지?" 할 수 있다.
  ```java
  // Spring이 사용하는 생성자 — 생성자가 2개이므로 @Autowired로 명시적 지정
  @Autowired
  public DataDragonPatchSyncScheduler(PatchVersionService patchVersionService) {
  ```

## Validation Results

| Check | Result |
|---|---|
| 컴파일 | Pass |
| Tests (191 total) | Pass — 0 failures |
| Build | Pass |

## Files Reviewed

| 파일 | 변경 |
|------|------|
| `common/application/PatchVersionService.java` | Modified — `currentPatch()` 추가 |
| `common/infra/champion/DataDragonPatchSyncScheduler.java` | Added |
| `common/presentation/api/AdminPatchController.java` | Added |
| `common/presentation/api/dto/PatchVersionResponse.java` | Added |
| `src/main/resources/application.yml` | Modified — patch-sync 설정 추가 |
| `test/.../AdminPatchControllerTest.java` | Added — 4 tests |
| `test/.../DataDragonPatchSyncSchedulerTest.java` | Added — 13 tests |
