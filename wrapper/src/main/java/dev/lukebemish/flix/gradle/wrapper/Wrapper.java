package dev.lukebemish.flix.gradle.wrapper;

import ca.uwaterloo.flix.api.Bootstrap;
import ca.uwaterloo.flix.api.Flix;
import scala.Option;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Properties;

public final class Wrapper {
    private Wrapper() {}

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new RuntimeException("Must provide a properties file");
        }

        Path argFile = Paths.get(args[0]);
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(argFile, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        FlixOptions options = new FlixOptions();
        options.read(properties);

        FlixCommand command = FlixCommand.valueOf(properties.getProperty("command").toUpperCase(Locale.ROOT));

        Path currentDir = Paths.get("").toAbsolutePath();

        Bootstrap bootstrap = new Bootstrap(currentDir, Option.empty());

        switch (command) {
            case COMPILE -> {
                Flix flix = new Flix();
                flix.setOptions(options.create());
                options.configure(flix);
                flix.compile();
            }
            case DOC -> {
                bootstrap.doc(options.create());
            }
            case RUN -> {
                String[] newArgs;
                if (args.length <= 1) {
                    newArgs = new String[0];
                } else {
                    newArgs = new String[args.length - 1];
                    System.arraycopy(args, 1, newArgs, 0, args.length);
                }
                bootstrap.run(options.create(), newArgs);
            }
        }
    }

    public enum FlixCommand {
        COMPILE,
        DOC,
        RUN
    }
}
