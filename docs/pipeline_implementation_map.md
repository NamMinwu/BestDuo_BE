# 데이터 파이프라인 구현 맵 (Implementation Map)

> **목적**: 기획·ADR이 "무엇을 하려 했는가"를 적는다면, 이 문서는 **코드가 실제로 무엇을 하는가**를 file:line 근거로 기록한다. 기획 대비 정합성 평가의 기준선(ground truth)으로 쓴다.
>
> **작성 기준일**: 2026-06-05 (`feat/db-size-metric` 브랜치)
> **검증 방식**: `src/` 직접 탐색 + `application.yml` 설정값 대조. file:line 은 작성 시점 기준.

---

## 0. 한눈에 보는 파이프라인

```
① Seed     DailyLeagueEntriesRunner   리그 엔트리 → summoner 시드
② Collect  CollectMatchIdsRunner      summoner → 최근 matchId → match_queue(READY)
③ Ingest   MatchIngestRunner          match_queue → match-v5 payload 저장(raw-first)
④ Aggregate BottomDuoAggregateScheduler(cron) → AggregateBottomDuoFromMatch
            payload_json 재읽기 → stat_agg / matchup_agg upsert
④b Ranking  ComputeBottomDuoRanking    PENDING → RANKED 승격(rank_score)
⑤ Cleanup  CleanupOldPatches          최신 N patch 만 stat/matchup 보존
⑥ Archive  MatchArchiver / MatchPayloadCleaner  payload → R2(JSONL.GZ) 후 DB 행 삭제 (수동/admin)
```

핵심 전제: **단일 Riot API key → 단일 스레드**. 수평 확장 없음. Stage 3 는 종료 없는 상시 루프.

---

## 1. 활성화 상태 스냅샷 (가장 먼저 볼 것)

대부분의 자동 단계는 **default `false`** 이며 환경변수로만 켜진다. 코드만 보면 "수동", prod env 가 켜야 "자동"이 된다.

| 설정 | 기본값 | 위치 |
|---|---|---|
| `PIPELINE_RUNNER_ENABLED` (Seed/Collect/Ingest 루프) | **false** | `application.yml:76` |
| `AGGREGATE_SCHEDULER_ENABLED` (집계 cron) | **false** | `application.yml:61` |
| `PATCH_SYNC_ENABLED` (DataDragon patch 동기화) | **false** | `application.yml:56` |
| `ARCHIVE_R2_ENABLED` (R2 아카이브) | **false** | `application.yml:68` |
| `aggregate.scheduler.cron` | `0 0 4 * * *` (Asia/Seoul) | `application.yml:62` |
| `AGGREGATE_RETENTION_PATCHES` | `3` | `application.yml:64` |
| `PIPELINE_COLLECT_DAILY_BUDGET` | `12000` | `application.yml:77` |
| `PIPELINE_COLLECT_BATCH_SIZE` | `20` | `application.yml:78` |
| `PIPELINE_INGEST_BATCH_SIZE` | `10` | `application.yml:79` |
| `PIPELINE_DIA_EME_DAILY_PAGE_QUOTA` | `1000` | `application.yml:82` |
| Rate limit (short) | `18 / 1s` (20의 90%) | `application.yml:50` |
| Rate limit (long) | `90 / 2min` (100의 90%) | `application.yml:52` |

> ⚠️ **서사 정합성 주의**: 포트폴리오는 "자동 daily pipeline" 으로 서술하지만 코드 기본값은 전부 off. prod(Railway) env 가 실제로 켜는지 별도 확인 필요.

---

## 2. Stage 1 — Seed (`DailyLeagueEntriesRunner`)

- **트리거**: 파이프라인 루프(`PIPELINE_RUNNER_ENABLED`)에서 호출. virtual thread 루프.
- **동작**:
  - Apex tier(CHALLENGER/GRANDMASTER/MASTER): tier 당 API 1회로 전체 엔트리 수신.
  - Non-apex(DIAMOND/EMERALD): `league-v4 entries/{queue}/{tier}/{division}?page={n}` 페이지네이션.
    - **일일 할당량(quota)**: 하루 `dia-eme-daily-page-quota`(기본 1000) 페이지 처리 후 정지.
    - division 별 safety cap 존재. 응답이 비면(`entriesFetched == 0`) 해당 division 소진 → 다음 division.
    - `seedPage` / `seedDivision` 은 날짜가 바뀌어도 리셋 안 함 — 사이클 위치 영속.
