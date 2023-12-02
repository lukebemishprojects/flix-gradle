package dev.lukebemish.flix.gradle.task;

import com.moandjiezana.toml.TomlWriter;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;

public abstract class FlixToml extends DefaultTask {
    @OutputFile
    public abstract RegularFileProperty getDestinationFile();
    @Input
    public abstract ListProperty<ModuleVersionIdentifier> getDependencyArtifactIds();
    @Input
    public abstract ListProperty<Boolean> getDependencyArtifactsAreFpkg();
    @Input
    public abstract Property<String> getPackageName();
    @Input
    public abstract Property<String> getPackageVersion();
    @Input
    @Optional
    public abstract Property<String> getPackageDescription();
    @Input
    @Optional
    public abstract Property<String> getPackageLicense();
    @Input
    @Optional
    public abstract ListProperty<String> getPackageAuthors();

    @Inject
    public FlixToml(Project project) {
        getPackageVersion().convention(project.provider(() ->
            project.getVersion() == "unspecified" ? null : project.getVersion().toString()
        ));
    }

    @TaskAction
    public void generate() {
        Map<String, Object> tomlMap = new HashMap<>();
        Map<String, Object> packageMap = new HashMap<>();
        packageMap.put("name", getPackageName().get());
        packageMap.put("version", getPackageVersion().get());
        if (getPackageDescription().isPresent()) {
            packageMap.put("description", getPackageDescription().get());
        }
        if (getPackageLicense().isPresent()) {
            packageMap.put("license", getPackageLicense().get());
        }
        if (getPackageAuthors().isPresent() && !getPackageAuthors().get().isEmpty()) {
            packageMap.put("authors", getPackageAuthors().get());
        }
        tomlMap.put("package", packageMap);

        Map<String, Object> dependenciesMap = new HashMap<>();
        Map<String, Object> mvnDependenciesMap = new HashMap<>();

        for (int i = 0; i < getDependencyArtifactIds().get().size(); i++) {
            ModuleVersionIdentifier id = getDependencyArtifactIds().get().get(i);
            boolean isFpkg = getDependencyArtifactsAreFpkg().get().get(i);
            var isGitHubFpkg = isFpkg && id.getGroup().startsWith("github/");
            if (isGitHubFpkg) {
                String user = id.getGroup().substring("github/".length());
                dependenciesMap.put("github:"+user+"/"+id.getName(), id.getVersion());
            } else {
                if (isFlix(id)) {
                    packageMap.put("flix", id.getVersion());
                } else {
                    mvnDependenciesMap.put(id.getGroup() + ":" + id.getName(), id.getVersion());
                }
            }
        }

        tomlMap.put("dependencies", dependenciesMap);
        tomlMap.put("mvn-dependencies", mvnDependenciesMap);

        TomlWriter tomlWriter = new TomlWriter();

        getDestinationFile().get().getAsFile().getParentFile().mkdirs();
        getDestinationFile().get().getAsFile().delete();
        try (var os = new FileOutputStream(getDestinationFile().get().getAsFile())) {
            tomlWriter.write(tomlMap, os);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isFlix(ModuleVersionIdentifier id) {
        return id.getGroup().equals("dev.flix") && id.getName().equals("flix");
    }
}
