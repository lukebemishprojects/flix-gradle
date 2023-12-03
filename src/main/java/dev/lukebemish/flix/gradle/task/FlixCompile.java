package dev.lukebemish.flix.gradle.task;

import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.internal.ExecActionFactory;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.Properties;

public abstract class FlixCompile extends AbstractFlixCompile {
    @OutputDirectory
    public abstract DirectoryProperty getDestinationDirectory();

    @Inject
    public FlixCompile(Project project, ExecActionFactory execActionFactory) {
        super(project, execActionFactory);
        this.getOptions().getOutput().convention(getDestinationDirectory());
    }

    @TaskAction
    public void exec() {
        File outputDir = getDestinationDirectory().get().getAsFile();
        if (outputDir.exists() && outputDir.isDirectory()) {
            try {
                FileUtils.deleteDirectory(outputDir);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            outputDir.mkdir();
        }

        Properties properties = getOptions().create();
        addFlixInput(properties);
        properties.put("command", "compile");

        runExec(properties);
    }
}
