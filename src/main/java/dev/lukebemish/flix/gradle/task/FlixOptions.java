package dev.lukebemish.flix.gradle.task;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.OutputDirectory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.IntStream;

public abstract class FlixOptions {
    @OutputDirectory
    @org.gradle.api.tasks.Optional
    public abstract DirectoryProperty getOutput();

    public Properties create() {
        Properties properties = new Properties();
        if (getOutput().isPresent()) {
            properties.setProperty("output", getOutput().get().getAsFile().getAbsolutePath());
        }
        return properties;
    }

    public Object create(ClassLoader classLoader) {
        try {
            Class<?> options = Class.forName("ca.uwaterloo.flix.util.Options", true, classLoader);
            Object defaultOptions = options.getMethod("Default").invoke(null);
            Method copy = Arrays.stream(options.getMethods()).filter(m -> m.getName().equals("copy")).findFirst().orElseThrow();
            Method[] copyOptions = IntStream.rangeClosed(1, copy.getParameterCount()).mapToObj(i -> {
                try {
                    return options.getMethod("copy$default$" + i);
                } catch (NoSuchMethodException e) {
                    throw new RuntimeException(e);
                }
            }).toArray(Method[]::new);

            Object[] args = new Object[copy.getParameterCount()];

            for (int i = 0; i < copy.getParameterCount(); i++) {
                var name = copy.getParameters()[i].getName();
                var value = handleProperty(name, classLoader);
                if (value.isPresent()) {
                    args[i] = value.get();
                } else {
                    args[i] = copyOptions[i].invoke(defaultOptions);
                }
            }

            return copy.invoke(defaultOptions, args);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Optional<Object> handleProperty(String option, ClassLoader classLoader) {
        return switch (option) {
            case "output" -> getOutput().isPresent() ? Optional.of(scalaOptionOf(getOutput().get().getAsFile().toPath(), classLoader)) : Optional.empty();
            default -> Optional.empty();
        };
    }

    private Object scalaOptionOf(Object value, ClassLoader classLoader) {
        try {
            Class<?> scalaOption = Class.forName("scala.Option", true, classLoader);
            MethodHandle scalaOptionOf = MethodHandles.publicLookup().findStatic(scalaOption, "apply", MethodType.methodType(scalaOption, Object.class));
            return scalaOptionOf.invoke( value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
