package org.olympiad.service;

import com.google.gson.Gson;
import org.olympiad.model.map.GameMap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Reader {

    public static String readFileToString(final String fileName) throws IOException {
        var filePath = Path.of(fileName);
        return Files.readString(filePath);
    }

    public GameMap readMap(String pathToFile) throws IOException {
        Gson gson = new Gson();
        final String jsonIn = readFileToString(pathToFile);
        return gson.fromJson(jsonIn, GameMap.class);
    }

}
