package dev.lukebemish.flix.gradle.task;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;

import java.util.Properties;

public abstract class FlixOptions {
    @OutputDirectory
    @org.gradle.api.tasks.Optional
    public abstract DirectoryProperty getOutput();

    @Input
    @org.gradle.api.tasks.Optional
    public abstract Property<Boolean> getStrictMonomorphism();

    @Input
    public abstract Property<Integer> getJvmTarget();

    @Input
    @org.gradle.api.tasks.Optional
    public abstract Property<LibLevel> getLibLevel();

    public Properties create() {
        Properties properties = new Properties();
        if (getOutput().isPresent()) {
            properties.setProperty("output", getOutput().get().getAsFile().getAbsolutePath());
        }
        if (getStrictMonomorphism().isPresent()) {
            properties.setProperty("strictMonomorphism", getStrictMonomorphism().get().toString());
        }
        if (getJvmTarget().isPresent()) {
            properties.setProperty("jvmTarget", getJvmTarget().get().toString());
        }
        if (getLibLevel().isPresent()) {
            properties.setProperty("libLevel", getLibLevel().get().getName());
        }
        return properties;
    }
}
