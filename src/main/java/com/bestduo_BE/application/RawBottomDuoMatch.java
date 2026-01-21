package com.bestduo_BE.application;

import com.bestduo_BE.domain.model.BottomDuoMatch;
import java.util.List;
import lombok.Data;

@Data
public class RawBottomDuoMatch {
  private List<String> puuids;
  private List<BottomDuoMatch> matches;
}
