package dev.lukebemish.flix.task;

import org.gradle.api.tasks.bundling.Zip;

public abstract class Fpkg extends Zip {
    public Fpkg() {
        getArchiveExtension().set("fpkg");
    }
}
