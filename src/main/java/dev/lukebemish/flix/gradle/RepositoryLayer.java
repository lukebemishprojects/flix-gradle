package dev.lukebemish.flix.gradle;

import dev.lukebemish.flix.gradle.dependencies.FpkgRepositoryLayer;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

import java.net.URI;

public abstract class RepositoryLayer implements BuildService<BuildServiceParameters.None>, AutoCloseable {

    private final FpkgRepositoryLayer.LayerServer layerServer;
    public RepositoryLayer() {
        layerServer = FpkgRepositoryLayer.startServer();
    }

    @Override
    public void close() {
        layerServer.close();
    }

    public URI url() {
        return layerServer.uri();
    }
}
