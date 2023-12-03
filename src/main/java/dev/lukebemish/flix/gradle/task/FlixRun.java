package dev.lukebemish.flix.gradle.task;

import org.gradle.api.Project;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.internal.ExecActionFactory;

import javax.inject.Inject;
import java.util.Properties;

public abstract class FlixRun extends AbstractFlixCompile {
    @Input
    public abstract ListProperty<String> getArgs();

    @Inject
    public FlixRun(Project project, ExecActionFactory execActionFactory) {
        super(project, execActionFactory);
    }

    @TaskAction
    public void exec() {
        Properties properties = getOptions().create();
        addFlixInput(properties);
        properties.put("command", "run");

        String[] args = getArgs().get().toArray(String[]::new);

        runExec(properties, args);
    }
}
