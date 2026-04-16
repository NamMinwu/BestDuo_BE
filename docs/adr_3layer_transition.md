# ADR-002: 경량 헥사고날에서 3-Layer로 전환

> 작성일: 2026-04-16
> 상태: 채택(Accepted)
> 대상 Phase: 3-Layer 전환 (Phase 0 ~ 5)
> 선행 결정: [ADR-001 — 경량 헥사고날 채택](./adr_phase5_lightweight_hexagonal.md) (Superseded)

---

## 1. 결정 요약

ADR-001에서 채택한 "경량 헥사고날" 구조를 **3-Layer 아키텍처(presentation / application / infra)** 로 단순화한다.

- **Port 인터페이스를 14개 → 2개로 축소** (`RiotApiPort`, `ChampionMetaClient`만 유지).
- **DB 레이어 Adapter 11개 전부 제거** — `*Impl` 접미사를 떼고 `infra/persistence`에 단일 클래스로 흡수.
- 의존성 방향은 **presentation → application → infra** 단방향을 그대로 유지.
- ADR-001은 **Superseded by this ADR**로 표기.

---

## 2. 배경 — 왜 ADR-001을 재평가했는가

ADR-001은 Riot API 호출부 1곳에 한해 Port 도입이라는 **최소 경량 헥사고날**을 채택했다. 그러나 실제 코드베이스를 확장하는 과정에서, **포트화의 임계선이 점진적으로 확장**되어 다음과 같은 상태에 도달했다:

```bash
$ find src/main/java -path "*/application/port/*" | wc -l
14   # Port 인터페이스
$ find src/main/java -path "*/infra/persistence/*Impl.java" | wc -l
11   # DB-layer Adapter 구현
```

### 2-1. 발견한 문제

1. **DB-layer Port의 ROI가 음수**
   - `MatchSaver`, `MatchQueuePicker`, `MatchQueueDispatcher`, `BottomDuoRawSaver` 등 11개 Port는 모두 **JPA Repository를 1:1로 위임**할 뿐 비즈니스 로직이 없음.
   - 어댑터(`*Impl`)는 단순히 Repository 호출을 그대로 통과시킴 → **이중 추상화**.
   - 테스트는 어차피 Repository를 mock하거나 Testcontainers로 실제 DB를 사용 → Port가 격리에 기여하지 않음.

2. **Port 추가 기준이 명문화되지 않아 점진적 확장**
   - "외부 경계만 포트화"라는 ADR-001의 원칙이 코드 리뷰 시점에 일관되게 적용되지 않았음.
   - 새 기능 추가 시 "기존 패턴을 따라" Port를 추가하는 관성이 발생 → 1개 → 14개로 증식.

3. **개발자 인지 부담**
   - 각 기능에 대해 "Port + Adapter + Impl" 3개 파일을 추적해야 함.
   - 1인 개발 환경에서 파일 탐색 비용이 비즈니스 로직 작성 시간을 압박.

4. **YAGNI 위반**
   - DB 교체 가능성: 사실상 0% (PostgreSQL 의존성 깊음).
   - Repository 교체 시나리오: 발생하지 않음.
   - 그럼에도 매번 Port를 도입하는 것은 발생하지 않을 미래에 대한 비용 선지급.

---

## 3. 결정 — 무엇을 제거하고 무엇을 남겼는가

### 3-1. 제거 대상 (총 23 파일)

| 분류 | 모듈 | 제거된 인터페이스 / 구현체 |
|---|---|---|
| Port 인터페이스 (12) | `aggregate` | `BottomDuoStatAggregator`, `BottomDuoMatchupAggregator`, `BottomDuoStatFinder`, `BottomDuoMatchupFinder` |
| | `ingest` | `MatchSaver`, `BottomDuoRawSaver`, `MatchQueueEnqueuer`, `MatchQueuePicker`, `MatchQueueDispatcher`, `MatchQueueStatusCount`, `MatchPayloadReader` |
| | `leagueentry` | `LeagueEntryFetcher` |
| Adapter `*Impl` (11) | `aggregate/infra/persistence/` | 4개 `*Impl` |
| | `ingest/infra/persistence/` | 6개 `*Impl` |
| | `leagueentry` | 1개 |

### 3-2. 보존 대상 (Port 2개)

| Port | 사유 |
|---|---|
| **`RiotApiPort`** + `RiotApiHttpAdapter` + `FakeRiotApiAdapter` | 외부 HTTP API. 테스트에서 Fake 구현체로 Rate Limit / 429 / 네트워크 오류를 재현 가능. 비즈니스 로직과 전송 로직 분리의 가치가 명확. |
| **`ChampionMetaClient`** + `ChampionMetaClientImpl` | DataDragon API 호출. 별도 캐싱·버전 동기화 로직이 어댑터 안에 있고, 테스트에서 stub 가능해야 함. |

### 3-3. DB 접근 패턴 변경

```java
// BEFORE — 경량 헥사고날
public class IngestMatchDetail {
    private final MatchSaver matchSaver;            // Port
    // ...
}

class MatchSaverImpl implements MatchSaver {        // Adapter
    private final MatchJpaRepository repo;
    public void save(Match m) { repo.save(m); }     // 위임만
}

// AFTER — 3-Layer
public class IngestMatchDetail {
    private final MatchSaver matchSaver;            // 직접 클래스 (Port 인터페이스 없음)
}

@Service
public class MatchSaver {                           // 단일 클래스 (Impl 접미사 제거)
    private final MatchJpaRepository repo;
    public void save(Match m) { repo.save(m); }
}
```

---

## 4. 새로운 폴더 구조 (3-Layer)

