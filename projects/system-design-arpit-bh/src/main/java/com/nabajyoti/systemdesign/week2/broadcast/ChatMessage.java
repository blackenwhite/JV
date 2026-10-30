package com.nabajyoti.systemdesign.week2.broadcast;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public record ChatMessage(String serverId, String user, String text) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize ChatMessage", e);
        }
    }

    public static ChatMessage fromJson(String json) {
        try {
            return MAPPER.readValue(json, ChatMessage.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to deserialize ChatMessage", e);
        }
    }
}
