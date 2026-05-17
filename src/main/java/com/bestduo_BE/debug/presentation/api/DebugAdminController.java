package com.bestduo_BE.debug.presentation.api;

import com.bestduo_BE.debug.application.HeapDumpUploader;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 일회성 디버깅 도구 endpoint.
 *
 * <p>현재는 heap dump 업로드만 제공. 분석 끝나면 통째로 제거 예정.
 *
 * <p>인증: {@code AdminApiKeyInterceptor} 가 {@code X-Admin-Key} 헤더를 검증한다.
 */
@RestController
@RequestMapping("/admin/debug")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "archive.r2.enabled", havingValue = "true")
@Slf4j
public class DebugAdminController {

  private final HeapDumpUploader heapDumpUploader;

  /**
   * Railway volume 의 {@code /dumps/*.hprof} 파일을 모두 R2 로 업로드한다.
   *
   * <p>JVM 의 {@code HeapDumpOnOutOfMemoryError} 가 만든 hprof 파일을 외부에서 받아볼 수 있게
   * 하기 위함. 업로드 후 파일은 그대로 둠 — 같은 hprof 가 R2 에 덮어쓰여도 무해 (idempotent).
   */
  @PostMapping("/upload-heap-dump")
  public List<HeapDumpUploader.UploadResult> uploadHeapDump() throws IOException {
    List<HeapDumpUploader.UploadResult> results = heapDumpUploader.uploadAllHprofFiles();
    log.info("[DebugAdmin/upload-heap-dump] uploaded {} hprof file(s)", results.size());
    return results;
  }
}
