# 리팩토링 계획: 경량 헥사고날 → 3-Layer 전환

> 작성일: 2026-04-16
> 선행 문서: `docs/architecture_study_3layer_vs_hexagonal.md`
> 목표: 14개 Port + 11개 Adapter 중 **2개 Port만 남기고 나머지 제거**, 폴더 구조를 3-layer로 정리
> 예상 기간: **5~7 영업일** (1인 기준, 전체 ~5,800 LOC)

---

## 0. 현황 진단 (실측)

```bash
$ find src/main/java -path "*/application/port/*" | wc -l
14   # Port 인터페이스
$ find src/main/java -path "*/infra/persistence/*Impl.java" | wc -l
11   # DB-layer Adapter 구현
```

### 현재 Port 분포
| 모듈 | Port 개수 | 보존? |
|---|---|---|
| `common/application/port` | 1 (`RiotApiPort`) | ✅ 유지 |
| `aggregate/application/port` | 5 | ❌ 4개 제거, `ChampionMetaClient` 1개만 `common`으로 이동 |
| `ingest/application/port` | 7 | ❌ 전부 제거 |
| `leagueentry/application/port` | 1 | ❌ 제거 |

### 보존할 Port (단 2개)
1. **`RiotApiPort`** + 2 adapters (`RiotApiHttpAdapter`, `FakeRiotApiAdapter`) — 유지
2. **`ChampionMetaClient`** + 1 adapter — `common`으로 이동, 유지

### 제거 대상 (총 23 파일)
- Port 인터페이스: 12개
- Adapter 구현체: 11개

---

## 1. 목표 구조 (리팩토링 종료 시점)

```
com.bestduo_BE/
├── BestduoBeApplication.java
├── config/                              # 글로벌 설정 (그대로 유지)
│
├── common/                              # 공유 커널 + 외부 어댑터
│   ├── domain/
│   │   ├── model/                       # QueueStatus, Tier, ChampionMeta 등
│   │   └── exception/                   # IllegalStateTransitionException 등
│   ├── application/
│   │   └── port/
│   │       ├── RiotApiPort.java         # ★ 유지
│   │       └── ChampionMetaClient.java  # ★ aggregate에서 이동
│   ├── infra/
│   │   ├── persistence/
│   │   │   ├── entity/                  # MatchQueue, Match 등 공유 엔티티
│   │   │   └── repository/              # JPA Repository (직접 사용)
│   │   ├── riot/
│   │   │   ├── RiotApiHttpAdapter.java
│   │   │   └── FakeRiotApiAdapter.java
│   │   └── champion/
│   │       └── ChampionMetaClientImpl.java
│   └── presentation/
│       └── api/                         # 공통 DTO/응답 envelope
│
├── pipeline/                            # 파이프라인 오케스트레이션
│   ├── application/
│   └── presentation/
│
├── ingest/                              # 매치 수집
│   ├── application/                     # MatchIngestService (Port 호출 → JPA 직접)
│   └── presentation/
│       └── api/
│
├── leagueentry/                         # 리그 엔트리 수집
│   ├── application/
│   └── presentation/
│
├── coverage/                            # 커버리지 통계
│   ├── application/
│   ├── infra/persistence/
│   └── presentation/
│
├── aggregate/                           # 집계
│   ├── application/                     # ★ port/ 폴더 제거됨
│   ├── infra/persistence/               # ★ Impl 클래스 제거, JPA 직접 사용
│   └── presentation/
│
└── monitoring/                          # 모니터링
```

### 핵심 변화
- `*/application/port/` 폴더 → `common/application/port/`만 남김 (2개 파일)
- `*/infra/persistence/*Impl.java` → 전부 삭제, Service에서 JPA Repository 직접 호출
- `*/domain/` 폴더 → `common/domain/`으로 통합

---

## 2. 단계별 계획 (5 Phase)

### Phase 0: 안전망 구축 (0.5일)

**목표**: 리팩토링 중 회귀 방지

- [ ] **0-1.** 현재 `dev` 브랜치에서 `refactor/3layer-transition` 브랜치 생성
- [ ] **0-2.** 현재 통합 테스트가 모두 green인지 확인
  ```bash
  ./gradlew test
  ```
- [ ] **0-3.** 핵심 유스케이스 5개에 대한 **smoke test** 작성 (없다면)
   - Match ingest end-to-end
   - LeagueEntry daily fetch
   - Coverage stat 갱신
   - BottomDuo aggregate 산출
   - Pipeline scheduler 동작
