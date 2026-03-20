package com.bestduo_BE.ingest.application.port;

import com.bestduo_BE.common.domain.model.BottomDuoRaw;
import java.util.List;

public interface BottomDuoRawSaver {
  void saveAllIdempotent(List<BottomDuoRaw> raws);
}
