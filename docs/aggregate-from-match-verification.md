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

## 차이가 발견될 경우

1. 차이가 발생한 (patch, tier, adc, sup) 조합을 식별
2. 같은 키의 `bottom_duo_raw` row 들을 직접 조회해 추출 로직 차이 분석
3. `BottomDuoExtractor` 또는 `AggregateBottomDuoFromMatch` 의 누적 로직 점검
4. 수정 후 해당 (patch, tier) 만 재처리 트리거하거나 다음 cron 까지 대기

## 검증 완료 후

검증이 통과하면 다음 PR (bottom_duo_raw 제거) 을 진행한다. 그 PR 에서:

- `bottom_duo_raw` 테이블 + 관련 entity/repository 제거
- `IngestMatchDetail` 에서 `BottomDuoRawSaver` 호출 제거
- `CleanupOldPatches` 에서 raw cleanup 제거
- `AggregateBottomDuoStats` / `AggregateBottomDuoMatchup` (deprecated) 클래스 삭제
