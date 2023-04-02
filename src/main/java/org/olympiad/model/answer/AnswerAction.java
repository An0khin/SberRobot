package org.olympiad.model.answer;

public record AnswerAction(
        String action,
        String resource,
        int size
) {
}
