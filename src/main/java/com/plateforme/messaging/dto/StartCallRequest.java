package com.plateforme.messaging.dto;

import com.plateforme.messaging.entity.CallType;

public record StartCallRequest(
        CallType callType
) {
}
