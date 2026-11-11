package com.example.cortebe.domain.problem.dto;

import com.example.cortebe.domain.problem.entity.Difficulty;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public class DifficultyDeserializer extends ValueDeserializer<Difficulty> {

    @Override
    public Difficulty deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.hasToken(JsonToken.VALUE_STRING)) {
            try {
                return Difficulty.valueOf(parser.getString());
            } catch (IllegalArgumentException ignored) {
                // 빈 문자열·숫자 문자열도 유효한 등급 이름이 아니다.
            }
        }
        return context.reportInputMismatch(Difficulty.class, "유효한 난이도 이름을 문자열로 입력해야 합니다.");
    }
}
