package com.bestduo_BE.infra.persistence;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.times;
import static org.mockito.BDDMockito.willThrow;

import com.bestduo_BE.domain.model.BottomDuoRaw;
import com.bestduo_BE.domain.model.Tier;
import com.bestduo_BE.infra.persistence.entity.BottomDuoRawEntity;
import com.bestduo_BE.infra.persistence.repository.BottomDuoRawJpaRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class BottomDuoRawSaverImplTest {

  @Mock
  private BottomDuoRawJpaRepository repository;

  private BottomDuoRawSaverImpl saver;

  @BeforeEach
  void setUp() {
    saver = new BottomDuoRawSaverImpl(repository);
  }

  @Test
  void saveAllIdempotent_convertsAndPersistsEachRaw() {
    List<BottomDuoRaw> raws = List.of(
        new BottomDuoRaw("KR_1", 100, 222, 412, true, "15.23", Tier.GOLD),
        new BottomDuoRaw("KR_1", 200, 51, 201, false, "15.23", Tier.GOLD)
    );

    saver.saveAllIdempotent(raws);

    ArgumentCaptor<BottomDuoRawEntity> captor = ArgumentCaptor.forClass(BottomDuoRawEntity.class);
    then(repository).should(times(2)).save(captor.capture());
    assertEquals(List.of(100, 200), captor.getAllValues().stream().map(BottomDuoRawEntity::getTeamId).toList());
  }

  @Test
  void saveAllIdempotent_returnsEarlyWhenNullOrEmpty() {
    saver.saveAllIdempotent(null);
    saver.saveAllIdempotent(List.of());
    then(repository).shouldHaveNoInteractions();
  }

  @Test
  void saveAllIdempotent_ignoresDataIntegrityViolations() {
    willThrow(new DataIntegrityViolationException("duplicate"))
        .given(repository).save(any(BottomDuoRawEntity.class));

    assertDoesNotThrow(() -> saver.saveAllIdempotent(List.of(
        new BottomDuoRaw("KR_2", 100, 222, 412, true, "15.1", Tier.SILVER),
        new BottomDuoRaw("KR_2", 200, 145, 53, false, "15.1", Tier.SILVER)
    )));
    then(repository).should(times(2)).save(any(BottomDuoRawEntity.class));
  }
}
