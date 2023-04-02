package org.olympiad;

import org.olympiad.model.Answer;
import org.olympiad.model.Map;
import org.olympiad.service.PathResolver;
import org.olympiad.service.Reader;
import org.olympiad.service.Writer;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class Main {
    private static final String FILE_OUTPUT = "answer.json";
    private static final String FILE_INPUT = "src/test/resources/hard1.json";

    public static void main(String[] args) throws IOException {
        final String pathToInFile;
        if(args.length == 1) {
            pathToInFile = args[0];
        } else {
            pathToInFile = FILE_INPUT;
        }
        // прочитать карту
        Reader reader = new Reader();
        Map map = reader.readMap(pathToInFile);

        PathResolver resolver = new PathResolver();
        // найти один из возможных оптимальных путей на карте
        Answer answer = resolver.findAnswer(map);

        //сохранить найденный путь в файл
        Writer writer = new Writer(FILE_OUTPUT, answer);
        writer.write();
    }

    public static List<Integer> findAnswer(String path) {
        try {
            Reader reader = new Reader();
            Map map = reader.readMap(path);

            PathResolver resolver = new PathResolver();
            Answer answer = resolver.findAnswer(map);

            return answer.path();
        } catch(Exception e) {
            e.printStackTrace();
        }

        return Collections.emptyList();
    }

}
