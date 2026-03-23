package com.bestduo_BE.common.domain.model;

public record IngestResult(
    int rawCreated,
    Long matchStartTimeSec
) {

}
