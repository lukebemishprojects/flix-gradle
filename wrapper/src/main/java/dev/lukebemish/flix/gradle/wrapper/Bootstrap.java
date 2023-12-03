package dev.lukebemish.flix.gradle.wrapper;

public final class Bootstrap {
    private Bootstrap() {}

    public static void main(String[] args) {
        try {
            Class.forName("ca.uwaterloo.flix.api.Flix", false, Bootstrap.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Could not find flix standard library");
        }
        Wrapper.main(args);
    }
}
