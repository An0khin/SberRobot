package org.olympiad.model;

import java.util.List;

public record Map(List<Vertex> vertex, List<Edge> edge, Robot robot, Goal goal) {
}
