# BestDuo_BE

## Daily Session Performance Checklist

Follow this playbook when `RunDailySession` behaves differently between the local DB and the Railway DB.

1. **함수별 시간 비교**
   - Run the session (e.g. via `SessionRunner`) once against the local DB and once against Railway.
   - Every run now emits a single `RunDailySession timings ...` log with `seedMs`, `refreshMs`, `consumeMs` and the overall `totalMs`.
   - Compare those numbers across the two environments to name the "범인 함수" (SeedBootstrapRun, RefreshBatchRun, or MatchDetailQueueWorker).

2. **범인 함수 안의 SQL 개수**
   - The same log line now exposes `seedSqlTotal`, `refreshSqlTotal`, and `consumeSqlTotal` (plus the SELECT/INSERT/UPDATE/DELETE breakdown).
   - If the culprit phase already issues only a few SQL statements, focus on query/DB/infra; if it fires hundreds, review the code path for unnecessary loops.

3. **느린 SQL의 DB 내부 실행시간 확인**
   - Turn on query logging by setting `logging.level.com.bestduo_BE.SQL=DEBUG` (application YAML or env var) and rerun the session.
   - Every executed SQL now prints as `SQL timeMs=123 success=true batch=false query=... params=[...]`, so you can immediately see which statements cost 100ms+.
   - Copy the slow SQL from the log (it includes bind parameters and execution time) and run `EXPLAIN ANALYZE` directly on Railway.
   - Decide whether the DB engine is actually slow or if most of the time is spent in network hops / repeated round-trips.

4. **Railway 메트릭 점검**
   - While step 3 runs, open the Railway metrics dashboard: CPU, memory, network, and disk IO should all stay within limits.
   - If any resource plateaus, record the timestamp together with the corresponding log line to justify a spec upgrade.

5. **위치 문제 분리**
   - After finishing steps 1–4, optionally rerun the same command from a different region (or tunnel through a VM in Railway's region).
   - If timings stay bad everywhere, it is DB/query bound. If the slowdown only happens far from the DB region, prioritize relocating the worker or the DB.

The combination of phase-level timings, SQL counts, and detailed SQL logs should drastically shorten the feedback loop when comparing Local vs Railway behaviour.
