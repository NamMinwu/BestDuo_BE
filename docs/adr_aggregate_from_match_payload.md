# ADR-006: cron 집계 경로를 `match.payload_json` 기반 in-memory 누적으로 통일하고 `bottom_duo_raw` 제거

> 작성일: 2026-05-16
> 상태: 채택(Accepted)
> 관련 이슈: [#75](https://github.com/NamMinwu/BestDuo_BE/issues/75)
> 관련 PR: [#76](https://github.com/NamMinwu/BestDuo_BE/pull/76), [#77](https://github.com/NamMinwu/BestDuo_BE/pull/77), [#78](https://github.com/NamMinwu/BestDuo_BE/pull/78), [#79](https://github.com/NamMinwu/BestDuo_BE/pull/79)
> 대상 모듈: `com.bestduo_BE.aggregate`, `com.bestduo_BE.ingest`
> 영향 파일:
> - 신규: `AggregateBottomDuoFromMatch`, `BottomDuoAggregateScheduler`, `V5__drop_bottom_duo_raw.sql`
> - 제거: `bottom_duo_raw` 테이블, `BottomDuoRawEntity/Repo/Saver`, `AggregateBottomDuoStats/Matchup` 및 raw self-join SQL, 두 admin 컨트롤러
> - 문서: `docs/aggregate-from-match-verification.md`

---

## 1. 결정 요약

| 항목 | 변경 전 | 변경 후 |
|---|---|---|
| 집계 데이터 소스 | `bottom_duo_raw` (정규화 테이블) | `match.payload_json` (raw blob) |
| 집계 방식 | SQL `GROUP BY` / self-join native query | 애플리케이션 in-memory `HashMap` 누적 |
| 쓰기 경로 | `INSERT … ON CONFLICT` (단일 SQL) | `JdbcTemplate.batchUpdate` bulk upsert |
| 수동 트리거 | `/admin/aggregate/bottom-duo-stat`, `/bottom-duo-matchup` | **제거** — cron 단일 진입점 |
| Cron tier 처리 | (raw 위 SQL 한 방) | tier 단위 직렬 처리, tier 실패 시 격리 |
| Raw 보관 | retention 정책으로 top-N patch 유지 | **테이블 제거** (V5 migration) |
| Match 메타 | tier 정보 없음 | `match.collection_tier` NOT NULL (V4) |

핵심 시그니처:

```java
record Result(int matchesProcessed, int statKeys, int matchupKeys,
              int statUpserted, int matchupUpserted,
              long statTotalGames, long matchupTotalGames) {}

Result execute(String patch, Tier tier, boolean upsert);
```

---

## 2. 문제 정의

### 2-1. 기존 구조의 결합

`bottom_duo_raw` 는 매치당 양 팀의 (adc, sup, win, patch, tier) 를 풀어둔 정규화 테이블이었다. cron 은 이 테이블 위에서 두 종류의 native SQL 을 돌렸다:

- **stat**: `bottom_duo_raw` GROUP BY `(patch, tier, adc, sup)` → `bottom_duo_stat_agg` upsert
- **matchup**: `bottom_duo_raw` self-join (같은 match_id, 다른 team_id) → `bottom_duo_matchup_agg` upsert

이 구조에 다음 결합이 누적되어 있었다.

| 결합 | 영향 |
|---|---|
| ① 추출 로직이 ingest 시점 SQL 한 곳에만 존재 | 새 lane 조합 (mid-jng, jng-bot 등) 추가 시 raw 스키마 + ingest + 두 native SQL 을 동시에 손봐야 함 |
| ② tier 정보가 raw 에만 있음 | match 단독으로는 어느 tier 버킷에서 수집됐는지 복구 불가 — 장기 보관 매치를 재집계할 수 없음 |
| ③ retention 으로 raw 가 잘리는 순간 과거 데이터 재계산 불가 | 알고리즘 수정/리플레이가 사실상 불가능 |
| ④ stat/matchup 두 native query 가 사실상 같은 데이터를 두 번 GROUP BY | 새 집계 종류가 늘어날수록 비용 선형 증가 |

### 2-2. 트리거

[#75](https://github.com/NamMinwu/BestDuo_BE/issues/75) 에서 bottom 외 lane 조합 확장을 검토하면서 ①, ② 가 동시에 막혔다. raw 스키마는 bottom 듀오 가정에 박혀 있어, lane 확장하려면 raw 자체를 갈아엎거나 raw 를 우회하는 길을 만들어야 했다.

---

## 3. 검토한 대안

### 옵션 A — raw 유지 + lane 컬럼 확장

`bottom_duo_raw` 에 `lane_kind` 같은 컬럼을 추가하고 native SQL 도 lane 별로 분기.

| 장점 | 단점 |
|---|---|
| 점진적 마이그레이션 | ②, ③ 결합은 그대로 — match 단독 재집계는 여전히 불가 |
| SQL 한 방의 단순함 유지 | ④ 가 lane 수만큼 곱셈으로 늘어남 |

→ **기각**. 결합을 비싸게 하는 방향.

### 옵션 B — Spring Batch 도입

Reader (match) → Processor (extract) → Writer (upsert) 청크 처리.

| 장점 | 단점 |
|---|---|
| 청크/재시작/스킵/리스너 등 기성 인프라 | 이번 작업 1회 처리량(수십만 row) 대비 과한 인프라 부담 |
| 표준화된 운영 | "포폴에서 over-engineering 금지" 원칙과 정면 충돌 |
| | 청크/Job/Step/리스너 추상화가 디버깅·관찰 가능성을 도리어 떨어뜨림 |

→ **기각**. 메모리 안에 충분히 들어가는 데이터에 대해 청크 프레임워크는 ROI 가 낮다. 향후 데이터 규모가 한계 도달하면 그때 도입.

### 옵션 C — `match.payload_json` 기반 in-memory 누적 (채택)

`match.payload_json` 에서 추출 (`BottomDuoExtractor`) 후 `HashMap<StatKey, Counter>` / `HashMap<MatchupKey, Counter>` 두 개에 누적, 끝나면 `JdbcTemplate.batchUpdate` 로 한꺼번에 upsert.

| 장점 | 단점 |
|---|---|
| 추출 로직이 도메인 서비스 한 곳 (`BottomDuoExtractor`) — lane 확장이 ingest/aggregate 양쪽에 동일 적용 | match.collection_tier 가 NOT NULL 로 채워져야 함 — V4 migration 필요 |
| match.payload_json 만 있으면 과거 매치도 재집계 가능 — ② 해결 | 메모리 사용 증가 (tier 당 1패치 누적 분 — 측정 결과 수십 MB 수준) |
| stat/matchup 을 단일 루프에서 동시 누적 — ④ 해결 | raw 테이블 drop 까지 cutover 검증 필요 |
| Spring Batch 같은 외부 의존성 없음 | |

→ **채택**.

---

## 4. 채택 근거 — 왜 옵션 C 인가

### 4-1. lane 확장 비용을 도메인 서비스 한 곳에 모은다

`BottomDuoExtractor.extract(matchId, match, tier)` 의 반환 타입을 확장하거나 동급의 `MidJngExtractor` 를 추가하면, ingest 와 aggregate 양쪽이 같은 도메인 서비스를 공유한다. 이전 구조에서는 raw 스키마/SQL 두 군데를 동시에 수정해야 했다.

### 4-2. raw 가 사라져도 재집계 가능

`match.payload_json` 은 ingest 시점에 그대로 저장된 원본이고, V4 로 `match.collection_tier` 가 NOT NULL 로 자리잡았다. 두 조건을 만족하면 cold archive 로 옮긴 patch 도 in-place 재집계가 가능하다. raw 테이블의 retention 자르기에 더 이상 묶이지 않는다.

### 4-3. 메모리 비용은 충분히 예측 가능

tier 당 한 patch 의 stat/matchup 누적 크기:
- stat key 수: `O(C^2)` (C = 챔피언 수, 약 165) → 최대 27k 키 — 실제로는 메타에 의해 훨씬 작음
- matchup key 수: `O(C^4)` 가 이론값이나 실제로는 조합 빈도 분포가 매우 sparse → 수만 ~ 수십만 키 수준

`HashMap<StatKey, int wins, int games>` 한 엔트리 ~40B 가정 시 tier 1패치 누적이 수십 MB 안쪽. 가장 큰 tier (MASTER) 도 단일 JVM heap (수 GB) 의 1% 미만.

### 4-4. over-engineering 금지 원칙

포폴 컨셉상 외부 프레임워크 의존성 추가는 비용이 크다. plain Java + JdbcTemplate 로 끝나는 구조는 코드 리뷰·디버깅·이력 추적이 모두 한 곳에서 끝난다.

---

## 5. 3-PR 시리즈로 분해한 이유

한 PR 로 묶지 않은 이유는 **검증 가능성** 이다.

| PR | 역할 | 검증 가능성 |
|---|---|---|
| [#77](https://github.com/NamMinwu/BestDuo_BE/pull/77) | `match.collection_tier` NOT NULL 추가, 레거시 row 정리 | 컬럼 백필 후 NULL row 0 — 단순 |
| [#78](https://github.com/NamMinwu/BestDuo_BE/pull/78) | `AggregateBottomDuoFromMatch` 도입, cron 을 새 경로로 단일화. raw 는 여전히 ingest 가 씀 | 양 경로 결과 diff 비교로 알고리즘 동치 검증 가능 (cutover gate) |
| [#79](https://github.com/NamMinwu/BestDuo_BE/pull/79) | raw 측 테이블·코드·admin endpoint 일괄 제거 | 78 검증 통과 후에만 안전 |

한 PR 에 묶었다면 cutover 시점에 raw 가 이미 사라져서 diff 검증을 못 했을 것이다. 검증 가능성을 위해 **두 경로가 잠시 공존하는 중간 상태** 를 일부러 유지했다.

---

## 6. 운영 검증 — per-tier cutoff 함정

검증 절차는 `docs/aggregate-from-match-verification.md` 에 있다. 한 가지 비자명한 함정만 ADR 에 남긴다.

### 6-1. 증상

PR #78 머지 후 첫 cron 직후, raw 측 SQL 로 재계산한 결과와 MATCH 경로 결과를 단순 비교했을 때:

- matchup diff: **0 row** ✅
- stat diff: **~10,000 row** ❌

알고리즘 버그처럼 보였다.

### 6-2. 원인

스케줄러는 tier 를 직렬로 처리하고 (CHALLENGER → … → EMERALD), **그 사이에도 ingest 는 계속 `bottom_duo_raw` 에 쓴다**. 그래서 검증 시점의 raw 에는 cron 의 각 tier 가 보지 못한 row 가 섞여 있다.

- 단순 비교 쿼리: raw 전체 vs stat_agg 전체 → "ingest 가 cron 후에 추가한 row" 만큼 false diff
- matchup 은 self-join 결과라 같은 match 의 양쪽 row 가 동시에 추가돼야만 한 건이 잡힘 → ingest 중간 상태에서는 한쪽만 들어와 있는 경우가 많아 diff 가 거의 안 잡힘

### 6-3. 해결

`bottom_duo_stat_agg.updated_at` 의 **tier 별 max** 를 cutoff 로 raw 를 자른 뒤 비교:

```sql
WITH cutoff AS (
  SELECT tier, max(updated_at) AS t
  FROM bottom_duo_stat_agg
  WHERE patch_version = :patch
  GROUP BY tier
)
SELECT count(*) AS diff_count
FROM bottom_duo_stat_agg s
JOIN ( ... bottom_duo_raw r WHERE r.created_at <= cutoff.t ... ) raw_agg
  ON ...
WHERE s.wins <> raw_agg.wins OR s.games <> raw_agg.games;
```

→ **0 row**. 알고리즘 동치 확정.

### 6-4. 교훈

> 직렬 처리 cron 과 동시 ingest 가 공유 테이블 위에서 만나면, 검증의 cutoff 는 항상 **처리 시점 기준** 으로 잘라야 한다. global `max(updated_at)` 같은 단일 시각 cutoff 는 처리 순서가 늦은 tier 에 false diff 를 만든다.

---

## 7. 검증을 자동화하지 않은 이유

shadow-write + 자동 diff 알람을 만들 수도 있었으나 채택하지 않았다.

- 이번 cutover 는 **1회성** — diff 비교가 통과하면 raw 제거되어 더 이상 비교할 raw 가 없음
- 운영 인프라 (cron, 알람 시스템) 추가는 over-engineering
- 수동 SQL 한 번이면 결과가 명확하고, 절차는 `docs/aggregate-from-match-verification.md` 에 남겨 재현 가능

---

## 8. Admin endpoint 함께 제거한 이유

`/admin/aggregate/bottom-duo-stat` 과 `/admin/aggregate/bottom-duo-matchup` 두 수동 트리거 endpoint 도 같이 제거했다.

| 유지 시 비용 | 제거 시 손실 |
|---|---|
| deprecated useCase 를 위해 컨트롤러를 살려두거나, MATCH 경로 wrapper 를 새로 만들어야 함 | cron 외 수동 트리거 수단이 사라짐 |

cron 으로 충분히 작동하고 있고, 패치 직후 강제 실행 같은 수동 트리거가 실제로 필요해진 적이 없다 — YAGNI 적용. 필요해지면 새 MATCH 기반 wrapper 를 한 곳에 정의하면 된다.

---

## 9. 후속 영향

### 9-1. 가능해진 것

- **lane 확장**: `BottomDuoExtractor` 와 동급 도메인 서비스를 추가하면 ingest/aggregate 양쪽이 자동으로 동기화됨
- **과거 매치 재집계**: `match.payload_json` 만 있으면 retention 잘린 patch 도 재집계 가능. 알고리즘 수정 후 백필 가능
- **`match.payload_json` cold archive**: 향후 hot table 분리 시 단일 의존성만 옮기면 됨

### 9-2. 운영 변경점

- ingest 경로에서 `bottom_duo_raw` 쓰기 사라짐 — match 한 번 저장으로 끝
- cron 로그가 `processed=… statKeys=… matchupKeys=… upserted=…/…` 형태로 단일화
- DB 용량: `bottom_duo_raw` (수십만~수백만 row) 제거 — 메인 DB 부담 감소

### 9-3. 잔존 리스크

- in-memory 누적 크기가 데이터 규모 증가로 한계에 도달하면 streaming + 청크 upsert 로 전환 필요. 현재 측정으로는 충분히 여유.
- `BottomDuoRaw` 도메인 record 는 추출 결과 컨테이너로 계속 사용됨 — DB 와는 무관하며 이름은 의도적으로 유지 (lane 확장 시 자연스럽게 `BottomDuoRow` 같은 추상화로 발전 가능).

---

## 10. 참고

- `docs/aggregate-from-match-verification.md` — cutover 수동 검증 절차 (per-tier cutoff SQL 포함)
- `src/main/java/com/bestduo_BE/aggregate/application/AggregateBottomDuoFromMatch.java` — 단일 진입점 useCase
- `src/main/java/com/bestduo_BE/aggregate/infra/scheduler/BottomDuoAggregateScheduler.java` — cron 호출 지점
- `src/main/resources/db/migration/V4__add_match_collection_tier.sql`, `V5__drop_bottom_duo_raw.sql`
