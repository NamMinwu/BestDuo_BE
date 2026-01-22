package com.bestduo_BE.application.port;

import com.bestduo_BE.domain.model.BottomDuoRaw;
import java.util.List;

public interface BottomDuoRawSaver {
  void saveAllIdempotent(List<BottomDuoRaw> raws);
}
