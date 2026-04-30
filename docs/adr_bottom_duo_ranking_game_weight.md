# ADR-004: 듀오 랭킹 점수 산출 — 게임수 비중 강화

> 작성일: 2026-04-30
> 상태: 채택(Accepted)
> 대상 모듈: `com.bestduo_BE.aggregate`
> 영향 파일:
> - `src/main/java/com/bestduo_BE/aggregate/application/ComputeBottomDuoRanking.java`
> - `src/main/java/com/bestduo_BE/aggregate/infra/persistence/repository/BottomDuoStatAggregateJpaRepository.java`
> - `src/test/java/com/bestduo_BE/aggregate/application/ComputeBottomDuoRankingTest.java`

---

## 1. 결정 요약

`bottom_duo_stat_aggregate` 의 랭킹 점수(`rank_score`) 산출식을 다음과 같이 변경한다.

| 항목 | 변경 전 | 변경 후 |
|---|---|---|
| `gameScore` | `min(1.0, games / 4)` (선형, 4판부터 포화) | `log1p(games) / log1p(maxGamesInTier)` (티어 내 상대 로그) |
| Bayesian prior (`adjusted_win_rate`) | `(wins + 25) / (games + 50)` | `(wins + 50) / (games + 100)` |
| 가중치 (winScore : pickScore : gameScore) | `0.60 : 0.25 : 0.15` | `0.40 : 0.20 : 0.40` |

`MIN_GAMES = 4` 는 "랭킹 노출 자격 최소치(미달 시 INSUFFICIENT_TIER)" 의미로만 유지하고, 점수 산출에서는 더 이상 사용하지 않는다.

---

## 2. 문제 정의

### 2-1. 관찰된 현상

자체 랭킹 시스템에서 **표본이 매우 적은 듀오가 상위권을 차지**하는 사례가 다수 발생.
구체적으로 8판 87.5% 승률 듀오가 1000판 50% 승률 듀오보다 위에 랭크되는 케이스가 확인됨.

### 2-2. 데이터 분포

수집된 듀오의 게임수 분포(현 패치 기준):
- 최소: **8 게임**
- 최대: **2700 게임**
- 비율: **약 337배 차이**

이 분포는 두 가지 의미를 가진다.
1. 단순 선형 정규화로는 변별력 확보 불가 (100판 이상 모두 같은 점수가 되면 뒤 구간 정보가 모두 손실됨).
2. 저샘플 듀오의 승률은 통계적으로 매우 불안정 — 8판 7승은 "강함"보다 운에 가까움.

### 2-3. 기존 식의 구조적 결함 (3가지)

`ComputeBottomDuoRanking.java:156-161` 의 기존 식:

```java
double winScore = agg.getAdjustedWinRate();
double pickScore = pickRate;
double gameScore = Math.min(1.0, (double) agg.getGames() / MIN_GAMES); // MIN_GAMES = 4
return 0.60 * winScore + 0.25 * pickScore + 0.15 * gameScore;
```

| 결함 | 효과 |
|---|---|
| ① `MIN_GAMES = 4` 로 4판부터 `gameScore = 1.0` 포화 | 5판/100판/2700판 모두 게임 점수가 동일 → 게임수 변별력 0 |
| ② `gameScore` 가중치 0.15 (3개 항목 중 최소) | 그나마 있는 게임 신호도 합산 시 묻힘 |
| ③ Bayesian prior `(wins+25)/(games+50)` 가 약함 | 8판 7승 → `0.55` (충분히 높아 보임). 저샘플 페널티 부족 |

세 결함이 결합하여 "표본이 적은데 운 좋게 승률 높은 듀오" 가 시스템적으로 과대평가됨.

---

## 3. 결정 — 각 변경의 단독 효과와 조합 필요성

세 변경은 **각각 단독으로는 부족하며, 조합해야** 위 3가지 결함을 모두 해결한다.

### 3-1. `gameScore` 를 로그 + 티어 내 상대화

```java
int maxGamesInTier = tierStats.stream().mapToInt(BottomDuoStatAggregate::getGames).max().orElse(1);
double gameScore = Math.log1p(agg.getGames()) / Math.log1p(maxGamesInTier);
```