- [ ] **0-4.** 현재 빌드/테스트 결과 baseline 기록
  ```bash
  ./gradlew test --info > /tmp/before.log
  ```

**검증**: 모든 테스트 green, baseline 저장

---

### Phase 1: `ingest` 모듈 정리 (1.5일)

**목표**: 7개 Port + 4개 Adapter 제거

#### 1-1. 영향 범위 분석 (0.25일)
- [ ] 각 Port를 사용하는 Service 식별
  ```bash
  grep -r "MatchQueueDispatcher\|MatchQueuePicker\|MatchSaver\|BottomDuoRawSaver" \
    src/main/java --include="*.java"
  ```
- [ ] 메모: 어떤 Service가 어떤 Port를 주입받는지 표로 정리

#### 1-2. Service에서 Port 의존성 제거 (1일)

각 Service마다:
- [ ] **a.** `private final XxxPort xxxPort` → `private final XxxJpaRepository repository`로 변경
- [ ] **b.** Port 메서드 호출 → JPA Repository 메서드 직접 호출로 변경
- [ ] **c.** 비즈니스 로직(트랜잭션, 검증)은 Service에 그대로 유지
- [ ] **d.** Adapter에 있던 *비즈니스 로직이 있다면* Service로 끌어올림 (대부분 단순 wrapping이라 이동할 게 없을 것)

**예시 변경**:
```java
// BEFORE
public class IngestMatchDetail {
    private final MatchSaver matchSaver;            // Port
    public void execute(...) { matchSaver.save(m); }
}

// AFTER
public class IngestMatchDetail {
    private final MatchJpaRepository matchRepository;  // JPA 직접
    public void execute(...) { matchRepository.save(m); }
}
```

#### 1-3. 파일 삭제
삭제할 7개 Port:
- [ ] `ingest/application/port/MatchQueueDispatcher.java`
- [ ] `ingest/application/port/MatchQueuePicker.java`
- [ ] `ingest/application/port/MatchQueueErrorTop.java`
- [ ] `ingest/application/port/MatchQueueRetryCount.java`
- [ ] `ingest/application/port/MatchQueueStatusCount.java`
- [ ] `ingest/application/port/MatchSaver.java`
- [ ] `ingest/application/port/BottomDuoRawSaver.java`

삭제할 4개 Adapter:
- [ ] `ingest/infra/persistence/MatchQueueDispatcherImpl.java`
- [ ] `ingest/infra/persistence/MatchQueuePickerImpl.java`
- [ ] `ingest/infra/persistence/MatchSaverImpl.java`
- [ ] `ingest/infra/persistence/BottomDuoRawSaverImpl.java`

#### 1-4. 빈 폴더 제거
- [ ] `ingest/application/port/` 폴더 삭제

#### 1-5. 검증
- [ ] `./gradlew compileJava` 통과
- [ ] `./gradlew test` 통과
- [ ] smoke test (Match ingest) 통과
- [ ] **커밋**: `refactor: 3-layer 전환 1단계 — ingest 모듈 Port/Adapter 제거`

---

### Phase 2: `aggregate` 모듈 정리 (1일)

#### 2-1. ChampionMetaClient를 common으로 이동 (0.25일)
- [ ] `aggregate/application/port/ChampionMetaClient.java`
  → `common/application/port/ChampionMetaClient.java`로 이동
- [ ] `package` 선언 + import 일괄 수정
  ```bash
  grep -rl "aggregate.application.port.ChampionMetaClient" src/ \
    | xargs sed -i '' 's|aggregate.application.port.ChampionMetaClient|common.application.port.ChampionMetaClient|g'
  ```
- [ ] `common/infra/champion/ChampionMetaClientImpl.java`의 import 갱신

#### 2-2. 나머지 4개 Port 제거 (0.75일)

각 Port에 대해 Phase 1과 동일한 절차:
- [ ] `BottomDuoMatchupAggregator` → Service에서 JPA 직접 사용으로 변경
- [ ] `BottomDuoMatchupFinder` → 동일
- [ ] `BottomDuoStatAggregator` → 동일
- [ ] `BottomDuoStatFinder` → 동일

삭제할 4개 Port:
- [ ] `aggregate/application/port/BottomDuoMatchupAggregator.java`
- [ ] `aggregate/application/port/BottomDuoMatchupFinder.java`
- [ ] `aggregate/application/port/BottomDuoStatAggregator.java`
- [ ] `aggregate/application/port/BottomDuoStatFinder.java`

