package com.domesticservice.ai.service;

import java.util.List;
import java.util.Map;

public interface AiProvider {
    record ToolReceipt(String name, String status, int httpStatus, long elapsedMillis) {}
    record Reply(String text, List<ToolReceipt> tools, Map<String, Object> draftChanges) {
        public Reply(String text, List<ToolReceipt> tools) {
            this(text, tools, Map.of());
        }
    }

    Reply reply(List<Map<String, Object>> context, String token);
}
