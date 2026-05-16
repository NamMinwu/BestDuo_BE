# Aggregate from match — 첫 cron 검증 절차

## 배경

PR 2 에서 cron 의 stat/matchup 집계 경로를 `bottom_duo_raw` 위 SQL 에서
`match.payload_json` 위 메모리 누적 ({@link AggregateBottomDuoFromMatch}) 으로 단일화했다.

`bottom_duo_raw` 테이블 자체는 이번 PR 에서 제거하지 않고 ingest 경로에서 계속 작성된다.
이 상태에서 첫 cron 사이클 직후 raw 기반 결과와 match 기반 결과의 정합성을 한 번 수동 확인한다.

## 검증 시점

- 새 코드 배포 후 첫 cron(매일 04:00 Asia/Seoul) 이 완료된 직후
- 이후 매 배포마다 반복할 필요 없음 — 1회성 cutover 검증

## 검증 쿼리

### 1. stat 결과 비교

같은 (patch, tier, adc, sup) 키에서 raw 기반 재계산값과 현재 `bottom_duo_stat_agg`(=match 기반 upsert 결과) 가 다른 row 를 찾는다.

```sql
SELECT
  s.patch_version, s.tier, s.adc_champion_id, s.sup_champion_id,
  s.wins  AS match_wins,
  s.games AS match_games,
  raw_agg.wins  AS raw_wins,
  raw_agg.games AS raw_games
FROM bottom_duo_stat_agg s
JOIN (
  SELECT
    COALESCE(patch, 'UNKNOWN')         AS patch_version,
    collection_tier                    AS tier,
    adc_champion_id::text              AS adc_champion_id,
    sup_champion_id::text              AS sup_champion_id,
    SUM(CASE WHEN win THEN 1 ELSE 0 END) AS wins,
    COUNT(*)                           AS games
  FROM bottom_duo_raw
  GROUP BY 1, 2, 3, 4
) raw_agg
  ON raw_agg.patch_version   = s.patch_version
 AND raw_agg.tier            = s.tier
 AND raw_agg.adc_champion_id = s.adc_champion_id
 AND raw_agg.sup_champion_id = s.sup_champion_id
WHERE s.wins  <> raw_agg.wins
   OR s.games <> raw_agg.games;
```

**기대 결과: 0 row** → match 기반 결과 = raw 기반 결과 = 안전 확인.

### 2. matchup 결과 비교

```sql
SELECT
  m.patch_version, m.tier,
  m.my_adc_champion_id, m.my_sup_champion_id,
  m.opp_adc_champion_id, m.opp_sup_champion_id,
  m.wins  AS match_wins,
  m.games AS match_games,
  raw_agg.wins  AS raw_wins,
  raw_agg.games AS raw_games
FROM bottom_duo_matchup_agg m
JOIN (
  SELECT
    COALESCE(a.patch, 'UNKNOWN')    AS patch_version,
    a.collection_tier               AS tier,
    a.adc_champion_id::text         AS my_adc_champion_id,
    a.sup_champion_id::text         AS my_sup_champion_id,
    b.adc_champion_id::text         AS opp_adc_champion_id,
    b.sup_champion_id::text         AS opp_sup_champion_id,
    SUM(CASE WHEN a.win THEN 1 ELSE 0 END) AS wins,
    COUNT(*)                        AS games
  FROM bottom_duo_raw a
  JOIN bottom_duo_raw b
    ON a.match_id        = b.match_id
   AND a.collection_tier = b.collection_tier
   AND a.team_id        <> b.team_id
  GROUP BY 1, 2, 3, 4, 5, 6
) raw_agg
  ON raw_agg.patch_version       = m.patch_version
 AND raw_agg.tier                = m.tier
 AND raw_agg.my_adc_champion_id  = m.my_adc_champion_id
 AND raw_agg.my_sup_champion_id  = m.my_sup_champion_id
 AND raw_agg.opp_adc_champion_id = m.opp_adc_champion_id
 AND raw_agg.opp_sup_champion_id = m.opp_sup_champion_id
WHERE m.wins  <> raw_agg.wins
   OR m.games <> raw_agg.games;
```

