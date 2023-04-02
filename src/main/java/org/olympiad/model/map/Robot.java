package org.olympiad.model.map;

import java.util.Map;

public record Robot(String baseStartName, int maxSize, Map<String, Integer> resources) {
}
