package org.olympiad.model;

import java.util.List;

public record VertexPath(int stop, int length, int resources, List<Integer> path) {
}