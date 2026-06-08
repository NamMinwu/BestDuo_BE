# 데이터 파이프라인 요구사항 명세 (Requirements)

> 작성일: 2026-06-05
> 상태: 초안(Draft) — 일부 SLO 수치 `[확인 필요]`
> 목적: 구현/마이그레이션(예: [ADR-008](./adr_remove_match_queue_inline_pipeline.md)) 이전에 **"이 파이프라인이 무엇을 보장해야 하는가"** 를 구조 비의존적으로 못박는다. 어떤 파이프라인 구조든 이 요구사항을 만족해야 한다.

---

## 1. 목적 & 범위

BestDuo 데이터 파이프라인은 Riot 공식 API에서 **에메랄드+ 솔로 랭크 매치**를 수집·저장하고, **패치 단위로 ADC/서포터 듀오 조합의 승률·픽률·랭킹·매치업·카운터**를 집계해 REST로 제공한다.

- **In scope**: Seed → Collect → Ingest → Aggregate → (Archive) 파이프라인, 그 산출물의 신선도·신뢰도·가용성 보장.
- **Out of scope**: §7.

---

## 2. 제품 컨텍스트 — 파이프라인이 떠받치는 출력

| API | 출력 단위 |
|---|---|
| `GET /bottom-duo/stats` | (tier, patch, ADC, SUP) 승률·픽률·랭킹·tier delta |
| `GET /bottom-duo/matchups` | (tier, patch, 내 듀오 vs 상대 듀오) 매치업 |
| `GET /bottom-duo/counters` | (tier, patch, 듀오) 최저 승률 카운터 |

→ 분석의 최소 단위 = **셀(cell)**: stat = (tier × ADC × SUP), matchup = (tier × 내듀오 × 상대듀오). 품질 요구(§4)는 이 셀 단위로 정의된다.

---

## 3. 기능 요구 (FR)

### FR-1. 데이터 출처 / 정합성
- Riot `league-v4`(summoner) + `match-v5`(matchId, match 상세)에서만 수집. 순수 통계 목적.
- **patch 순도(purity)**: 한 patch 통계에 다른 patch 매치가 섞이지 않는다. (입구 `startTime` 필터 + 출구 patch 검증.)

### FR-2. 수집 범위 — tier별 분리 (핵심)
- 대상 tier = **CHALLENGER / GRANDMASTER / MASTER / DIAMOND / EMERALD 5개, 각각 분리.** (Q4)
- 수집과 집계 **둘 다 tier 단위**로 분리되어야 한다. (어떤 매치가 어느 tier 버킷에서 왔는지 복원 가능해야 함.)

### FR-3. 수집 모델 — 현재 patch best-effort
- **현재 patch**를 대상으로 **일일 rate-limit 예산이 닿는 만큼** 수집한다. (Q1)
- tier별 차등(예: apex 더 깊게)을 허용한다.
- 고정 "총 목표 매치 수"는 요구가 아니다. 품질은 §4의 셀 단위 기준으로 보장한다.

### FR-4. 증분 · 멱등
- 매일 증분 갱신. 이미 수집된 매치는 재수집하지 않는다(dedup).
- 재시작/재실행이 데이터를 중복·손상시키지 않는다(idempotent). (→ NFR-5)

### FR-5. 집계
- (tier, patch)별로 듀오 조합의 wins/games/win_rate/pick_rate, 랭킹(rank_score), 매치업/카운터를 산출.
- 집계 기준이 바뀌어도 **외부 호출 없이 재집계** 가능해야 한다 (현재 payload가 DB에 있는 patch 한정 — [ADR-006](./adr_aggregate_from_match_payload.md)).

### FR-6. patch 전환
- 새 patch 등장 시, 그 patch 기준 수집·집계로 전환된다. 전환 grace 처리를 포함한다.
- 새 patch 통계가 노출되기까지의 신선도는 NFR-1.

---

## 4. 데이터 품질 요구 (QR) — *핵심*

> "예산 best-effort 수집"은 셀의 **머리**(인기 조합/저tier)엔 충분하고 **꼬리**(희귀 조합/고tier/매치업)에만 약하다. 따라서 품질은 수집량이 아니라 **셀당 최소 표본**으로 보장한다.

- **QR-1. 표시 정직성 (두 레버)**: 품질은 단일 임계가 아니라 ① **저표본 평활**(QR-2, prior 100)이 랭킹을 보호하고 ② **표본 수(games)를 항상 노출** + 표시 바닥 미만은 **"표본 부족" low-confidence 라벨**(숨기지 않음 — 고tier 데이터를 보여주려는 의도와 일치)로 정직성을 보장한다. 현재 값: 랭킹 바닥 `MIN_GAMES=4`(`ComputeBottomDuoRanking:20`) + 평활 prior 100. 표시 라벨 임계(제안 ~30)는 serving 결정 — §8.
- **QR-2. 저표본 평활**: 랭킹/비교는 `adjusted_win_rate = (wins+50)/(games+100)` (Bayesian smoothing, 현행) 로 저표본 셀의 과대평가를 억제한다.
- **QR-3. 고tier 한계 명시**: 챌린저 등 인구가 적은 tier는 구조적으로 표본이 작다. QR-1 가드가 가장 크게 작용하는 지점이며, "데이터 부족"을 사용자에게 정직하게 노출한다.
- **QR-4. patch 순도**: FR-1 재확인 — 통계 신뢰의 전제.

