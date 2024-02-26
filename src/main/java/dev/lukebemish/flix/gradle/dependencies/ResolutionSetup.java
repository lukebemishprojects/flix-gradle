package dev.lukebemish.flix.gradle.dependencies;

import org.gradle.api.flow.FlowAction;
import org.gradle.api.flow.FlowParameters;
import org.gradle.api.provider.Property;
import org.gradle.api.services.ServiceReference;
import org.gradle.api.tasks.Input;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
@SuppressWarnings("UnstableApiUsage")
public abstract class ResolutionSetup implements FlowAction<ResolutionSetup.Parameters> {
    public interface Parameters extends FlowParameters {
        @ServiceReference
        Property<RepositoryLayer> getRepositoryLayer();

        @Input
        Property<Boolean> getShouldClose();
    }

    @Override
    public void execute(@NotNull Parameters parameters) {
        RepositoryLayer repositoryLayer = parameters.getRepositoryLayer().get();
        if (parameters.getShouldClose().get()) {
            repositoryLayer.close();
        }
    }
}