- **상태**:
  - `DailyPipelineState`(날짜별) + `CoverageBucket`(patch×tier별). 자정 리셋.
  - **`CoverageBucket.create(patch, tier)` 는 프로덕션에서 호출됨** → `DailyLeagueEntriesRunner.java:133`.
    (※ `plan_dia_eme_cycle_seed.md` 의 "한 곳도 호출 안 함" 서술은 stale.)

---

## 3. Stage 2 — Collect (`CollectMatchIdsRunner`)

- **동작**: `seeded_at` 은 있고 `match_ids_collected_at` 은 없는 summoner 를 골라 최근 matchId 수집 → `match_queue`(status=READY)에 tier+patch 메타와 함께 적재.
- **tier별 matchesPerSummoner 차등 구현됨**: `matchCountFor(summoner.getLastKnownTier())` → `CollectMatchIdsRunner.java:105`.
- **일일 예산**: `collect-daily-budget`(기본 **12000**) API 호출. 소진 시 Stage 3 로 넘어감.
  (※ `daily_pipeline_redesign_plan.md` 의 Stage2 "8000 req/day" 는 문서가 stale — 코드는 12000.)
- **patch 필터**: enqueue 시 `startTime`(현재 patch 시작 시점) 필터로 이전 patch 매치 유입 차단(입구 게이트).

---

## 4. Stage 3 — Ingest (`MatchIngestRunner`)

- **동작**: `match_queue` 에서 READY 또는 재시도 가능한 ERROR 행을 batch(`ingest-batch-size`=10)로 픽 → match-v5 상세 수집 → `RiotMatchDto` 를 `match.payload_json` 에 **그대로 저장(raw-first)**.
- **patch 검증(출구 게이트)**: `expectedPatch` 와 불일치하면 폐기 — cross-patch 노이즈 차단.
- **재시도/복구**:
  - ERROR 행은 `retryCount < 2` 이고 `errorCooldownMinutes` 경과 시 재처리.
  - stale 복구: RUNNING 상태가 10분 초과면 READY 로 되돌림.
- **rate limit**: `DualWindowRateLimiter` + `RiotRateLimitInterceptor` — short/long 윈도우(18/1s, 90/2min). 429 시 즉시 `sleep → continue`.

---

## 5. Stage 4 — Aggregate (`AggregateBottomDuoFromMatch`)

진입점: `BottomDuoAggregateScheduler.run()` (cron) — `@ConditionalOnProperty(aggregate.scheduler.enabled=true)`, `BottomDuoAggregateScheduler.java:25,41`.

- **5 tier 순회**: 한 cron 실행에서 CHALLENGER~EMERALD 5개를 `for` 루프 → `BottomDuoAggregateScheduler.java:52`. tier 별로 `fromMatchUseCase.execute(patch, tier, true)` 호출 후 ranking 까지 수행. 한 tier 실패해도 나머지 진행.
- **읽기 방식 (`AggregateBottomDuoFromMatch.java`)**:
  - keyset 페이지네이션: `cursor` = 마지막 `matchId`, `PAGE_SIZE=500`.
  - `matchRepository.findPageByTierAndPatch(...)` → **`List<Match>` 엔티티** 반환 → `AggregateBottomDuoFromMatch.java:131`.
  - 페이지마다 `objectMapper.readValue(m.getPayloadJson(), RiotMatchDto.class)` → `BottomDuoExtractor.extract()` 로 듀오 추출 → in-memory `HashMap<StatKey,Counter>` / `HashMap<MatchupKey,Counter>` 누적.
  - `execute()` 에 **`@Transactional` 없음** (클래스/메서드 모두).
- **upsert**: `JdbcTemplate.batchUpdate`(UPSERT_BATCH_SIZE=500)로 `bottom_duo_stat_agg`, `bottom_duo_matchup_agg` 에 `on conflict do update`.
  - `adjusted_win_rate = (wins + 50) / (games + 100)` — Bayesian smoothing. `ranking_status='PENDING'` 으로 insert.

> 🔴 **설계 일관성 이슈 (ADR-007 대비)**: archive 경로는 `MatchPayloadProjection`(L1 캐시 우회)으로 하드닝됐지만, aggregate 읽기 경로는 같은 `payload_json` 을 더 많은 행(tier 전체)에 대해 **full 엔티티로 로딩**한다. 같은 repository 에 projection 메서드가 나란히 존재(아래 §8). 현재는 cron 전용 호출 + 스케줄 스레드(OSIV 미적용) + `@Transactional` 부재 덕에 페이지마다 detach→GC 되어 **우연히 안전**하지만, `@Transactional` 추가나 HTTP 트리거 도입 시 ADR-007 이 문서화한 OOM 이 재발할 수 있는 latent 구조다. 자세한 내용은 ADR-007 §7 참조.

