package org.olympiad.model.map;

import java.util.List;

public record GameMap(
        List<Base> bases,
        Robot robot,
        List<Vertex> vertexes,
        List<Edge> edges
) {
}
