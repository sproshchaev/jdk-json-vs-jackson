package demo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** A script-like program: read one file, print one number, exit. */
public final class OneShot {

    public static void main(String[] args) throws IOException {
        String doc = Files.readString(Path.of(DataFiles.FORECAST));
        JsonTasks tasks = switch (args[0]) {
            case "jdk" -> new JdkTasks();
            case "jackson-tree" -> new JacksonTreeTasks();
            case "jackson-binding" -> new JacksonBindingTasks();
            default -> throw new IllegalArgumentException(args[0]);
        };
        System.out.printf(Locale.ROOT, "%.4f%n", tasks.averageTemperature(doc));
    }
}
