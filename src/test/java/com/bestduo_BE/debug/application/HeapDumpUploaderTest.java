package com.bestduo_BE.debug.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bestduo_BE.config.ArchiveProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@ExtendWith(MockitoExtension.class)
class HeapDumpUploaderTest {

  private static final String BUCKET = "test-bucket";

  @Mock
  private S3Client s3Client;

  @TempDir
  Path dumpDir;

  private HeapDumpUploader uploader;

  @BeforeEach
  void setUp() {
    ArchiveProperties props = new ArchiveProperties();
    props.getR2().setBucket(BUCKET);
    uploader = new HeapDumpUploader(dumpDir.toString(), s3Client, props);
  }

  @Test
  @DisplayName("uploadAllHprofFiles — 디렉토리에 .hprof 파일이 없으면 빈 리스트 반환하고 S3 호출 안 함")
  void emptyDirReturnsEmptyList() throws IOException {
    List<HeapDumpUploader.UploadResult> results = uploader.uploadAllHprofFiles();

    assertThat(results).isEmpty();
    verifyNoInteractions(s3Client);
  }

  @Test
  @DisplayName("uploadAllHprofFiles — .hprof 1개면 R2 PUT 1회 호출되고 key=debug/<filename>")
  void singleHprofIsUploaded() throws IOException {
    Path hprof = dumpDir.resolve("java_pid1.hprof");
    Files.write(hprof, new byte[]{0x01, 0x02, 0x03});
    when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenReturn(PutObjectResponse.builder().build());

    List<HeapDumpUploader.UploadResult> results = uploader.uploadAllHprofFiles();

    ArgumentCaptor<PutObjectRequest> reqCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(s3Client).putObject(reqCaptor.capture(), any(RequestBody.class));
    assertThat(reqCaptor.getValue().bucket()).isEqualTo(BUCKET);
    assertThat(reqCaptor.getValue().key()).isEqualTo("debug/java_pid1.hprof");

    assertThat(results).hasSize(1);
    assertThat(results.get(0).r2Key()).isEqualTo("debug/java_pid1.hprof");
    assertThat(results.get(0).bytes()).isEqualTo(3L);
    assertThat(results.get(0).localPath()).isEqualTo(hprof.toString());
  }

  @Test
  @DisplayName("uploadAllHprofFiles — .hprof 여러 개면 파일명 정렬 순서로 모두 업로드")
  void multipleHprofsUploadedInSortedOrder() throws IOException {
    Files.write(dumpDir.resolve("java_pid2.hprof"), new byte[]{0x10});
    Files.write(dumpDir.resolve("java_pid1.hprof"), new byte[]{0x20, 0x21});
    Files.write(dumpDir.resolve("java_pid3.hprof"), new byte[]{0x30, 0x31, 0x32});
    when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenReturn(PutObjectResponse.builder().build());

    List<HeapDumpUploader.UploadResult> results = uploader.uploadAllHprofFiles();

    verify(s3Client, times(3)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    assertThat(results).extracting(HeapDumpUploader.UploadResult::r2Key)
        .containsExactly(
            "debug/java_pid1.hprof",
            "debug/java_pid2.hprof",
            "debug/java_pid3.hprof");
    assertThat(results).extracting(HeapDumpUploader.UploadResult::bytes)
        .containsExactly(2L, 1L, 3L);
  }

  @Test
  @DisplayName("uploadAllHprofFiles — .hprof 아닌 파일은 무시")
  void nonHprofFilesAreIgnored() throws IOException {
    Files.write(dumpDir.resolve("java_pid1.hprof"), new byte[]{0x01});
    Files.write(dumpDir.resolve("readme.txt"), new byte[]{0x02});
    Files.write(dumpDir.resolve("app.log"), new byte[]{0x03});
    when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenReturn(PutObjectResponse.builder().build());

    List<HeapDumpUploader.UploadResult> results = uploader.uploadAllHprofFiles();

    verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    assertThat(results).hasSize(1);
    assertThat(results.get(0).r2Key()).isEqualTo("debug/java_pid1.hprof");
  }

  @Test
  @DisplayName("uploadAllHprofFiles — dump 디렉토리 자체가 없으면 빈 리스트 반환하고 S3 호출 안 함")
  void missingDumpDirReturnsEmptyList() throws IOException {
    HeapDumpUploader withMissingDir = new HeapDumpUploader(
        dumpDir.resolve("nonexistent").toString(),
        s3Client,
        uploaderProps());

    List<HeapDumpUploader.UploadResult> results = withMissingDir.uploadAllHprofFiles();

    assertThat(results).isEmpty();
    verifyNoInteractions(s3Client);
  }

  private ArchiveProperties uploaderProps() {
    ArchiveProperties props = new ArchiveProperties();
    props.getR2().setBucket(BUCKET);
    return props;
  }
}
