package org.olympiad.model.map;

import java.util.List;

public record Base(
        int vertexId,
        String name,
        List<BaseResource> resources
) {
}