```
com.bestduo_BE/
├── BestduoBeApplication.java
├── config/                              # 글로벌 설정
│
├── common/                              # 공유 커널 + 외부 어댑터
│   ├── domain/
│   │   ├── model/                       # Tier, QueueStatus, ChampionMeta, ...
│   │   ├── exception/                   # IllegalStateTransitionException
│   │   └── service/                     # BottomDuoExtractor (순수 도메인 로직)
│   ├── application/
│   │   └── port/
│   │       ├── RiotApiPort.java         # ★ 유지
│   │       └── ChampionMetaClient.java  # ★ 유지
│   ├── infra/
│   │   ├── persistence/                 # 공유 엔티티 + Repository
│   │   ├── riot/                        # RiotApiHttpAdapter, FakeRiotApiAdapter
│   │   └── champion/                    # ChampionMetaClientImpl
│   └── presentation/
│       └── api/                         # AdminPatchController
│
├── ingest/                              # 표준 3-layer
│   ├── application/                     # IngestMatchDetail, MatchIngestRunner
│   ├── infra/persistence/               # MatchSaver, BottomDuoRawSaver, MatchQueueDispatcher
│   └── presentation/api/                # IngestController, AdminQueueController
│
├── aggregate/                           # 표준 3-layer
│   ├── application/                     # GetBottomDuoStatistics, AggregateBottomDuoStats
│   ├── infra/persistence/               # *Aggregator, *Finder
│   └── presentation/api/                # 4개 Controller
│
├── coverage/                            # 표준 3-layer
│   ├── application/
│   ├── infra/persistence/
│   └── presentation/api/
│
├── leagueentry/                         # 의도적 편차: infra/ 없음
│   ├── application/
│   └── presentation/api/                # → common/infra/persistence 공유
│
├── pipeline/                            # 의도적 편차: application/만
│   └── application/                     # @Scheduled 배치 오케스트레이션 (HTTP 없음)
│
├── monitoring/                          # 그대로 유지
└── config/                              # 그대로 유지
```

### 4-1. 의도적 구조 편차 (왜 모든 모듈이 3폴더가 아닌가)

| 모듈 | 결여 폴더 | 사유 |
|---|---|---|
| `pipeline` | `presentation/`, `infra/` | `@Scheduled` 배치 오케스트레이션 모듈로 HTTP 엔드포인트가 없음. 자체 엔티티도 없음. **결여가 누락이 아니라 모듈 성격을 정확히 표현**. |
| `leagueentry` | `infra/` | `common/infra/persistence`의 Repository를 직접 사용. 모듈마다 동일 Repository를 복제하는 것은 DRY 위반. |

이 편차는 빈 placeholder 폴더로 강제 통일하지 않는다 (YAGNI).

---

## 5. 향후 Port 추가 기준 (셀프 가이드)

새 외부 의존성을 추가할 때, **아래 4개를 모두 만족**할 때만 Port로 만든다:

1. ☐ 외부 시스템(HTTP API, 메시지 브로커, 파일시스템)인가? — DB는 제외
2. ☐ 테스트할 때 stub/fake가 필요한가? (Mockito mock으로 충분하지 않은가)
3. ☐ 향후 다른 구현체로 교체할 가능성이 30% 이상 있는가?
4. ☐ 1줄 이상의 비즈니스 로직(재시도·캐싱·변환)이 어댑터에 들어가는가?

**4개 중 하나라도 NO면 → Port 만들지 않고 직접 구현체 사용.**

JPA Repository, 단순 wrapper, 사내 유틸 → 항상 직접 사용.

---

## 6. ADR-001과의 차이

| 항목 | ADR-001 (경량 헥사고날) | ADR-002 (3-Layer) |
|---|---|---|
| Port 개수 | 1개 (`RiotApiPort`) | 2개 (`RiotApiPort`, `ChampionMetaClient`) |
| DB-layer Port | 명시적으로 미적용 | 명시적으로 금지 (Port 추가 기준 4번) |
| 폴더 구조 | `application/port + infra/adapter` | `presentation/ + application/ + infra/` |
| `*Impl` 접미사 | 허용 | 단일 구현 시 제거 |
| 모듈 간 인프라 공유 | 미정의 | `common/infra` 공유 명시적으로 허용 |
| Port 추가 기준 | 비공식 | 4개 체크리스트 명문화 |

---

## 7. 트레이드오프 — 무엇을 잃었는가

- **DB 교체 유연성**: 이미 ADR-001에서 포기한 부분이며, 본 결정으로 명시적으로 확정.
- **인터페이스로 인한 mock 용이성**: Mockito는 concrete class도 mock 가능하므로 실질 손실 없음.
- **"교과서적 헥사고날" 외형**: 표면적 일관성을 잃었지만, 실제 가치는 의존성 방향과 테스트 격리에 있고 둘 다 보존됨.

---

## 8. 검증

- [x] Port 개수: `find src -path "*/application/port/*" | wc -l` → 2
- [x] DB Impl 개수: `find src -name "*Impl.java" -path "*/infra/persistence/*" | wc -l` → 0
- [x] 컴파일 통과
- [x] 전체 테스트 green (`./gradlew test`)
- [x] 의존성 방향 일관성 (presentation → application → infra)

---

## 9. 참고

- [ADR-001 — 경량 헥사고날 채택 (Superseded)](./adr_phase5_lightweight_hexagonal.md)
- [3-Layer 전환 계획서](./refactoring_to_3layer_plan.md)
- [아키텍처 비교 학습 노트](./architecture_study_3layer_vs_hexagonal.md)
- [현재 시스템 아키텍처](./architecture.md)
