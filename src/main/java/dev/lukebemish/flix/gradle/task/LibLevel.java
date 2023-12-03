package dev.lukebemish.flix.gradle.task;

import org.gradle.api.Named;

public abstract class LibLevel implements Named {
    public static final String NIX = "nix";
    public static final String MIN = "min";
    public static final String ALL = "all";
}