삭제할 4개 Adapter:
- [ ] `aggregate/infra/persistence/BottomDuoMatchupAggregatorImpl.java`
- [ ] `aggregate/infra/persistence/BottomDuoMatchupFinderImpl.java`
- [ ] `aggregate/infra/persistence/BottomDuoStatAggregatorImpl.java`
- [ ] `aggregate/infra/persistence/BottomDuoStatFinderImpl.java`

#### 2-3. 빈 폴더 제거
- [ ] `aggregate/application/port/` 폴더 삭제

#### 2-4. 검증 및 커밋
- [ ] 컴파일 + 전체 테스트 통과
- [ ] BottomDuo aggregate smoke test 통과
- [ ] **커밋**: `refactor: 3-layer 전환 2단계 — aggregate 모듈 정리 + ChampionMetaClient common 이동`

---

### Phase 3: `leagueentry` + `common` 모듈 정리 (0.5일)

#### 3-1. leagueentry Port 제거
- [ ] `SummonerSeedRegistry` 사용처 확인 후 JPA 직접 사용으로 변경
- [ ] 삭제: `leagueentry/application/port/SummonerSeedRegistry.java`
- [ ] 삭제: `leagueentry/infra/persistence/SummonerSeedRegistryImpl.java`
- [ ] `leagueentry/application/port/` 폴더 삭제

#### 3-2. common 모듈 정리
- [ ] `MatchPayloadReaderImpl`, `MatchQueueEnqueuerImpl`이 Port 없이 단독 클래스라면 그대로 유지 (실측 후 결정)
- [ ] Port가 있다면 Phase 1과 동일 절차로 제거

#### 3-3. 검증 및 커밋
- [ ] 컴파일 + 전체 테스트 통과
- [ ] **커밋**: `refactor: 3-layer 전환 3단계 — leagueentry/common 모듈 Port 제거`

---

### Phase 4: 폴더 구조 정합성 검증 + 의도적 편차 문서화 (0.5일)

> **Phase 1~3 완료 시점에서 재평가한 결과**, 원안의 4-1/4-2는 실제로 적용할 대상이 없음.
> Phase 4의 본질은 "구조의 일관성"이 아니라 **"의존성 방향의 일관성"** (presentation → application → infra)이며,
> 이 기준은 Phase 1~3에서 이미 달성됨. 따라서 이 단계는 **검증 + 편차 문서화**로 축소.

#### 4-1. ~~누락된 `presentation/` 폴더 생성~~ → **N/A (의도적 편차)**
- [x] **`pipeline/` — `application/`만 존재**
   - 사유: `@Scheduled` 배치 오케스트레이션 모듈로 HTTP 엔드포인트가 없음. `presentation/`이 없는 것이 누락이 아니라 **모듈 성격을 정확히 표현하는 결과**.
   - 빈 placeholder 폴더 생성은 YAGNI 위반 → 만들지 않음.
- [x] **`leagueentry/` — `infra/` 없음, `common/infra` 공유**
   - 사유: 자체 엔티티가 없고 `common/infra/persistence`의 Repository를 직접 사용. 모듈마다 동일 Repository를 복제하는 것은 DRY 위반 → 현 구조 유지.

#### 4-2. ~~빈 `domain/` 폴더 정리~~ → **N/A**
- [x] `common/domain/`은 `exception/model/service` 하위에 실제 코드 존재 (정리 불필요).
- [x] 다른 모듈에는 `domain/` 폴더 자체가 없음 (정리 대상 없음).

#### 4-3. import 정리 (선택)
- [ ] 사용 안 하는 import 제거 (빌드/테스트 통과 시 생략 가능, IDE optimize로 일괄 처리하는 것이 효율적)

#### 4-4. 검증 및 커밋
- [ ] `./gradlew compileJava compileTestJava` 통과
- [ ] `./gradlew test` 전체 green
- [ ] **커밋**: `refactor: 3-layer 전환 4단계 — Phase 4 구조 검증 + 의도적 편차 문서화`

#### 4-5. 모듈 구조 최종 스냅샷
| 모듈 | presentation | application | infra | 비고 |
|---|:---:|:---:|:---:|---|
| `aggregate` | ✓ | ✓ | ✓ | 표준 3-layer |
| `coverage` | ✓ | ✓ | ✓ | 표준 3-layer |
| `ingest` | ✓ | ✓ | ✓ | 표준 3-layer |
| `common` | ✓ | ✓ | ✓ | 공유 커널 + 외부 어댑터 (Port 2개 보존) |
| `leagueentry` | ✓ | ✓ | — | 공유 인프라 사용 (DRY) |
| `pipeline` | — | ✓ | — | 배치 오케스트레이션 (`@Scheduled` 전용) |

