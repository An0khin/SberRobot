package org.olympiad.service;

import com.google.gson.Gson;
import org.olympiad.model.Map;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Reader {

    public static String readFileToString(final String fileName) throws IOException {
        var filePath = Path.of(fileName);
        return Files.readString(filePath);
    }

    public Map readMap(String pathToFile) throws IOException {
        Gson gson = new Gson();
        final String jsonIn = readFileToString(pathToFile);
        Map map = gson.fromJson(jsonIn, Map.class);
        return map;
    }

}
