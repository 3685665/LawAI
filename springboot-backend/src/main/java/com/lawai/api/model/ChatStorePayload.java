package com.lawai.api.model;

import java.util.List;

public record ChatStorePayload(List<ChatSessionRecord> sessions) {
}