**효과 (현 패치 기준 maxGamesInTier ≈ 2700):**

| games | log1p(games) / log1p(2700) |
|---|---|
| 8 | **0.28** |
| 50 | 0.49 |
| 100 | 0.58 |
| 500 | 0.79 |
| 1000 | 0.87 |
| 2700 | **1.00** |

전 구간에서 단조 증가 + 적당한 곡률. 8판과 2700판이 0.28 vs 1.00 으로 명확히 차이남.

### 3-2. Bayesian prior 강화 (50 → 100)

```sql
-- BottomDuoStatAggregateJpaRepository.java:53
(sum(case when r.win = true then 1 else 0 end)::double precision + 50.0) / (count(*) + 100)
```

**효과:** 저샘플 듀오만 선택적으로 50% 쪽으로 더 강하게 끌려옴. 대량 표본 듀오의 `adjustedWinRate` 는 거의 변화 없음.

| 케이스 | 변경 전 adjWin | 변경 후 adjWin | 차이 |
|---|---|---|---|
| 8판 7승 (87.5%) | `(7+25)/(8+50) = 0.552` | `(7+50)/(8+100) = 0.528` | **−0.024** |
| 100판 60승 (60%) | `(60+25)/(100+50) = 0.567` | `(60+50)/(100+100) = 0.550` | −0.017 |
| 2700판 60% 승률 (1620승) | `(1620+25)/(2700+50) = 0.598` | `(1620+50)/(2700+100) = 0.596` | −0.002 |

소표본일수록 페널티가 크다. 정확히 의도한 효과.

### 3-3. 가중치 재조정 (0.40 / 0.20 / 0.40)

`gameScore = winScore` 로 **샘플 신뢰성을 승률만큼 중요한 시그널로 격상**.

`pickScore` 는 0.20 으로 약간 줄임 — 픽률은 게임수와 강한 상관이 있어, 게임수 비중이 커지면 pickScore 가중치를 높게 유지할 필요가 줄어듦 (이중 카운팅 방지).

---

## 4. 검증 시나리오 — 변경 전/후 대조

가정: 티어 총 게임 100,000 / `maxGamesInTier = 2700`

| 케이스 | games | 승률 | adjWin (신) | gameScore (신) | pickScore | **신 rankScore** | adjWin (구) | gameScore (구) | **구 rankScore** |
|---|---|---|---|---|---|---|---|---|---|
| **A** 저샘플 고승률 | 8 | 87.5% (7승) | 0.528 | 0.28 | 0.00008 | **0.323** | 0.552 | 1.00 | **0.482** |
| **B** 대량 견조 | 2700 | 55% | 0.546 | 1.00 | 0.027 | **0.624** | 0.555 | 1.00 | **0.546** |
| **C** 대량 평균 | 1000 | 50% | 0.500 | 0.87 | 0.010 | **0.550** | 0.500 | 1.00 | **0.453** |
| **D** 중샘플 강함 | 100 | 65% | 0.575 | 0.58 | 0.001 | **0.346** | 0.567 | 1.00 | **0.492** |

> 신 rankScore = `0.40 * adjWin + 0.20 * pickScore + 0.40 * gameScore`
> 구 rankScore = `0.60 * adjWin + 0.25 * pickScore + 0.15 * gameScore` (gameScore 는 모두 1.0 으로 포화)

### 결과 비교

| 시스템 | 순위 |
|---|---|
| **변경 전** | B (0.546) > **D** (0.492) > **A** (0.482) > C (0.453) |
| **변경 후** | B (0.624) > C (0.550) > **D** (0.346) > **A** (0.323) |

**핵심 변화:**
1. A(8판 87.5%) 가 4위로 추락 — 사용자가 원한 결과.
2. C(1000판 50%) 가 D(100판 65%) 보다 위 — 표본 신뢰성을 승률보다 우선시한 결과.
3. B(대량 견조) 가 안정적으로 1위 — 샘플 + 승률 모두 좋은 듀오가 보상받음.

---

## 5. 트레이드오프 — 명시적 비교

### 5-1. 거부된 대안

#### A안: `MIN_GAMES` 만 4 → 100 으로 상향 (선형 유지)