---

### Phase 5: 문서화 + ADR 갱신 (0.5일)

#### 5-1. 새 ADR 작성
- [ ] `docs/adr_3layer_transition.md` 신규 작성
   - Phase 5의 "경량 헥사고날" 결정을 어떻게 재평가했는지
   - 무엇을 제거했고 왜 제거했는지
   - Riot API와 ChampionMetaClient만 Port로 남긴 이유
   - 향후 Port 추가 기준 (체크리스트)

#### 5-2. 기존 ADR 업데이트
- [ ] `docs/adr_phase5_lightweight_hexagonal.md` 상단에 "Superseded by 3-Layer Transition" 표기

#### 5-3. README/architecture.md 갱신
- [ ] `docs/architecture.md`에 새 폴더 구조 다이어그램 추가
- [ ] 의존성 방향 화살표 명시

#### 5-4. 최종 PR
- [ ] `dev` 브랜치로 PR 생성
   - 제목: `refactor: 경량 헥사고날에서 3-Layer로 전환`
   - 본문: "왜 전환했는지" + "무엇이 바뀌었는지" + "테스트 결과"

---

## 3. 위험 요소 & 대응

| 위험 | 가능성 | 영향 | 대응 |
|---|---|---|---|
| Adapter에 숨겨진 비즈니스 로직 발견 | 중 | 중 | Service로 끌어올림. PR에 명시 |
| 트랜잭션 경계 깨짐 | 낮음 | 높음 | `@Transactional`을 Service 레벨에서 일관 적용 확인 |
| 테스트가 Port를 mock하고 있음 | 높음 | 중 | JPA Repository를 mock하거나 `@DataJpaTest` 사용으로 전환 |
| 순환 import 발생 | 낮음 | 낮음 | `common`만 다른 모듈을 import 안 하면 됨 |
| 패키지 이동으로 Spring 빈 누락 | 낮음 | 높음 | `@SpringBootApplication`의 base package 확인 |

### 롤백 전략
각 Phase 끝마다 커밋 → 문제 발생 시 `git revert <phase-commit>`로 즉시 복구 가능.

---

## 4. 완료 기준 (Definition of Done)

- [x] `find src -path "*/application/port/*" | wc -l` → **2** (RiotApiPort + ChampionMetaClient)
- [x] `find src -name "*Impl.java" -path "*/infra/persistence/*" | wc -l` → **0**
- [x] ~~모든 모듈이 `presentation/ + application/ + infra/` 구조로 통일됨~~ → **의존성 방향(presentation → application → infra) 일관성**으로 재정의. 모듈 성격에 따른 의도적 편차는 Phase 4-5 표 참조 (`pipeline`, `leagueentry`).
- [ ] 전체 테스트 green
- [ ] smoke test 5개 통과
- [ ] 새 ADR 작성됨
- [ ] PR 생성됨

---

## 5. 향후 Port 추가 기준 (셀프 가이드)

새 외부 의존성을 추가할 때, **아래 4개를 모두 만족**할 때만 Port로 만든다:

1. ☐ 외부 시스템(HTTP API, 메시지 브로커, 파일시스템)인가? — DB는 제외
2. ☐ 테스트할 때 stub/fake가 필요한가?
3. ☐ 향후 다른 구현체로 교체할 가능성이 30% 이상 있는가?
4. ☐ 1줄 이상의 비즈니스 로직이 어댑터에 들어가는가?

**4개 중 하나라도 NO면 → 직접 구현체 사용** (Port 만들지 않음).

JPA Repository, 단순 wrapper, 사내 유틸 → 항상 직접 사용.

---

## 6. 일정 요약

| Phase | 작업 | 소요 |
|---|---|---|
| 0 | 안전망 구축 | 0.5일 |
| 1 | ingest 모듈 정리 (Port 7개 제거) | 1.5일 |
| 2 | aggregate 모듈 정리 + ChampionMetaClient 이동 | 1일 |
| 3 | leagueentry + common 정리 | 0.5일 |
| 4 | 폴더 구조 일관화 | 1일 |
| 5 | 문서화 + ADR | 0.5일 |
| **합계** | | **5일** |

여유 버퍼 포함: **5~7 영업일**

---

## 7. 다음 액션

1. 이 계획을 검토하고 동의/수정
2. `Phase 0` 시작 (`refactor/3layer-transition` 브랜치 생성)
3. 매 Phase 완료마다 커밋 + 사용자 확인
