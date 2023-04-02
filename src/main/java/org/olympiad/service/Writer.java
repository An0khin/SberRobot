package org.olympiad.service;

import com.google.gson.Gson;
import org.olympiad.model.answer.Answer;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public record Writer(String pathToFile, Answer path) {

    public void write() throws IOException {
        Gson gson = new Gson();
        try(FileWriter fileWriter = new FileWriter(pathToFile);
            PrintWriter printWriter = new PrintWriter(fileWriter)) {
            printWriter.print(gson.toJson(path.steps()));
        }
    }
}
