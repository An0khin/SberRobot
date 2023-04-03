package org.olympiad.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.olympiad.model.VertexPath;
import org.olympiad.model.answer.Answer;
import org.olympiad.model.answer.AnswerAction;
import org.olympiad.model.answer.AnswerStep;
import org.olympiad.model.map.*;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class PathResolver {
    public static final String MINE = "mine";
    private static final Logger logger = LogManager.getLogger(PathResolver.class);
    private final List<List<Vertex>> needMines = new ArrayList<>();
    private final Map<Integer, VertexResource> idToResources = new HashMap<>();
    private final Map<String, Integer> needResources = new HashMap<>();
    private List<Vertex> allMines = new ArrayList<>();

    // метод находит оптимальный путь для сбора необходимого количества ресурсов и доставки их на базу
    public Answer findAnswer(GameMap map) {

        var edges = map.edges();
        var vertices = map.vertexes();
        var robotSize = map.robot().maxSize();

        for(Base base : map.bases()) {
            for(BaseResource resource : base.resources()) {
                needResources.put(
                        resource.name(),
                        resource.necessarySize() + needResources.getOrDefault(resource.name(), 0)
                );
            }
        }

        int leftResources = needResources.values().stream()
                .reduce(Integer::sum)
                .orElse(0);

        var baseVertices = map.bases();

        var baseStartId = baseVertices.stream()
                .filter(base -> base.name().equals(map.robot().baseStartName()))
                .map(Base::vertexId)
                .findFirst()
                .orElse(0);

        int startId = baseStartId;

        var firstPath = paths(baseStartId, edges, vertices);

        allMines = vertices.stream()
                .filter(vertex -> vertex.type().equals(MINE))
                .filter(vertex -> firstPath.get(vertex.id()) != null)
                .sorted(Comparator
                        .comparing(mine -> firstPath.get(((Vertex) mine).id()).length())
                        .thenComparing(o -> ((Vertex) o).resource().size(), Comparator.reverseOrder()))
                .toList();

        allMines.forEach(mine -> idToResources.put(mine.id(), mine.resource()));

        List<Integer> resultPathWithRepeats = new ArrayList<>();

        var curResSize = 0;
        Map<String, Integer> curResourcesByType = new HashMap<>();

        Map<Integer, List<BaseResource>> baseResources = new HashMap<>();
        for(Base base : baseVertices) {
            List<BaseResource> resources = new ArrayList<>(base.resources());
            baseResources.put(base.vertexId(), resources);
        }

        List<AnswerStep> steps = new ArrayList<>();

        while(leftResources > 0) {
            List<Integer> path = new ArrayList<>();
            int pathLength = Integer.MAX_VALUE;

            List<Integer> optimalPath = new ArrayList<>();


            for(Map.Entry<String, Integer> entry : needResources.entrySet()) {
                getRichMines(-1, entry.getValue(), entry.getKey(), new ArrayList<>());
            }


            for(List<Vertex> mines : needMines) {
                int curLength = 0;
                List<Integer> curPath = new ArrayList<>();
                int tempStart = startId;

                for(Vertex mine : mines) {
                    var pathFromBaseTo = paths(tempStart, edges, vertices);
                    curLength += pathFromBaseTo.get(mine.id()).length();
                    curPath.addAll(pathFromBaseTo.get(mine.id()).path());

                    tempStart = mine.id();
                }

                if(curLength <= pathLength) {
                    pathLength = curLength;
                    path = curPath;
                }
            }


            //Возврат на базу
            List<Integer> basesIds = new ArrayList<>();

            if(!curResourcesByType.isEmpty()) {
                for(Base base : baseVertices) {
                    List<String> types = base.resources().stream()
                            .map(BaseResource::name)
                            .toList();

                    for(String key : curResourcesByType.keySet()) {
                        if(types.contains(key) && baseResources.containsKey(base.vertexId())) {
                            basesIds.add(base.vertexId());
                            break;
                        }
                    }
                }
            }


            //Получение ближайшей базы
            int curLength;
            List<Integer> curPath = new ArrayList<>();
            List<Integer> bestBasePath = new ArrayList<>();
            int minBaseLength = Integer.MAX_VALUE;

            for(int baseId : basesIds) {
                curPath.clear();
                var pathFromBaseTo = paths(startId, edges, vertices);
                curLength = pathFromBaseTo.get(baseId).length();
                curPath.addAll(pathFromBaseTo.get(baseId).path());

                if(curLength < minBaseLength) {
                    minBaseLength = curLength;
                    bestBasePath = new ArrayList<>(curPath);
                }
            }

            if(curResSize == robotSize || curResSize >= leftResources) {
                pathLength = minBaseLength;
                path = bestBasePath;
            }

            //Выстраивание пути
            List<AnswerStep> tempSteps = new ArrayList<>();

            for(int id : path) {
                List<AnswerAction> actions = new ArrayList<>();

                if(baseResources.containsKey(id)) { //Закидываем ресы на базу
                    List<BaseResource> list = new ArrayList<>();
                    for(BaseResource baseResource : baseResources.get(id)) {
                        String name = baseResource.name();
                        if(!curResourcesByType.containsKey(name)) {
                            list.add(baseResource);
                            continue;
                        }

                        int resources = curResourcesByType.get(baseResource.name());

                        if(resources < baseResource.necessarySize()) {
                            list.add(new BaseResource(name, baseResource.necessarySize() - resources));
                            curResourcesByType.remove(baseResource.name());

                            actions.add(new AnswerAction("put", baseResource.name(), baseResource.necessarySize() - resources));
                        } else {
                            resources -= baseResource.necessarySize();
                            if(resources > 0) {
                                curResourcesByType.replace(name, resources);
                            } else {
                                curResourcesByType.remove(baseResource.name());
                            }
                            actions.add(new AnswerAction("put", baseResource.name(), baseResource.necessarySize()));
                        }
                    }

                    if(list.isEmpty()) {
                        baseResources.remove(id);
                    }

                    baseResources.replace(id, list);

                    int tempRes = curResSize;
                    curResSize = curResourcesByType.values().stream()
                            .reduce(Integer::sum)
                            .orElse(0);

                    leftResources -= tempRes - curResSize;
                }

                if(idToResources.containsKey(id)) { //Получаем ресы из шахты
                    VertexResource mineVertex = idToResources.get(id);
                    int resources = mineVertex.size();
                    String type = mineVertex.name();
                    int needTypeResources = needResources.get(type);

                    int[] values = new int[] {resources, needTypeResources, robotSize - curResSize};

                    int takeResources = Arrays.stream(values).min().getAsInt();

                    if(takeResources != 0) {
                        actions.add(new AnswerAction("get", type, takeResources));

                        if(curResourcesByType.containsKey(type)) {
                            curResourcesByType.replace(type, takeResources + curResourcesByType.get(type));
                        } else {
                            curResourcesByType.put(type, takeResources);
                        }


                        curResSize += takeResources;

                        if(needTypeResources - takeResources == 0) {
                            needResources.remove(type);
                        }
                        if(resources - takeResources == 0) {
                            idToResources.remove(id);
                        }

                        if(needResources.containsKey(type)) {
                            needResources.replace(type, needTypeResources - takeResources);
                        }
                        if(idToResources.containsKey(id)) {
                            idToResources.replace(id, new VertexResource(type, resources - takeResources));
                        }
                    }
                }

                tempSteps.add(new AnswerStep(id, actions));
                optimalPath.add(id);
            }

            resultPathWithRepeats.addAll(optimalPath);
            steps.addAll(tempSteps);

            startId = resultPathWithRepeats.get(resultPathWithRepeats.size() - 1);
            needMines.clear();
        }

        List<AnswerStep> answerPath = new ArrayList<>();

        var previousId = -1;
        for(AnswerStep step : steps) {
            if(step.vertexId() != previousId)
                answerPath.add(step);
            previousId = step.vertexId();
        }

        System.out.println(answerPath);

        logger.info("Map: {}", map);
        Answer answer = new Answer(answerPath);
        logger.info("Answer: {}", answer);
        return answer;
    }

    Map<Integer, VertexPath> paths(int start, List<Edge> edges, List<Vertex> vertices) {
        Set<Integer> passedPoints = new HashSet<>();
        Map<Integer, VertexPath> fastestPaths = new HashMap<>();

        fastestPaths.put(
                start,
                new VertexPath(start, 0, 0, List.of(start))
        );

        while(passedPoints.size() != vertices.size()) {
            var fastestVertex = fastestPaths.values().stream()
                    .sorted(Comparator.comparing(VertexPath::length))
                    .filter(Predicate.not(vertex -> passedPoints.contains(vertex.stop())))
                    .findFirst()
                    .orElse(null);

            if(fastestVertex == null) {
                break;
            }

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

                var mineResource = idToResources.get(curVertex.id());
                var curVertexPath = new VertexPath(
                        pointId,
                        fastestVertex.length() + curEdge.size(),
                        fastestVertex.resources() + (mineResource == null ? 0 : mineResource.size()),
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

    private void getRichMines(int counter, int goal, String type, List<Vertex> curMines) {
        List<Vertex> allMinesByType = allMines.stream()
                .filter(vertex -> vertex.resource().name().equals(type))
                .toList();

        List<Vertex> minesByType = curMines.stream()
                .filter(vertex -> vertex.resource().name().equals(type))
                .toList();

        int mineResources = minesByType.stream()
                .map(vertex -> idToResources.get(vertex.id()).size())
                .reduce(Integer::sum)
                .orElse(0);

        if(mineResources >= goal) {
            needMines.add(minesByType);
        }

        if(counter != allMinesByType.size()) {
            for(int i = counter + 1; i < allMinesByType.size(); i++) {
                curMines.add(allMinesByType.get(i));
                getRichMines(i, goal, type, new ArrayList<>(curMines));
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
