package com.bestduo_BE.workitem.application;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Virtual Thread vs Platform Thread 비교 벤치마크
 *
 * <p>이 테스트는 CI에서 제외(@Tag("benchmark"))되도록 설계되었다.
 * 직접 실행: ./gradlew test --tests "*.WorkerPoolThreadModelBenchmark"
 *
 * <p>검증 목적:
 * WorkerPool에서 Virtual Thread를 쓰는 것이 Riot key 기반 환경에서
 * 실제 얼마나 효과가 있는지 확인한다.
 *
 * <p>핵심 가설:
 * - Throughput(처리량): key 수가 진짜 병목 → 두 방식 동일
 * - OS 스레드 점유: worker >> key 일 때 virtual thread가 carrier thread를 반환 → OS 스레드 수 차이 발생
 * - I/O 대기 시간이 길수록(= Riot API 응답 시간) virtual thread 이점 커짐
 *
 * <p>시뮬레이션 매핑:
 * <pre>
 *   leaseForWorker()    → Semaphore.acquire()    (key 점유, blocking)
 *   pickAndLock()       → AtomicInteger.decrementAndGet() (DB SELECT FOR UPDATE SKIP LOCKED)
 *   execute()           → Thread.sleep(IO_DELAY) (Riot API HTTP 호출)
 *   clearWorkerLease()  → Semaphore.release()    (key 반환)
 * </pre>
 */
@Tag("benchmark")
class WorkerPoolThreadModelBenchmark {

  // ── 시뮬레이션 파라미터 ──────────────────────────────────────────────
  private static final int WORK_ITEMS = 40;        // 처리할 WorkItem 총 수
  private static final long IO_DELAY_MS = 200;     // Riot API 호출 시뮬레이션 (ms)
  private static final int WARMUP_ITEMS = 5;       // JVM 워밍업용 (결과 제외)
  // ────────────────────────────────────────────────────────────────────

  @Test
  void compare_virtualThread_vs_platformThread() throws InterruptedException {
    System.out.println();
    System.out.println("╔══════════════════════════════════════════════════════════════╗");
    System.out.println("║    WorkerPool: Virtual Thread vs Platform Thread Benchmark    ║");
    System.out.println("╠══════════════════════════════════════════════════════════════╣");
    System.out.printf("║  WorkItems=%-4d  IO_Delay=%-6dms                            ║%n",
        WORK_ITEMS, IO_DELAY_MS);
    System.out.println("╚══════════════════════════════════════════════════════════════╝");
    System.out.println();

    List<ScenarioResult> results = new ArrayList<>();

    // ── 시나리오 1: key=1, worker=4  (완전 직렬화)
    results.add(runScenario("key=1  worker=4  (완전 직렬화)", 1, 4));
    // ── 시나리오 2: key=2, worker=8  (현실적 운영 환경)
    results.add(runScenario("key=2  worker=8  (운영 환경 근사)", 2, 8));
    // ── 시나리오 3: key=2, worker=32 (worker >> key, blocking 극대화)
    results.add(runScenario("key=2  worker=32 (worker >> key 극단)", 2, 32));
    // ── 시나리오 4: key=4, worker=32 (key 확보 시 가속 여부 확인)
    results.add(runScenario("key=4  worker=32 (key 확보 시 가속)", 4, 32));

    printSummary(results);
  }

  // ──────────────────────────────────────────────────────────────────────────────

  private ScenarioResult runScenario(String label, int numKeys, int numWorkers)
      throws InterruptedException {
    System.out.printf("▶ 시나리오: %s%n", label);

    // JVM 워밍업 (결과 집계 제외)
    runOnce("WARMUP (Virtual) ", numKeys, numWorkers, WARMUP_ITEMS, true);
    runOnce("WARMUP (Platform)", numKeys, numWorkers, WARMUP_ITEMS, false);

    RunResult virtual  = runOnce("Virtual Thread  ", numKeys, numWorkers, WORK_ITEMS, true);
    RunResult platform = runOnce("Platform Thread ", numKeys, numWorkers, WORK_ITEMS, false);

    System.out.printf("  %-18s elapsed=%5dms  throughput=%.1f items/s  peakOsThreads=%d%n",
        "Virtual Thread:", virtual.elapsedMs, virtual.throughput, virtual.peakOsThreads);
    System.out.printf("  %-18s elapsed=%5dms  throughput=%.1f items/s  peakOsThreads=%d%n",
        "Platform Thread:", platform.elapsedMs, platform.throughput, platform.peakOsThreads);

    double throughputDiff = Math.abs(virtual.throughput - platform.throughput) / platform.throughput * 100;
    int threadDiff = platform.peakOsThreads - virtual.peakOsThreads;
    System.out.printf("  → 처리량 차이: %.1f%%  /  OS 스레드 절감: %+d개%n%n",
        throughputDiff, threadDiff);

    return new ScenarioResult(label, numKeys, numWorkers, virtual, platform);
  }

