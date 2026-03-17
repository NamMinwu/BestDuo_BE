# 모니터링 (Prometheus + Grafana)

로컬에서 JVM 메모리 등 메트릭을 실시간으로 확인할 수 있습니다.

## 사용 방법

### 1. Spring Boot 앱 실행

```bash
./gradlew bootRun
```

앱이 `localhost:8080`에서 떠 있어야 합니다.

### 2. Prometheus + Grafana 실행

(설정은 compose 파일에 인라인되어 있어 File Sharing 제한과 무관하게 동작합니다.)

```bash
cd monitoring
docker compose up -d
```

### 3. 접속

- **Grafana**: http://localhost:3000
  - ID: `admin`
  - PW: `admin` (최초 로그인 시 변경 권장)

- **Prometheus**: http://localhost:9090 (직접 쿼리용)

### 4. 대시보드 확인

Grafana 로그인 후 왼쪽 메뉴 **Dashboards** → **Bestduo JVM Memory** 선택.

- Heap / Non-Heap 메모리 사용량 시계열
- 전체 JVM 메모리 사용량
- 5초마다 자동 갱신

## 종료

```bash
cd monitoring
docker compose down
```

## 참고

- `host.docker.internal`은 Mac/Windows Docker에서 동작합니다.
- Linux에서는 `host.docker.internal`이 없을 수 있어, `prometheus.yml`의 target을 `host.docker.internal` → `172.17.0.1` 또는 호스트 IP로 변경해야 할 수 있습니다.
