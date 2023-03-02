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
    private java.util.Map<Integer, Integer> idToResources = new HashMap<>();


    // метод находит оптимальный путь для сбора необходимого количества ресурсов и доставки их на базу
    public Answer findAnswer(Map map) {

        var edges = map.edge();
        var vertices = map.vertex();
//        var goal = map.goal().resources();
        var robotSize = map.robot().size();
        var curRes = 0;
        var leftResources = map.goal().resources();

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

        allMines.forEach(mine -> idToResources.put(mine.id(), mine.resources()));

        getNeedMines(-1, leftResources, new ArrayList<>());
        logger.info(needMines);

        List<Integer> resultPathWithRepeats = new ArrayList<>();

        //for(int stage = 0; stage < (goal / robotSize + (goal % robotSize != 0 ? 1 : 0)); stage++) {
        while(leftResources > 0) {

            int pathLength = Integer.MAX_VALUE;
            List<Integer> path = new ArrayList<>();
            List<Integer> optimalPath = new ArrayList<>();
            int curLength;
            List<Integer> curPath;
            int startId;
            getNeedMines(-1, leftResources, new ArrayList<>());

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

                if(curLength <= pathLength) {
                    pathLength = curLength;
                    path = curPath;
                }
            }

            for(int id : path) {
                logger.info("Id == " + id);
                if(curRes == robotSize || curRes >= leftResources)
                    break;

                if(id == baseVertex.id()) {
                    leftResources -= curRes;
                    curRes = 0;
                }

                if(idToResources.containsKey(id)) {
                    logger.info("Id " + id + " in map");
                    logger.info("Resources right now = " + curRes);
                    int tempRes = curRes;

                    int mineResources = idToResources.get(id);
                    logger.info("Id " + id + " has " + mineResources + " resources");

                    if(tempRes + mineResources > robotSize) {
                        logger.info("Will be more than robot size");
                        curRes = robotSize;
                        idToResources.replace(id, (tempRes + mineResources) - robotSize);
                    } else {
                        curRes += mineResources;
                        idToResources.replace(id, 0);
                    }
                }

                optimalPath.add(id);
            }

//            optimalPath = path;

            resultPathWithRepeats.addAll(optimalPath);
            resultPathWithRepeats.addAll(paths(optimalPath.get(optimalPath.size() - 1), edges, vertices).get(baseVertex.id()).path());

            leftResources -= curRes;
            curRes = 0;
//            leftResources = 0;
        }

        List<Integer> answerPath = new ArrayList<>();

        var previousId = -1;
        for(int id : resultPathWithRepeats) {
            if(id != previousId)
                answerPath.add(id);
            previousId = id;
        }

        logger.info(answerPath); //0 4 0 1 0 3 0 1 2 5 2 1 0

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
                        fastestVertex.resources() + (idToResources.containsKey(curVertex.id()) ? idToResources.get(curVertex.id()) : 0),
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
        if(curMines.stream().map(vertex -> idToResources.get(vertex.id())).reduce((i1, i2) -> i1+i2).orElse(0) >= goal) {
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
