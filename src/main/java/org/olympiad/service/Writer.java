package org.olympiad.service;

import com.google.gson.Gson;
import org.olympiad.model.Answer;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class Writer {
    private final String pathToFile;
    private final Answer path;

    public Writer(String pathToFile, Answer path) {
        this.pathToFile = pathToFile;
        this.path = path;
    }

    public void write() throws IOException {
        Gson gson = new Gson();
        try (FileWriter fileWriter = new FileWriter(pathToFile);
             PrintWriter printWriter = new PrintWriter(fileWriter)) {
            printWriter.print(gson.toJson(path));
        }
    }
}
