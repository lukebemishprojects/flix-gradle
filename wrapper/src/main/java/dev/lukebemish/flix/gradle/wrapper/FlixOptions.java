package dev.lukebemish.flix.gradle.wrapper;

import ca.uwaterloo.flix.api.Flix;
import ca.uwaterloo.flix.util.JvmTarget;
import ca.uwaterloo.flix.util.LibLevel;
import ca.uwaterloo.flix.util.Options;
import scala.Option;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.IntStream;

class FlixOptions {
    private Path outputDirectory;
    private Boolean strictMonomorphism;
    private JvmTarget jvmTarget;
    private final List<Path> sourceJars = new ArrayList<>();
    private final List<Path> sourceFlix = new ArrayList<>();
    private final List<Path> sourceFlixPkgs = new ArrayList<>();
    private LibLevel libLevel;

    void read(Properties properties) {
        String output = properties.getProperty("output");
        if (output != null) {
            outputDirectory = Path.of(output);
        }
        String strictMonomorphism = properties.getProperty("strictMonomorphism");
        if (strictMonomorphism != null) {
            this.strictMonomorphism = Boolean.parseBoolean(strictMonomorphism);
        }
        String jvmTarget = properties.getProperty("jvmTarget");
        switch (jvmTarget) {
            case "21" -> this.jvmTarget = JvmTarget.Version21$.MODULE$;
            default -> {
                try {
                    int version = Integer.parseInt(jvmTarget);
                    throw new IllegalArgumentException("Unsupported jvmTarget: " + version);
                } catch (NumberFormatException e) {
                    throw new RuntimeException("Invalid jvmTarget: " + jvmTarget);
                }
            }
        }

        String libLevel = properties.getProperty("libLevel");
        if (libLevel != null) {
            this.libLevel = switch (libLevel) {
                case "nix" -> LibLevel.Nix$.MODULE$;
                case "min" -> LibLevel.Min$.MODULE$;
                case "all" -> LibLevel.All$.MODULE$;
                default -> throw new RuntimeException("Unknown lib level: "+libLevel);
            };
        }

        String sourceJars = properties.getProperty("sourceJars");
        if (sourceJars != null) {
            Arrays.stream(sourceJars.split(",")).filter(s->!s.isEmpty()).map(Path::of).forEach(this.sourceJars::add);
        }
        String sourceFlix = properties.getProperty("sourceFlix");
        if (sourceFlix != null) {
            Arrays.stream(sourceFlix.split(",")).filter(s->!s.isEmpty()).map(Path::of).forEach(this.sourceFlix::add);
        }
        String sourceFlixPkgs = properties.getProperty("sourceFlixPkgs");
        if (sourceFlixPkgs != null) {
            Arrays.stream(sourceFlixPkgs.split(",")).filter(s->!s.isEmpty()).map(Path::of).forEach(this.sourceFlixPkgs::add);
        }
    }

    void configure(Flix flix) {
        sourceJars.forEach(flix::addJar);
        sourceFlix.forEach(flix::addFlix);
        sourceFlixPkgs.forEach(flix::addPkg);
    }

    Options create() {
        try {
            Class<?> optionsClass = Options.class;
            Options defaultOptions = Options.Default();

            Method copy = Arrays.stream(optionsClass.getMethods()).filter(m -> m.getName().equals("copy")).findFirst().orElseThrow();
            Method[] copyOptions = IntStream.rangeClosed(1, copy.getParameterCount()).mapToObj(i -> {
                try {
                    return optionsClass.getMethod("copy$default$" + i);
                } catch (NoSuchMethodException e) {
                    throw new RuntimeException(e);
                }
            }).toArray(Method[]::new);

            Object[] args = new Object[copy.getParameterCount()];

            for (int i = 0; i < copy.getParameterCount(); i++) {
                var name = copy.getParameters()[i].getName();
                var value = handleProperty(name);
                if (value.isPresent()) {
                    args[i] = value.get();
                } else {
                    args[i] = copyOptions[i].invoke(defaultOptions);
                }
            }

            return (Options) copy.invoke(defaultOptions, args);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Optional<Object> handleProperty(String option) {
        return switch (option) {
            case "output" -> outputDirectory == null ? Optional.empty() : Optional.of(Option.apply(outputDirectory));
            case "xstrictmono" -> strictMonomorphism == null ? Optional.empty() : Optional.of(strictMonomorphism);
            case "target" -> jvmTarget == null ? Optional.empty() : Optional.of(jvmTarget);
            case "lib" -> libLevel == null ? Optional.empty() : Optional.of(libLevel);
            default -> Optional.empty();
        };
    }
}
