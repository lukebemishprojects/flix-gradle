package dev.lukebemish.flix.gradle.wrapper;

import ca.uwaterloo.flix.api.Flix;
import ca.uwaterloo.flix.language.CompilationMessage;
import ca.uwaterloo.flix.runtime.CompilationResult;
import ca.uwaterloo.flix.util.Validation;

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

        Flix flix = new Flix();
        flix.setOptions(options.create());
        options.configure(flix);

        switch (command) {
            case COMPILE -> {
                compile(flix);
            }
            case DOC -> {
                // TODO: Implement
                throw new UnsupportedOperationException("Not yet implemented");
            }
            case RUN -> {
                String[] newArgs;
                if (args.length == 1) {
                    newArgs = new String[0];
                } else {
                    newArgs = new String[args.length - 1];
                    System.arraycopy(args, 1, newArgs, 0, args.length);
                }

                var main = compile(flix).getMain();

                if (main.isEmpty()) {
                    throw new RuntimeException("No main function found");
                } else {
                    main.get().apply(newArgs);
                }
            }
        }
    }

    private static CompilationResult compile(Flix flix) {
        var validation = flix.compile();
        if (!(validation instanceof Validation.Success<CompilationResult, CompilationMessage> success)) {
            scala.collection.Iterable<String> list = flix.mkMessages(validation.toHardFailure().errors());
            while (!list.isEmpty()) {
                System.err.println(list.head());
                list = list.tail();
            }
            throw new RuntimeException("Compilation failed");
        } else {
            return success.get();
        }
    }

    public enum FlixCommand {
        COMPILE,
        DOC,
        RUN
    }
}
