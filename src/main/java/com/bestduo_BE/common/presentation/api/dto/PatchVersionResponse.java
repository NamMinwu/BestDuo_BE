package com.bestduo_BE.common.presentation.api.dto;

import java.time.OffsetDateTime;

public record PatchVersionResponse(String patch, OffsetDateTime releasedAt) {
}
