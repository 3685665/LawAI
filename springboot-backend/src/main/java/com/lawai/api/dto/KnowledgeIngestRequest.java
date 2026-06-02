package com.lawai.api.dto;

import java.util.List;

public record KnowledgeIngestRequest(List<KnowledgeDocumentRequest> documents) {
}