---

## 5. 비기능 요구 (NFR) — production-like SLO (Q2)

> 수집 *커버리지*는 예산 best-effort라 SLO를 안 걸지만, **신선도·가용성·신뢰도·운영 안전**엔 production-like SLO를 건다.

| # | 요구 | SLO |
|---|---|---|
| NFR-1 | **신선도(freshness)** | 새 patch는 출시 후 **3일(`PATCH_GRACE_PERIOD_DAYS=3`) grace** 후 노출 — grace 중에는 직전 patch를 effective로 서빙(`PatchVersionService:20`, 초반 표본 부족 회피). effective patch는 **매일** 재집계(cron). |
| NFR-2 | **읽기 가용성** | 공개 REST API uptime **≥ 99%**. 집계 지연 중에도 직전 patch 데이터는 계속 서빙. |
| NFR-3 | **파이프라인 liveness** | 수집/집계가 진행 중임을 heartbeat로 관측. "진행 멈춤"(throughput=0 지속)을 알람. |
| NFR-4 | **에러율 / rate-limit 준수** | 지속 429 = 0(예산 하한 90% 운영). 개별 ingest 실패는 1회 시도 후 skip + `ingest_failure_total{reason}` 기록(전용 재시도 머신 없음 — 재수집 + 10-참가자 redundancy가 자연 재시도). **시스템 실패(auth/대량 5xx)는 조용히 흡수 금지** — 실패율 급증 알람 + auth halt. |
| NFR-5 | **재시작 안전** | 서버 재시작/재배포 후 데이터 손실 0, 자동 재개. 재시작 비용은 in-flight 작업 단위로 bounded(전체 부하와 무관). 크래시 중간 상태가 중복·누락을 만들지 않는다. |
| NFR-6 | **디스크 제약 (바인딩)** | DB 크기 `< 임계`(현재 Hobby 5GB의 80% 룰, `bestduo_db_size_bytes/5e9 > 0.8` Grafana 알림). 무한 증가하는 테이블이 없어야 한다. |
| NFR-7 | **보존(retention) — 적응적** | 고정 정책이 아니라 **디스크 여유를 보며 운영자가 조정**한다(Q3). stat은 최신 N patch, raw payload는 archive 후 삭제(최신 patch 보호)를 기본 가이드로 하되, NFR-6 임계를 1차 기준으로 유연 조정. |
| NFR-8 | **관측성(observability)** | 처리율·에러분류·in-flight·pending·신선도·디스크를 Micrometer→Prometheus/Grafana로 노출. 상태-테이블 스캔이 아닌 event-emitted 메트릭을 지향(디스크 비용 회피). |

---

## 6. 제약 (Constraints)

- **C-1. 단일 Riot API key**: 현재 dev 키. 처리량 천장이 키당 고정 → 멀티 워커 수평 확장은 예산을 늘리지 못함.
  - 향후 prod 키: `500 req/10s`, `30,000 req/10min` = **50 req/s 지속**. 단일 프로세스 + 동시 10~25로 포화([ADR-008 §8](./adr_remove_match_queue_inline_pipeline.md)).
- **C-2. 인프라**: Railway Hobby(디스크 5GB, 네이티브 볼륨 알림 미제공 → Grafana 임계 룰로 대체).
- **C-3. Riot ToS / 공식 API only**: 비공식 수집·스크래핑 금지. rate limit 준수 필수.
- **C-4. 단순성 원칙**: 지금 필요하지 않은 구조(추상화/큐/멀티프로세스)는 만들지 않는다. pressure가 real해질 때 도입.

---

## 7. 범위 외 (Out of scope)

- 바텀 외 라인(탑/정글/미드) 통계.
- 실시간(분 단위) 통계 — 일일 신선도로 충분.
- 멀티 리전(현재 단일 regional host).
- 멀티 머신/멀티 프로세스 수평 확장 — *키 추가로 예산이 실제 N배가 될 때까지* 보류([ADR-008 §9](./adr_remove_match_queue_inline_pipeline.md)).

---

## 8. 미해결 / 확인 필요 (Open questions)

| 항목 | 필요 결정 |
|---|---|
| **QR-1 표시 라벨 임계** (serving) | "표본 부족" 라벨을 붙일 게임 수 (stat 셀 ~30 제안, matchup 셀 더 낮게). 파이프라인과 독립. |
| **QR-4 시스템 실패 알람 임계** | `ingest_failure_total` 급증 알람 발화 기준(NFR-4). |

> 해소됨: NFR-1 신선도(grace 3일+일일), NFR-2 uptime(99%), 저표본 노출 방식("숨김" 아님 → 표본 노출+라벨, QR-1), 실패 재시도(전용 머신 없음 — NFR-4).

---

## 9. 참고

- [ADR-008](./adr_remove_match_queue_inline_pipeline.md) — 파이프라인 구조 결정(이 요구사항을 만족하는 구조 선택)
- [ADR-006](./adr_aggregate_from_match_payload.md) — payload 기반 재집계(FR-5)
- [pipeline_implementation_map.md](./pipeline_implementation_map.md) — 현재 구현 맵
- [incident_postgres_disk_full_recovery_mode.md](./incident_postgres_disk_full_recovery_mode.md) — NFR-6 디스크 제약의 근거
