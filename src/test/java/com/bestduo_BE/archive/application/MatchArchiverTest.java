package com.bestduo_BE.archive.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.persistence.projection.MatchPayloadProjection;
import com.bestduo_BE.common.infra.persistence.repository.MatchJpaRepository;
import com.bestduo_BE.config.ArchiveProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@ExtendWith(MockitoExtension.class)
class MatchArchiverTest {

  private static final String PATCH = "16.5";
  private static final Tier TIER = Tier.MASTER;
  private static final String BUCKET = "test-bucket";
  private static final String EXPECTED_KEY = "match-archive/patch=16.5/tier=MASTER.jsonl.gz";

  @Mock
  private MatchJpaRepository matchRepository;

  @Mock
  private S3Client s3Client;

  private MatchArchiver archiver;

  @BeforeEach
  void setUp() {
    ArchiveProperties props = new ArchiveProperties();
    props.getR2().setBucket(BUCKET);
    archiver = new MatchArchiver(matchRepository, s3Client, props);
  }

  @Test
  @DisplayName("execute — 매치가 0건이면 archivedCount=0 이고 S3 호출은 일어나지 않는다")
  void emptyPagesProduceZeroArchived() {
    when(matchRepository.findPayloadPageByTierAndPatch(eq(TIER.name()), eq(PATCH), any(), eq(500)))
        .thenReturn(List.of());

    MatchArchiver.Result result = archiver.execute(PATCH, TIER);

    assertThat(result.archivedCount()).isZero();
    assertThat(result.bytesUploaded()).isZero();
    verifyNoInteractions(s3Client);
  }

  @Test
  @DisplayName("execute — 매치 1건이면 putObject 가 정확한 bucket/key 로 1회 호출되고 bytesUploaded > 0")
  void singleMatchProducesOnePutObject() {
    MatchPayloadProjection m = projection("KR_1", "{\"info\":{\"gameVersion\":\"16.5.1\"}}");
    when(matchRepository.findPayloadPageByTierAndPatch(eq(TIER.name()), eq(PATCH), eq(""), eq(500)))
        .thenReturn(List.of(m));
    when(matchRepository.findPayloadPageByTierAndPatch(eq(TIER.name()), eq(PATCH), eq("KR_1"), eq(500)))
        .thenReturn(List.of());
    when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenReturn(PutObjectResponse.builder().build());

    MatchArchiver.Result result = archiver.execute(PATCH, TIER);

    ArgumentCaptor<PutObjectRequest> reqCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(s3Client).putObject(reqCaptor.capture(), any(RequestBody.class));
    assertThat(reqCaptor.getValue().bucket()).isEqualTo(BUCKET);
    assertThat(reqCaptor.getValue().key()).isEqualTo(EXPECTED_KEY);
    assertThat(result.archivedCount()).isEqualTo(1);
    assertThat(result.bytesUploaded()).isGreaterThan(0);
    assertThat(result.objectKey()).isEqualTo(EXPECTED_KEY);
  }

  @Test
  @DisplayName("execute — 페이지가 여러 번 나뉘어도 모든 row 가 한 객체로 합쳐져 putObject 1회 호출")
  void paginationAcrossMultiplePagesAggregatesIntoSinglePut() {
    MatchPayloadProjection m1 = projection("KR_1", "{\"info\":{\"gameVersion\":\"16.5.1\"}}");
    MatchPayloadProjection m2 = projection("KR_2", "{\"info\":{\"gameVersion\":\"16.5.1\"}}");
    MatchPayloadProjection m3 = projection("KR_3", "{\"info\":{\"gameVersion\":\"16.5.2\"}}");
    when(matchRepository.findPayloadPageByTierAndPatch(eq(TIER.name()), eq(PATCH), eq(""), eq(500)))
        .thenReturn(List.of(m1, m2));
    when(matchRepository.findPayloadPageByTierAndPatch(eq(TIER.name()), eq(PATCH), eq("KR_2"), eq(500)))
        .thenReturn(List.of(m3));
    when(matchRepository.findPayloadPageByTierAndPatch(eq(TIER.name()), eq(PATCH), eq("KR_3"), eq(500)))
        .thenReturn(List.of());
    when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenReturn(PutObjectResponse.builder().build());

    MatchArchiver.Result result = archiver.execute(PATCH, TIER);

    verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    assertThat(result.archivedCount()).isEqualTo(3);
  }

  @Test
  @DisplayName("execute — 업로드된 gzip body 를 풀면 jsonl 한 줄씩 원본 payload 와 일치한다")
  void uploadedBytesGunzipMatchPayloadsAsJsonl() throws IOException {
    String p1 = "{\"info\":{\"gameVersion\":\"16.5.1\",\"gameId\":1}}";
    String p2 = "{\"info\":{\"gameVersion\":\"16.5.1\",\"gameId\":2}}";
    MatchPayloadProjection m1 = projection("KR_1", p1);
    MatchPayloadProjection m2 = projection("KR_2", p2);
    when(matchRepository.findPayloadPageByTierAndPatch(eq(TIER.name()), eq(PATCH), eq(""), eq(500)))
        .thenReturn(List.of(m1, m2));
    when(matchRepository.findPayloadPageByTierAndPatch(eq(TIER.name()), eq(PATCH), eq("KR_2"), eq(500)))
        .thenReturn(List.of());

    // temp file 은 putObject 후 finally 에서 삭제되므로 호출 시점에 바이트를 캡처해 둔다.
    byte[][] capturedBody = new byte[1][];
    when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenAnswer(invocation -> {
          RequestBody body = invocation.getArgument(1);
          capturedBody[0] = body.contentStreamProvider().newStream().readAllBytes();
          return PutObjectResponse.builder().build();
        });

    archiver.execute(PATCH, TIER);

    String decoded = gunzip(capturedBody[0]);
    String[] lines = decoded.split("\n");
    assertThat(lines).containsExactly(p1, p2);
  }

  private static MatchPayloadProjection projection(String matchId, String payloadJson) {
    return new MatchPayloadProjection() {
      @Override
      public String getMatchId() {
        return matchId;
      }

      @Override
      public String getPayloadJson() {
        return payloadJson;
      }
    };
  }

  private static String gunzip(byte[] gzipped) throws IOException {
    try (InputStream in = new GZIPInputStream(new java.io.ByteArrayInputStream(gzipped))) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
