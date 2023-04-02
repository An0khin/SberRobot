package org.olympiad.model.answer;

import java.util.List;

public record AnswerStep(
        int vertexId,
        List<AnswerAction> actions
) {
}
