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
    private List<List<Vertex>> needMines = new ArrayList<>();
    private List<Vertex> allMines = new ArrayList<>();


    // метод находит оптимальный путь для сбора необходимого количества ресурсов и доставки их на базу
    public Answer findAnswer(Map map) {

        var edges = map.edge();
        var vertices = map.vertex();
        var goal = map.goal().resources();
        var robotSize = map.robot().size();
        var curRes = 0;

        var baseVertex = vertices.stream()
                .filter(vertex -> vertex.type().equals(BASE))
                .findFirst()
                .get();


        var mapFromTo = paths(baseVertex.id(), edges, vertices);
//        List<VertexPath> paths = mapFromTo.values().stream()
//                .sorted(Comparator
//                        .comparing(VertexPath::length)
//                        .thenComparing(VertexPath::resources, Comparator.reverseOrder()))
//                .toList();

//        logger.info(paths);


        java.util.Map<Integer, VertexPath> finalMapFromTo = mapFromTo;
        allMines = vertices.stream()
                .filter(vertex -> vertex.type().equals(MINE))
                .sorted(Comparator
                        .comparing(mine -> finalMapFromTo.get(((Vertex) mine).id()).length())
                        .thenComparing(o -> Integer.compare(((Vertex) o).resources(), ((Vertex) o).resources()) , Comparator.reverseOrder()))
//                .map(Vertex::id)
                .toList();

        getNeedMines(-1, goal, new ArrayList<>());
        logger.info(needMines);

        List<Integer> answerPath = new ArrayList<>();

        for(int stage = 0; stage < (goal / robotSize + (goal % robotSize != 0 ? 1 : 0)); stage++) {

            int optimalPathLength = Integer.MAX_VALUE;
            List<Integer> optimalPath = new ArrayList<>();
            int curLength;
            List<Integer> curPath;
            int startId;

            for(List<Vertex> mines : needMines) {
                curLength = 0;
                curPath = new ArrayList<>();
                startId = baseVertex.id();

                for(Vertex mine : mines) {
                    mapFromTo = paths(startId, edges, vertices);
                    curLength += mapFromTo.get(mine.id()).length();
                    curPath.addAll(mapFromTo.get(mine.id()).path());

                    startId = mine.id();
                }

                if(curLength <= optimalPathLength) {
                    optimalPathLength = curLength;
                    optimalPath = curPath;
                }
            }

            answerPath.addAll(optimalPath);
            answerPath.addAll(paths(optimalPath.get(optimalPath.size() - 1), edges, vertices).get(baseVertex.id()).path());
        }

        logger.info(answerPath);

//        logger.info(findPath(3, 7, edges, vertices));

        //TODO - напишите здесь реализацию метода поиска одного из возможных оптимальных маршрутов на карте
        List<Integer> path = Collections.emptyList();
        Answer answer = new Answer(path);
        logger.info("Response: {}", answer);
        return answer;
    }

    java.util.Map<Integer, VertexPath> paths(int start, List<Edge> edges, List<Vertex> vertices) {
        Set<Integer> passedPoints = new HashSet<>();
        java.util.Map<Integer, VertexPath> fasterPaths = new HashMap<>();

        fasterPaths.put(start, new VertexPath(0, 0, List.of(start)));

        while(passedPoints.size() != vertices.size()) {
            var fastestVertex = fasterPaths.values().stream()
                    .sorted(Comparator.comparing(VertexPath::length))
                    .filter(Predicate.not(vertex -> passedPoints.contains(vertex.path().get(vertex.path().size() - 1))))
                    .findFirst()
                    .get();
            var fastestVertexId = fastestVertex.path().get(fastestVertex.path().size() - 1);

            var closestPoints = edges.stream()
                    .filter(edge -> edge.start() == fastestVertexId)
                    .map(edge -> edge.stop())
                    .filter(Predicate.not(passedPoints::contains))
                    .collect(Collectors.toSet());

            closestPoints.addAll(edges.stream()
                    .filter(edge -> edge.stop() == fastestVertexId)
                    .map(edge -> edge.start())
                    .filter(Predicate.not(passedPoints::contains))
                    .collect(Collectors.toSet()));


            for(var pointId : closestPoints) {
                var curEdge = edges.stream()
                        .filter(edge -> edge.start() == fastestVertexId && edge.stop() == pointId
                                || edge.stop() == fastestVertexId && edge.start() == pointId)
                        .findFirst()
                        .get();
                var curVertex = vertices.stream()
                        .filter(vertex -> vertex.id() == pointId)
                        .findFirst()
                        .get();

                var path = new LinkedList<>(fastestVertex.path());
                path.addLast(pointId);

                var curVertexPath = new VertexPath(
                        fastestVertex.length() + curEdge.size(),
                        fastestVertex.resources() + curVertex.resources(),
                        path);

                if(fasterPaths.containsKey(pointId) && fasterPaths.get(pointId).length() > curVertexPath.length()) {
                    fasterPaths.put(
                            pointId,
                            curVertexPath
                    );
                }

                if(!fasterPaths.containsKey(pointId)) {
                    fasterPaths.put(
                            pointId,
                            curVertexPath
                    );
                }
            }
            passedPoints.add(fastestVertexId);
            logger.info(fastestVertexId);
        }

        passedPoints.forEach(logger::info);
        fasterPaths.forEach((key, value) -> logger.info(key + " -> " + value));

        return fasterPaths;
    }

    private void getNeedMines(int counter, int goal, List<Vertex> curMines) {
        if(curMines.stream().map(Vertex::resources).reduce((i1, i2) -> i1+i2).orElse(0) >= goal) {
            needMines.add(curMines);
        }
        if(counter == allMines.size()) {
            return;
        } else {
            for(int i = counter + 1; i < allMines.size(); i++) {
                curMines.add(allMines.get(i));
                getNeedMines(i, goal, new ArrayList<>(curMines));
                curMines.remove(curMines.size() - 1);
            }
        }

    }
}
