package dev.lukebemish.flix.gradle.task;

import dev.lukebemish.flix.gradle.wrapper.WrapperFinder;
import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.process.ExecOperations;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.jar.Manifest;

@DisableCachingByDefault(
    because = "Super-class, not to be instantiated directly"
)
public abstract class AbstractFlixCompile extends DefaultTask {
    @InputFiles
    public abstract ConfigurableFileCollection getFlixInput();
    @SkipWhenEmpty
    @InputFiles
    public abstract Property<FileCollection> getSource();
    @Nested
    public abstract FlixOptions getOptions();

    private final ExecOperations execOperations;
    @Inject
    public AbstractFlixCompile(ExecOperations execOperations) {
        this.execOperations = execOperations;
    }

    protected Path runExec(Properties properties) {
        Path tempDir = getTemporaryDir().toPath();

        Path tempClasses = tempDir.resolve("classes");

        URL classUrl = WrapperFinder.class.getResource(WrapperFinder.class.getSimpleName() + ".class");
        if (classUrl == null) {
            throw new RuntimeException("Could not find class " + WrapperFinder.class.getSimpleName());
        }
        var wrapperClassPath = classUrl.toExternalForm();
        String manifestPath = wrapperClassPath.substring(0, wrapperClassPath.lastIndexOf("!") + 1) +
            "/META-INF/MANIFEST.MF";

        try (var is = new URL(manifestPath).openStream()) {
            Manifest mf = new Manifest(is);
            String[] classes = mf.getMainAttributes().getValue("Flix-Gradle-Wrapper-Classes").split(",");
            for (var className : classes) {
                try (var classStream = WrapperFinder.class.getResourceAsStream("/" + className)) {
                    if (classStream == null) {
                        throw new RuntimeException("Could not find class " + className);
                    }
                    var out = tempClasses.resolve(className);
                    Files.createDirectories(out.getParent());
                    if (Files.exists(out)) {
                        Files.delete(out);
                    }
                    Files.copy(classStream, out);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        List<File> classpath = new ArrayList<>();
        getFlixInput().forEach(it -> {
            if (it.isDirectory()) {
                classpath.add(it);
            } else if (it.getName().endsWith(".jar")) {
                classpath.add(it);
            }
        });

        classpath.add(tempClasses.toFile());
        Path propertiesFile = tempDir.resolve("options.properties");
        try (var writer = Files.newBufferedWriter(propertiesFile)) {
            properties.store(writer, "Flix Gradle Wrapper Options");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Path runDir = tempDir.resolve("run");
        try {
            if (Files.exists(runDir)) {
                FileUtils.deleteDirectory(runDir.toFile());
            }
            Files.createDirectories(runDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        execOperations.javaexec(exec -> {
            exec.classpath(classpath.toArray());
            exec.getMainClass().set("dev.lukebemish.flix.gradle.wrapper.Wrapper");
            exec.args(propertiesFile.toAbsolutePath().toString());

            exec.setWorkingDir(runDir.toFile());
        });

        return runDir;
    }

    protected void addFlixInput(Properties properties) {
        List<String> sourceJars = new ArrayList<>();
        List<String> sourceFlix = new ArrayList<>();
        List<String> sourceFlixPkgs = new ArrayList<>();

        getFlixInput().forEach(it -> {
            if (it.getName().endsWith(".fpkg")) {
                try {
                    sourceFlixPkgs.add(it.getAbsolutePath());
                } catch (Throwable throwable) {
                    throw new RuntimeException(throwable);
                }
            } else if (it.getName().endsWith(".jar")) {
                try {
                    sourceJars.add(it.getAbsolutePath());
                } catch (Throwable throwable) {
                    throw new RuntimeException(throwable);
                }
            } else {
                throw new RuntimeException("Unknown file type "+it.getName());
            }
        });
        getSource().get().getAsFileTree().forEach(it -> {
            try {
                sourceFlix.add(it.getAbsolutePath());
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        });

        properties.put("sourceJars", String.join(",", sourceJars));
        properties.put("sourceFlix", String.join(",", sourceFlix));
        properties.put("sourceFlixPkgs", String.join(",", sourceFlixPkgs));
    }
}