### 5b. Ranking (`ComputeBottomDuoRanking`)
- `@Transactional` (`ComputeBottomDuoRanking.java:25`). tier×patch 의 `stat_agg` 를 `rank_score` 로 정렬해 `ranking_status='PENDING' → 'RANKED'` 승격.
- 별도 admin 경로 존재: `POST /admin/aggregate/recompute-ranking` (`AggregateAdminController.java:33`) — **ranking 만** 재계산(과거 스케줄러가 ranking 호출을 빠뜨린 회귀 대응용). from-match 집계 자체를 HTTP 로 트리거하는 엔드포인트는 **없음**.

---

## 6. Stage 5 — Cleanup (`CleanupOldPatches`)

- cron 끝에서 실행. 최신 `AGGREGATE_RETENTION_PATCHES`(기본 3)개 patch 의 stat/matchup 행만 보존, 그 외 삭제.

---

## 7. Stage 6 — Archive / Cleanup (수동 · admin)

- **트리거**: admin 엔드포인트(`/admin/archive/*`). cron 상시화는 미구현(기획 slide 14 "자동 아카이브 상시화" = future work).
- **`MatchArchiver`**: `match.payload_json` 을 `MatchPayloadProjection`(interface projection) + temp file 스트리밍으로 gzip JSONL 화 → R2(S3 호환) 업로드. L1 캐시 우회로 대용량 OOM 회피(ADR-007).
- **`MatchPayloadCleaner`**: R2 `HEAD` 검증 후 DB 행 삭제. **최신 2 patch 보호**(`protected_latest=2`).

> 🟡 **재집계 범위 주의**: ADR-006 의 "payload 만 있으면 과거 재집계 가능" 가치는, archive cleanup 이 payload 를 DB 에서 지우고 **R2 → 재집계 reload 경로가 없으므로**, 실제로는 *DB 에 payload 가 남은 최근 patch 한정*. retention(stat 3 patch)상 실무 영향은 작음.

---

## 8. 핵심 Repository — `MatchJpaRepository`

같은 (tier, patch) keyset 쿼리가 **엔티티용**과 **projection용** 두 벌 존재.

| 메서드 | 반환 | 용도 | 위치 |
|---|---|---|---|
| `findPageByTierAndPatch` | `List<Match>` (엔티티) | **aggregate** 경로 | `MatchJpaRepository.java:29` |
| `findPayloadPageByTierAndPatch` | `List<MatchPayloadProjection>` (L1 우회) | **archive** 경로 | `MatchJpaRepository.java:50` |
| `findDistinctPatches` | `List<String>` | archive cleanup 의 protected 목록 계산 | `MatchJpaRepository.java:61` |
| `deleteByTierAndPatch` | `int` (`@Modifying @Transactional`) | archive 후 디스크 회수 | `MatchJpaRepository.java:71` |

- projection 메서드 주석(`:37-39`)이 직접 명시: *"L1 캐시에 엔티티가 등록되지 않도록 … OSIV 가 세션을 잡고 있어도 페이지가 GC 대상이 되어 OOM 을 피한다."* → archive 엔 적용, aggregate 엔 미적용(§5 이슈).
- **OSIV**: `open-in-view` 미설정 → Spring Boot 기본값 `true`. (단, @Scheduled 스레드엔 미적용.)

---

## 9. half-wired / 주의 항목 요약

| 항목 | 상태 |
|---|---|
| 자동 단계 플래그(pipeline/aggregate/patch/archive) | 전부 default **off** — prod env 의존 |
| 자동 아카이브 cron | 미구현(수동 admin only) — 기획상 future work |
| aggregate 읽기 경로 projection 미적용 | latent OOM 구조(현재는 우연히 안전) — §5 / ADR-007 §7 |
| from-match 강제 재집계 admin 엔드포인트 | 없음(YAGNI, ADR-006 §8) |
| 문서 stale | collect budget 8000(문서) vs 12000(코드); `CoverageBucket.create` "호출 안 함"(문서) vs 호출됨(코드) |
| TODO/FIXME | 파이프라인 코드 내 없음. magic number 는 대부분 config 외부화됨 |

---

### 관련 문서
- 기획/의도: `daily_pipeline_redesign_plan.md`, `architecture.md`, `portfolio_slides.md`
- ADR: `adr_aggregate_from_match_payload.md`(ADR-006), `adr_archive_oom_projection.md`(ADR-007)
- 인시던트: `incident_postgres_disk_full_recovery_mode.md`
