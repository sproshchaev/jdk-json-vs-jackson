package demo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * A script-like program on the JDK API: read one file, print one number, exit.
 * Kept apart from OneShotJackson so that neither run loads the other's classes.
 */
public final class OneShotJdk {

    public static void main(String[] args) throws IOException {
        String doc = Files.readString(Path.of(DataFiles.FORECAST));
        System.out.printf(Locale.ROOT, "%.4f%n", new JdkTasks().averageTemperature(doc));
    }
}
