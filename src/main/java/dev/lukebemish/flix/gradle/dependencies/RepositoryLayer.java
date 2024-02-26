package dev.lukebemish.flix.gradle.dependencies;

import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.jetbrains.annotations.ApiStatus;

import java.net.URI;

@ApiStatus.Internal
public abstract class RepositoryLayer implements BuildService<BuildServiceParameters.None>, AutoCloseable {

    private final FpkgRepositoryLayer.LayerServer layerServer;
    public RepositoryLayer() {
        try {
        layerServer = FpkgRepositoryLayer.startServer();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        layerServer.close();
    }

    public URI url() {
        return layerServer.uri();
    }
}
