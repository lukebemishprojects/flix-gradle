package dev.lukebemish.flix.task;

import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

public abstract class FlixCompile extends DefaultTask {
    @Classpath
    public abstract ConfigurableFileCollection getClasspath();
    @InputFiles
    public abstract ConfigurableFileCollection getFlixInput();
    @InputFiles
    public abstract Property<FileCollection> getSource();
    @OutputDirectory
    public abstract DirectoryProperty getDestinationDirectory();

    private final ExecOperations execOperations;

    @Inject
    public FlixCompile(ExecOperations execOperations) {
        this.execOperations = execOperations;
    }

    @TaskAction
    public void exec() {
        List<File> classpath = new ArrayList<>();
        getClasspath().forEach(it -> {
            if (it.isDirectory()) {
                classpath.add(it);
            } else if (it.getName().endsWith(".jar")) {
                classpath.add(it);
            }
        });

        URL[] urls = new URL[classpath.size()];
        for (int i = 0; i < classpath.size(); i++) {
            try {
                urls[i] = classpath.get(i).toURI().toURL();
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }

        File outputDir = getDestinationDirectory().get().getAsFile();
        if (outputDir.exists() && outputDir.isDirectory()) {
            try {
                FileUtils.deleteDirectory(outputDir);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            outputDir.mkdir();
        }

        try (URLClassLoader compilerClassLoader = new URLClassLoader(urls)) {
            Class<?> flix = Class.forName("ca.uwaterloo.flix.api.Flix", true, compilerClassLoader);
            MethodHandle addFlix = MethodHandles.publicLookup().findVirtual(flix, "addFlix", MethodType.methodType(flix, Path.class));
            MethodHandle addJar = MethodHandles.publicLookup().findVirtual(flix, "addJar", MethodType.methodType(flix, Path.class));
            MethodHandle addPkg = MethodHandles.publicLookup().findVirtual(flix, "addPkg", MethodType.methodType(flix, Path.class));
            MethodHandle ctorFlix = MethodHandles.publicLookup().findConstructor(flix, MethodType.methodType(void.class));

            Object flixInstance = ctorFlix.invoke();
            getFlixInput().forEach(it -> {
                if (it.getName().endsWith(".fpkg")) {
                    try {
                        addPkg.invoke(flixInstance, it.toPath());
                    } catch (Throwable throwable) {
                        throw new RuntimeException(throwable);
                    }
                } else if (it.getName().endsWith(".jar")) {
                    try {
                        addJar.invoke(flixInstance, it.toPath());
                    } catch (Throwable throwable) {
                        throw new RuntimeException(throwable);
                    }
                } else {
                    throw new RuntimeException("Unknown file type "+it.getName());
                }
            });
            getSource().get().getAsFileTree().forEach(it -> {
                try {
                    addFlix.invoke(flixInstance, it.toPath());
                } catch (Throwable throwable) {
                    throw new RuntimeException(throwable);
                }
            });

            Class<?> options = Class.forName("ca.uwaterloo.flix.util.Options", true, compilerClassLoader);
            Object defaultOptions = options.getMethod("Default").invoke(null);
            Method copy = Arrays.stream(options.getMethods()).filter(m -> m.getName().equals("copy")).findFirst().orElseThrow();
            Method[] copyOptions = IntStream.rangeClosed(1, copy.getParameterCount()).mapToObj(i -> {
                try {
                    return options.getMethod("copy$default$"+i);
                } catch (NoSuchMethodException e) {
                    throw new RuntimeException(e);
                }
            }).toArray(Method[]::new);

            int outputIdx = -1;
            for (int i = 0; i < copy.getParameterCount(); i++) {
                Parameter p = copy.getParameters()[i];
                if (p.getName().equals("output")) {
                    outputIdx = i;
                    break;
                }
            }

            Class<?> scalaOption = Class.forName("scala.Option", true, compilerClassLoader);
            MethodHandle scalaOptionOf = MethodHandles.publicLookup().findStatic(scalaOption, "apply", MethodType.methodType(scalaOption, Object.class));

            Object outputInstance = scalaOptionOf.invoke(getDestinationDirectory().get().getAsFile().toPath());
            Object[] args = new Object[copy.getParameterCount()];

            for (int i = 0; i < copy.getParameterCount(); i++) {
                if (i == outputIdx) {
                    args[i] = outputInstance;
                } else {
                    args[i] = copyOptions[i].invoke(defaultOptions);
                }
            }

            Object optionsInstance = copy.invoke(defaultOptions, args);

            MethodHandle setOptions = MethodHandles.publicLookup().findVirtual(flix, "setOptions", MethodType.methodType(flix, options));

            setOptions.invoke(flixInstance, optionsInstance);

            MethodHandle compile = MethodHandles.publicLookup().findVirtual(flix, "compile", MethodType.methodType(Class.forName("ca.uwaterloo.flix.util.Validation", true, compilerClassLoader)));

            Object validation = compile.invoke(flixInstance);
        } catch (Throwable exc) {
            throw new RuntimeException(exc);
        }
    }
}
