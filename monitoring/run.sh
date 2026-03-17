#!/bin/bash
# Documents 폴더 마운트 문제 회피: /tmp로 복사 후 실행 (Docker는 /tmp 항상 접근 가능)

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TMP_DIR="/tmp/bestduo-monitoring"

mkdir -p "$TMP_DIR"/prometheus "$TMP_DIR"/grafana/provisioning/datasources "$TMP_DIR"/grafana/provisioning/dashboards/json

cp "$SCRIPT_DIR"/prometheus/prometheus.yml "$TMP_DIR/prometheus/"
cp "$SCRIPT_DIR"/grafana/provisioning/datasources/datasource.yml "$TMP_DIR/grafana/provisioning/datasources/"
cp "$SCRIPT_DIR"/grafana/provisioning/dashboards/dashboard.yml "$TMP_DIR/grafana/provisioning/dashboards/"
cp "$SCRIPT_DIR"/grafana/provisioning/dashboards/json/jvm-memory.json "$TMP_DIR/grafana/provisioning/dashboards/json/"

# volume 경로를 TMP_DIR로 변경한 compose 파일 생성
sed "s|\./prometheus|$TMP_DIR/prometheus|g; s|\./grafana|$TMP_DIR/grafana|g" \
  "$SCRIPT_DIR/docker-compose.yml" > "$TMP_DIR/docker-compose.yml"

cd "$TMP_DIR"
docker compose up -d "$@"

echo ""
echo "Prometheus: http://localhost:9090"
echo "Grafana:    http://localhost:3000 (admin/admin)"
echo ""
echo "종료: cd $TMP_DIR && docker compose down"