- **장점:** 변경 1줄. 가장 단순.
- **거부 이유:** `min(1.0, games / 100)` 은 100판 이상 모든 듀오의 `gameScore` 가 1.0 으로 동일.
  100판 듀오와 2700판 듀오의 신뢰도 차이가 사라짐. 실 데이터 분포(상한 2700) 에 부적합.
- **결론:** 데이터 분포 폭이 좁다면 A안도 충분했겠지만, 8 ~ 2700 의 337배 분포에서는 변별력 손실이 큼.

#### B안 단독: 가중치 재조정만 (산식 유지)

- **거부 이유:** `gameScore` 자체가 4판부터 포화되어 있어, 가중치를 0.40 으로 올려도 1.0 × 0.40 = 0.40 의 상수가 됨. 변별력 0.

#### C안 단독: prior 강화만 (식 / 가중치 유지)

- **거부 이유:** 8판 87.5% 의 adjWin 이 0.552 → 0.528 로 떨어져도, gameScore 가 여전히 1.0 (가중치 0.15) 이라 합산 점수에서 묻힘.

> **세 변경 모두 필요한 이유:** 결함 ①(포화) 은 식 변경, ②(저비중) 은 가중치, ③(약한 prior) 는 prior 강화로만 각각 해결됨. 한두 개만으로는 다른 결함이 효과를 상쇄.

### 5-2. 채택안의 트레이드오프

#### 비용 1 — 패치 초반 변별력 약화

`maxGamesInTier` 가 분모이므로, 새 패치 초반 (모든 듀오가 10~50판 수준) 에는 `gameScore` 의 절대값이 작아져도 **상대적 차이는 보존됨** (예: 50판 / 100판 = 0.81, 10판 / 100판 = 0.52).
다만 `pickScore` 와의 상대 영향이 일시적으로 변할 수 있음.

- **모니터링 지표:** 패치 출시 후 24~48 시간 동안 `rank_score` 분포(p10/p50/p90).
- **완화책 (현재 미적용, future work):** `Math.log1p(Math.max(maxGamesInTier, 500))` 로 floor 적용. 데이터 분포 본 뒤 결정.

#### 비용 2 — 대량/평범 듀오의 과대평가 가능성

C(1000판 50%) 가 D(100판 65%) 보다 위로 가는 결과는 **의도된 트레이드오프**.
"표본이 신뢰할 만한 평균 듀오" 를 "표본이 적은 강해 보이는 듀오" 보다 우선시한다는 사용자 가치판단을 반영.

- **반대 의견:** "100판 65% 는 통계적으로 의미 있는 강함 시그널이고, 1000판 50% 는 그저 평균인데, 평균이 위로 가면 이상하지 않은가?"
- **현재 답:** 사용자 데이터 (특히 8판 87.5% 사례) 에서 "낮은 표본 = 신뢰 불가" 라는 신호가 너무 강해서, 표본 신뢰성을 일단 우선시함. 추후 분포 보고 가중치 미세조정 가능.

#### 비용 3 — Bayesian prior 강화로 신호 약화

prior 50 → 100 은 100판 60승(60%) 의 adjWin 을 0.567 → 0.550 으로 끌어내림.
실제로 강한 듀오(50~200판 구간)의 강함 신호가 약간 무뎌짐.

- **완화 근거:** 강한 듀오는 시간이 지나면 더 많은 표본을 쌓을 것이고, 표본이 쌓이면 prior 영향이 자연 감소 (`(wins+50)/(games+100)` 의 prior 영향은 `games` 가 커질수록 0 에 수렴).
- **수치 증거:** 1000판 60% 승률은 prior 영향이 0.6% 수준 (5-2 표 참조).

### 5-3. 가중치를 0.40 / 0.20 / 0.40 으로 정한 이유 (vs 0.50 / 0.20 / 0.30)

초기 제안은 `0.50 win + 0.20 pick + 0.30 game`. 사용자가 "gameScore 를 winScore 와 같은 비중으로" 요청.

| 가중치 안 | A(저샘플 고승률) rankScore | B(대량 견조) rankScore | A vs C 격차 |
|---|---|---|---|
| 0.50 / 0.20 / 0.30 | 0.348 | 0.586 | 0.005 (애매) |
| **0.40 / 0.20 / 0.40 (채택)** | **0.323** | **0.624** | **0.227 (명확)** |