**기대 결과: 0 row**.

> matchup 은 self-join 결과라 raw 에 같은 match 의 양 팀 row 가 모두 들어가 있어야만 한 건이 잡힌다.
> ingest 가 cron 도중에 한 팀만 먼저 insert 한 중간 상태에서는 양쪽 모두 0 으로 잡혀 timing
> 영향이 거의 없다 — 첫 쿼리에서 바로 0 이 나오는 경우가 일반적.

### 3. stat 차이가 나올 때 — per-tier cutoff 보정

cron 은 tier 를 직렬로 처리하고 그 사이 ingest 가 계속 `bottom_duo_raw` 에 쓰기 때문에,
검증 시점의 raw 에는 stat_agg 가 보지 못한 row 가 섞여 있을 수 있다. 1번 쿼리에서 diff
가 0 이 아니면 곧바로 알고리즘 오류로 단정하지 말고, raw 를 tier 별 stat_agg.updated_at
이전으로 잘라서 다시 비교한다.

```sql
WITH cutoff AS (
  SELECT tier, max(updated_at) AS t
  FROM bottom_duo_stat_agg
  WHERE patch_version = :patch
  GROUP BY tier
)
SELECT count(*) AS diff_count
FROM bottom_duo_stat_agg s
JOIN (
  SELECT
    COALESCE(r.patch, 'UNKNOWN') AS patch_version,
    r.collection_tier            AS tier,
    r.adc_champion_id::text      AS adc_champion_id,
    r.sup_champion_id::text      AS sup_champion_id,
    SUM(CASE WHEN r.win THEN 1 ELSE 0 END) AS wins,
    COUNT(*)                     AS games
  FROM bottom_duo_raw r
  JOIN cutoff c
    ON c.tier = r.collection_tier
   AND r.created_at <= c.t
  WHERE COALESCE(r.patch, 'UNKNOWN') = :patch
  GROUP BY 1, 2, 3, 4
) raw_agg
  ON raw_agg.patch_version   = s.patch_version
 AND raw_agg.tier            = s.tier
 AND raw_agg.adc_champion_id = s.adc_champion_id
 AND raw_agg.sup_champion_id = s.sup_champion_id
WHERE s.patch_version = :patch
  AND (s.wins <> raw_agg.wins OR s.games <> raw_agg.games);
```

**기대 결과: 0**. cutoff 미적용에서는 diff 가 나오더라도 cutoff 적용 후 0 이면 알고리즘
동치 — RAW 와 MATCH 경로 사이 timing slippage 일 뿐이다.

## 차이가 발견될 경우

cutoff 적용 후에도 diff 가 0 이 아니면 알고리즘 차이 가능성이 높다. 다음 순서로 분기한다.

1. 차이가 발생한 (patch, tier, adc, sup) 조합을 식별
2. 같은 키의 `bottom_duo_raw` row 들을 직접 조회해 추출 로직 차이 분석
3. `BottomDuoExtractor` 또는 `AggregateBottomDuoFromMatch` 의 누적 로직 점검
4. 수정 후 해당 (patch, tier) 만 재처리 트리거하거나 다음 cron 까지 대기

### 흔한 false-positive 패턴

- **단일 팀만 raw 에 들어간 match**: `match.payload_json` 에는 양 팀이 있지만 어느 시점에
  ingest 가 한쪽만 저장하고 끝난 historical row. RAW self-join (matchup) 에서는
  자연스럽게 빠지고, MATCH 경로에서는 두 팀 모두 추출돼 stat 에만 잡혀 diff 로 보임.
  → MATCH 결과가 더 정확. 굳이 보정하지 않는다.

## 검증 완료 후

검증이 통과하면 다음 PR (bottom_duo_raw 제거) 을 진행한다. 그 PR 에서:

- `bottom_duo_raw` 테이블 + 관련 entity/repository 제거
- `IngestMatchDetail` 에서 `BottomDuoRawSaver` 호출 제거
- `CleanupOldPatches` 에서 raw cleanup 제거
- `AggregateBottomDuoStats` / `AggregateBottomDuoMatchup` (deprecated) 클래스 삭제