  private RunResult runOnce(String tag, int numKeys, int numWorkers, int totalItems, boolean virtualThread)
      throws InterruptedException {

    Semaphore keyPool = new Semaphore(numKeys, true); // Riot key pool 시뮬레이션
    AtomicInteger workQueue = new AtomicInteger(totalItems);
    CountDownLatch done = new CountDownLatch(totalItems);
    AtomicInteger peakThreads = new AtomicInteger(0);
    ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

    // runLoop() 시뮬레이션
    Runnable workerLoop = () -> {
      while (!Thread.currentThread().isInterrupted()) {
        try {
          // leaseForWorker() 시뮬레이션 — key 없으면 여기서 blocking
          if (!keyPool.tryAcquire(5, TimeUnit.SECONDS)) {
            return; // 타임아웃 (테스트 종료)
          }

          try {
            // pickAndLock() 시뮬레이션 — 원자적으로 작업 픽업
            int remaining = workQueue.decrementAndGet();
            if (remaining < 0) {
              workQueue.incrementAndGet();
              return; // 큐 소진 → 이 worker는 종료
            }

            // execute() 시뮬레이션 — Riot API I/O blocking
            Thread.sleep(IO_DELAY_MS);
            done.countDown();

            // OS 스레드 peak 기록
            int current = threadMXBean.getThreadCount();
            peakThreads.updateAndGet(prev -> Math.max(prev, current));

          } finally {
            keyPool.release(); // clearWorkerLease() 시뮬레이션
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    };

    long start = System.currentTimeMillis();

    if (virtualThread) {
      // WorkerPool 현재 방식: newVirtualThreadPerTaskExecutor
      try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
        for (int i = 0; i < numWorkers; i++) {
          executor.submit(workerLoop);
        }
        done.await(30, TimeUnit.SECONDS);
      }
    } else {
      // 비교 대상: FixedThreadPool (OS 스레드 고정)
      try (var executor = Executors.newFixedThreadPool(numWorkers)) {
        for (int i = 0; i < numWorkers; i++) {
          executor.submit(workerLoop);
        }
        done.await(30, TimeUnit.SECONDS);
        executor.shutdownNow();
      }
    }

    long elapsed = System.currentTimeMillis() - start;
    double throughput = totalItems * 1000.0 / elapsed;

    return new RunResult(elapsed, throughput, peakThreads.get());
  }

  // ──────────────────────────────────────────────────────────────────────────────

  private void printSummary(List<ScenarioResult> results) {
    System.out.println("╔══════════════════════════════════════════════════════════════════════════════╗");
    System.out.println("║                              결론 요약                                        ║");
    System.out.println("╠══════════════════════════════════════════════════════════════════════════════╣");

    for (ScenarioResult r : results) {
      double tDiff = Math.abs(r.virtual.throughput - r.platform.throughput)
          / r.platform.throughput * 100;
      int threadDiff = r.platform.peakOsThreads - r.virtual.peakOsThreads;

      String throughputVerdict = tDiff < 5.0 ? "처리량 동일 (key가 병목)" : "처리량 차이 있음";
      String threadVerdict = threadDiff > 5
          ? String.format("OS 스레드 %d개 절감 (virtual 유리)", threadDiff)
          : "OS 스레드 차이 미미";

      System.out.printf("║ %-32s → %s / %s%n",
          r.label.substring(0, Math.min(32, r.label.length())),
          throughputVerdict, threadVerdict);
    }

    System.out.println("╠══════════════════════════════════════════════════════════════════════════════╣");
    System.out.println("║ 이론적 최대 처리량 = (numKeys × 1000ms) / IO_DELAY_MS  items/s              ║");
    System.out.printf( "║ key=1 → %.0f/s  /  key=2 → %.0f/s  /  key=4 → %.0f/s                   ║%n",
        1000.0 / IO_DELAY_MS, 2000.0 / IO_DELAY_MS, 4000.0 / IO_DELAY_MS);
    System.out.println("╚══════════════════════════════════════════════════════════════════════════════╝");
  }

  // ──────────────────────────────────────────────────────────────────────────────

  private record RunResult(long elapsedMs, double throughput, int peakOsThreads) {}

  private record ScenarioResult(
      String label, int numKeys, int numWorkers,
      RunResult virtual, RunResult platform) {}
}
