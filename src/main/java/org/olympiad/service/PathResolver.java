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

    // метод находит оптимальный путь для сбора необходимого количества ресурсов и доставки их на базу
    public Answer findAnswer(GameMap map) {

        List<AnswerStep> steps = getSteps(map);

        List<AnswerStep> answerPath = new ArrayList<>();

        int previousId = -1;
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

    public List<AnswerStep> getSteps(GameMap map) {
        var edges = map.edges();
        var vertices = map.vertexes();
        var robotSize = map.robot().maxSize();

        Map<String, Integer> needResources = new HashMap<>();
        int leftResources = 0;

        for(Base base : map.bases()) {
            for(BaseResource resource : base.resources()) {
                needResources.put(
                        resource.name(),
                        resource.necessarySize() + needResources.getOrDefault(resource.name(), 0)
                );
                leftResources += resource.necessarySize();
            }
        }

        List<Base> baseVertices = map.bases();

        int startId = getBaseStartId(baseVertices, map.robot().baseStartName());

        var firstPath = paths(startId, edges, vertices);

        baseVertices = baseVertices.stream()
                .filter(base -> firstPath.containsKey(base.vertexId()))
                .toList();

        List<Vertex> allMines = vertices.stream()
                .filter(vertex -> vertex.type().equals(MINE))
                .filter(vertex -> firstPath.get(vertex.id()) != null)
                .sorted(Comparator
                        .comparing(mine -> firstPath.get(((Vertex) mine).id()).length())
                        .thenComparing(o -> ((Vertex) o).resource().size(), Comparator.reverseOrder()))
                .toList();

        allMines.forEach(mine -> idToResources.put(mine.id(), mine.resource()));

        int curResSize = 0;
        Map<String, Integer> curResourcesByType = new HashMap<>();

        Map<Integer, List<BaseResource>> baseResources = new HashMap<>();
        baseVertices.forEach(base -> baseResources.put(base.vertexId(), base.resources()));

        List<AnswerStep> steps = new ArrayList<>();

        while(leftResources > 0) {
            List<Integer> path = new ArrayList<>();
            int pathLength = Integer.MAX_VALUE;


            for(Map.Entry<String, Integer> entry : needResources.entrySet()) {
                int min = Math.min(robotSize, entry.getValue());
                getRichMines(-1, min, entry.getKey(), new ArrayList<>(), allMines);
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
            var pathFromStartTo = paths(startId, edges, vertices);

            for(int baseId : basesIds) {
                curPath.clear();
                curLength = pathFromStartTo.get(baseId).length();
                curPath.addAll(pathFromStartTo.get(baseId).path());

                if(curLength < minBaseLength) {
                    minBaseLength = curLength;
                    bestBasePath = new ArrayList<>(curPath);
                }
            }

            if(curResSize == robotSize || curResSize >= leftResources) {
                pathLength = minBaseLength;
                path = bestBasePath;
            }

            if(bestBasePath.size() > 0 && canBaseResources(bestBasePath.get(bestBasePath.size() - 1), curResourcesByType, baseVertices) && minBaseLength <= pathLength) {
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

                            actions.add(new AnswerAction("put", baseResource.name(), resources));
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
                    int needTypeResources = needResources.getOrDefault(type, 0);

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
            }

            steps.addAll(tempSteps);

            startId = steps.get(steps.size() - 1).vertexId();
            needMines.clear();
        }

        return steps;
    }

    private int getBaseStartId(List<Base> baseVertices, String baseStartName) {
        for(Base base : baseVertices) {
            if(base.name().equals(baseStartName)) {
                return base.vertexId();
            }
        }

        throw new NullPointerException("There is no " + baseStartName + " Base");
    }

    //Проверка на наличие хотя бы одного вида необходимых ресурсов в нужном количестве для базы
    private boolean canBaseResources(int baseId, Map<String, Integer> curResourcesByType, List<Base> baseVertices) {
        Optional<Base> base = baseVertices.stream().filter(b -> b.vertexId() == baseId).findFirst();

        if(base.isEmpty()) {
            return false;
        }

        for(BaseResource baseResource : base.get().resources()) {
            if(curResourcesByType.getOrDefault(baseResource.name(), Integer.MIN_VALUE) >= baseResource.necessarySize()) {
                return true;
            }
        }

        return false;
    }

    //Получение всех путей от start узла до всех остальных
    Map<Integer, VertexPath> paths(int start, List<Edge> edges, List<Vertex> vertices) {
        Set<Integer> passedPoints = new HashSet<>();
        Map<Integer, VertexPath> fastestPaths = new HashMap<>();

        fastestPaths.put(
                start,
                new VertexPath(start, 0, 0, List.of(start))
        );

        while(passedPoints.size() != vertices.size()) {
            VertexPath fastestVertex = fastestPaths.values().stream()
                    .sorted(Comparator.comparing(VertexPath::length))
                    .filter(Predicate.not(vertex -> passedPoints.contains(vertex.stop())))
                    .findFirst()
                    .orElse(null);

            if(fastestVertex == null) {
                break;
            }

            int fastestVertexId = fastestVertex.stop();

            Set<Integer> closePoints = getClosePoints(fastestVertexId, edges).stream()
                    .filter(Predicate.not(passedPoints::contains))
                    .collect(Collectors.toSet());

            for(int pointId : closePoints) {
                Edge curEdge = edges.stream()
                        .filter(edge -> edge.start() == fastestVertexId && edge.stop() == pointId
                                || edge.stop() == fastestVertexId && edge.start() == pointId)
                        .findFirst()
                        .orElseThrow();

                Vertex curVertex = vertices.stream()
                        .filter(vertex -> vertex.id() == pointId)
                        .findFirst()
                        .orElseThrow();

                LinkedList<Integer> path = new LinkedList<>(fastestVertex.path());
                path.addLast(pointId);

                VertexResource mineResource = idToResources.get(curVertex.id());
                VertexPath curVertexPath = new VertexPath(
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

    //Получение всех подходящих по количеству ресурсов множеств шахт
    private void getRichMines(int counter, int goal, String type, List<Vertex> curMines, List<Vertex> allMines) {
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
                getRichMines(i, goal, type, new ArrayList<>(curMines), allMines);
                curMines.remove(curMines.size() - 1);
            }
        }
    }

    //Получение всех связанных с from узлов
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
