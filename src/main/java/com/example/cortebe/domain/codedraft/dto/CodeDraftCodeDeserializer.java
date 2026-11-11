package com.example.cortebe.domain.codedraft.dto;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public class CodeDraftCodeDeserializer extends ValueDeserializer<String> {

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            return context.reportInputMismatch(String.class, "코드는 JSON 문자열이어야 합니다.");
        }
        return parser.getString();
    }
}
