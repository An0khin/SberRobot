package org.olympiad.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.olympiad.model.Map;
import org.olympiad.model.*;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class PathResolver {
    public static final String BASE = "base";
    public static final String MINE = "mine";

    private static final Logger logger = LogManager.getLogger(PathResolver.class);

    private List<Vertex> allMines = new ArrayList<>();
    private final List<List<Vertex>> needMines = new ArrayList<>();

    private final java.util.Map<Integer, Integer> idToResources = new HashMap<>();

    // метод находит оптимальный путь для сбора необходимого количества ресурсов и доставки их на базу
    public Answer findAnswer(Map map) {

        var edges = map.edge();
        var vertices = map.vertex();
        var robotSize = map.robot().size();
        var leftResources = map.goal().resources();

        var baseVertex = vertices.stream()
                .filter(vertex -> vertex.type().equals(BASE))
                .findFirst()
                .orElseThrow();

        var firstPath = paths(baseVertex.id(), edges, vertices);

        allMines = vertices.stream()
                .filter(vertex -> vertex.type().equals(MINE))
                .sorted(Comparator
                        .comparing(mine -> firstPath.get(((Vertex) mine).id()).length())
                        .thenComparing(o -> ((Vertex) o).resources(), Comparator.reverseOrder()))
                .toList();

        allMines.forEach(mine -> idToResources.put(mine.id(), mine.resources()));

        List<Integer> resultPathWithRepeats = new ArrayList<>();

        var curRes = 0;
        while(leftResources > 0) {
            List<Integer> path = new ArrayList<>();
            int pathLength = Integer.MAX_VALUE;

            List<Integer> optimalPath = new ArrayList<>();

            getRichMines(-1, leftResources, new ArrayList<>());
            for(List<Vertex> mines : needMines) {
                int curLength = 0;
                List<Integer> curPath = new ArrayList<>();
                int startId = baseVertex.id();

                for(Vertex mine : mines) {
                    var pathFromBaseTo = paths(startId, edges, vertices);
                    curLength += pathFromBaseTo.get(mine.id()).length();
                    curPath.addAll(pathFromBaseTo.get(mine.id()).path());

                    startId = mine.id();
                }

                if(curLength <= pathLength) {
                    pathLength = curLength;
                    path = curPath;
                }
            }

            for(int id : path) {
                if(curRes == robotSize || curRes >= leftResources)
                    break;

                if(id == baseVertex.id()) {
                    leftResources -= curRes;
                    curRes = 0;
                }

                if(idToResources.containsKey(id)) {
                    int tempRes = curRes;

                    int mineResources = idToResources.get(id);

                    if(tempRes + mineResources > robotSize) {
                        curRes = robotSize;
                        idToResources.replace(id, (tempRes + mineResources) - robotSize);
                    } else {
                        curRes += mineResources;
                        idToResources.replace(id, 0);
                    }
                }

                optimalPath.add(id);
            }

            resultPathWithRepeats.addAll(optimalPath);
            resultPathWithRepeats.addAll(paths(optimalPath.get(optimalPath.size() - 1), edges, vertices).get(baseVertex.id()).path());

            leftResources -= curRes;
            curRes = 0;
        }

        List<Integer> answerPath = new ArrayList<>();

        var previousId = -1;
        for(int id : resultPathWithRepeats) {
            if(id != previousId)
                answerPath.add(id);
            previousId = id;
        }

        Answer answer = new Answer(answerPath);
        logger.info("Response: {}", answer);
        return answer;
    }

    java.util.Map<Integer, VertexPath> paths(int start, List<Edge> edges, List<Vertex> vertices) {
        Set<Integer> passedPoints = new HashSet<>();
        java.util.Map<Integer, VertexPath> fastestPaths = new HashMap<>();

        fastestPaths.put(
                start,
                new VertexPath(start, 0, 0, List.of(start))
        );

        while(passedPoints.size() != vertices.size()) {
            var fastestVertex = fastestPaths.values().stream()
                    .sorted(Comparator.comparing(VertexPath::length))
                    .filter(Predicate.not(vertex -> passedPoints.contains(vertex.stop())))
                    .findFirst()
                    .orElseThrow();
            var fastestVertexId = fastestVertex.stop();

            var closePoints = getClosePoints(fastestVertexId, edges).stream()
                    .filter(Predicate.not(passedPoints::contains))
                    .collect(Collectors.toSet());

            for(var pointId : closePoints) {
                var curEdge = edges.stream()
                        .filter(edge -> edge.start() == fastestVertexId && edge.stop() == pointId
                                || edge.stop() == fastestVertexId && edge.start() == pointId)
                        .findFirst()
                        .orElseThrow();

                var curVertex = vertices.stream()
                        .filter(vertex -> vertex.id() == pointId)
                        .findFirst()
                        .orElseThrow();

                var path = new LinkedList<>(fastestVertex.path());
                path.addLast(pointId);

                var curVertexPath = new VertexPath(
                        pointId,
                        fastestVertex.length() + curEdge.size(),
                        fastestVertex.resources() + (idToResources.getOrDefault(curVertex.id(), 0)),
                        path
                );

                if(fastestPaths.containsKey(pointId) && fastestPaths.get(pointId).length() > curVertexPath.length()) {
                    fastestPaths.replace(
                            pointId,
                            curVertexPath
                    );
                }

                if(!fastestPaths.containsKey(pointId)) {
                    fastestPaths.put(
                            pointId,
                            curVertexPath
                    );
                }
            }
            passedPoints.add(fastestVertexId);
        }

        return fastestPaths;
    }

    private void getRichMines(int counter, int goal, List<Vertex> curMines) {
        if(curMines.stream().map(vertex -> idToResources.get(vertex.id())).reduce(Integer::sum).orElse(0) >= goal) {
            needMines.add(curMines);
        }
        if(counter != allMines.size()) {
            for(int i = counter + 1; i < allMines.size(); i++) {
                curMines.add(allMines.get(i));
                getRichMines(i, goal, new ArrayList<>(curMines));
                curMines.remove(curMines.size() - 1);
            }
        }
    }

    private Set<Integer> getClosePoints(int from, List<Edge> edges) {
        Set<Integer> closestPoints = new HashSet<>();

        for(var edge : edges) {
            if(edge.start() == from) {
                closestPoints.add(edge.stop());
            } else if(edge.stop() == from) {
                closestPoints.add(edge.start());
            }
        }

        return closestPoints;
    }
}