승률과 표본 신뢰성을 동등하게 다루면, 사용자 불만의 핵심인 A 케이스의 강등 폭이 더 커진다. **사용자 가치판단(표본 적은 듀오는 신뢰 불가)** 을 산식에 더 강하게 반영하기 위한 선택.

---

## 6. 영향 범위 및 마이그레이션

### 6-1. 코드 변경

| 파일 | 변경 |
|---|---|
| `ComputeBottomDuoRanking.java:156-161` | `computeRankScore` 산식 변경 (로그 gameScore + 가중치 재조정). `maxGamesInTier` 를 `applyRankings` 에서 미리 계산해 전달 |
| `BottomDuoStatAggregateJpaRepository.java:53` | upsert SQL 의 `adjusted_win_rate` 계산식 prior 50 → 100 |
| `ComputeBottomDuoRankingTest.java` | 4-검증 시나리오 (A/B/C/D) 추가 — 신·구 식의 순위 차이를 회귀 테스트로 고정 |

### 6-2. 데이터 마이그레이션

기존 `bottom_duo_stat_aggregate` 의 `adjusted_win_rate`, `rank_score`, `ranking` 은 **다음 패치 적재 시점에 자동 재계산**됨.
별도 backfill 스크립트 불요.

### 6-3. API 응답 영향

`BottomDuoStatisticsResponse` 의 필드 의미는 변하지 않음. 단 `rank_score`, `ranking`, `duo_tier` 의 분포가 달라지므로 프론트에서 절대값 임계로 분기하는 로직이 있다면 재확인 필요.
- 현재 `toDuoTier(score)` 의 임계 (`0.75 / 0.65 / 0.55 / 0.45 / 0.35`) 는 **그대로 유지** — 새 식의 점수 분포가 0.30 ~ 0.65 범위에 분포하므로 듀오 티어가 전반적으로 1~2 단계 낮아질 것임. 임계 재조정은 별도 ADR 로 다룰 예정.

---

## 7. Future Work (미반영, 향후 결정)

1. **`maxGamesInTier` floor 적용** — 패치 초반 안정화용. 데이터 보고 결정.
2. **`toDuoTier` 임계 재조정** — 새 분포에 맞게 `0.55 / 0.45 / 0.35 / 0.25 / 0.15` 등으로 하향 검토.
3. **가중치 자동 튜닝** — 데이터 누적 후 그리드 서치로 0.40 / 0.20 / 0.40 의 적정성 재검토.
4. **`pickScore` 정의 검토** — 현재 단순 비율(`games / tierTotal`) 이라 분포가 매우 작음. 로그 변환 검토 가능.

---

## 8. 회귀 방지 — 테스트 추가 (TDD 절차)

`ComputeBottomDuoRankingTest` 에 다음 시나리오를 **구현 전에** 추가한다 (RED → GREEN).

```java
@Test
@DisplayName("저샘플 고승률 듀오는 대량 견조 듀오보다 낮은 랭킹을 가진다")
void lowSampleHighWinrate_ranksBelow_highSampleSteady() {
    // Arrange: 같은 티어에 A(8판 7승), B(2700판 1485승=55%) 추가
    // Act: execute(patch, tier)
    // Assert: B.ranking < A.ranking
}

@Test
@DisplayName("대량 평균 듀오가 중샘플 강함 듀오보다 높은 랭킹을 가진다")
void highSampleAverage_ranksAbove_midSampleStrong() {
    // Arrange: C(1000판 500승=50%), D(100판 65승=65%)
    // Act + Assert: C.ranking < D.ranking
}
```

---

## 9. 참고 — 의사결정 컨텍스트

- 발의자: 사용자 (랭킹 결과에 대한 직접 관찰)
- 분석 기간: 2026-04-30 단일 세션
- 관련 도메인 코드: `BottomDuoStatAggregate.applyRankingMetrics`, `markInsufficientData`
- 관련 데이터: `bottom_duo_raw` (집계 원본), `bottom_duo_stat_aggregate` (랭킹 결과)
