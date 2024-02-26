package dev.lukebemish.flix.gradle;

import dev.lukebemish.flix.gradle.task.FlixCompile;
import dev.lukebemish.flix.gradle.task.FlixRun;
import dev.lukebemish.flix.gradle.task.LibLevel;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.attributes.java.TargetJvmEnvironment;
import org.gradle.api.attributes.java.TargetJvmVersion;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.bundling.Jar;

import javax.inject.Inject;
import java.util.Map;

public abstract class FlixGradleExtension {
    public abstract Property<Boolean> getSkipRuntimeElements();
    public abstract Property<Boolean> getSkipApiElements();

    private final ObjectFactory objectFactory;
    private final Project project;

    private boolean isApplication = false;


    @Inject
    public FlixGradleExtension(Project project, ObjectFactory objectFactory) {
        this.objectFactory = objectFactory;
        this.project = project;

        this.getSkipRuntimeElements().convention(project.provider(() -> !isApplication));
        this.getSkipApiElements().convention(true);

        getSkipRuntimeElements().finalizeValueOnRead();
        getSkipApiElements().finalizeValueOnRead();
    }

    @Nested
    public abstract LibLevels getLibLevels();

    public abstract static class LibLevels {
        @Inject
        protected ObjectFactory getObjectFactory() {
            throw new UnsupportedOperationException();
        }

        public LibLevel getNix() {
            return getObjectFactory().named(LibLevel.class, LibLevel.NIX);
        }

        public LibLevel getMin() {
            return getObjectFactory().named(LibLevel.class, LibLevel.MIN);
        }

        public LibLevel getAll() {
            return getObjectFactory().named(LibLevel.class, LibLevel.ALL);
        }
    }

    @SuppressWarnings("UnstableApiUsage")
    public void application() {
        this.isApplication = true;
        JavaPluginExtension javaPluginExtension = project.getExtensions().getByType(JavaPluginExtension.class);
        SourceSetContainer sourceSets = javaPluginExtension.getSourceSets();

        sourceSets.all(sourceSet -> {
            var flixSource = (SourceDirectorySet) sourceSet.getExtensions().getByName("flix");

            ConfigurationContainer configurations = project.getConfigurations();

            var flixClasspath = configurations.maybeCreate(FlixGradlePlugin.sourcedNameOf(sourceSet, FlixGradlePlugin.FLIX_CLASSPATH_CONFIGURATION_NAME));
            var flix = configurations.maybeCreate(FlixGradlePlugin.sourcedNameOf(sourceSet, "flix"));

            flixSource.getDestinationDirectory().set(project.getLayout().getBuildDirectory().dir("classes/flix/" + sourceSet.getName()));

            ((ConfigurableFileCollection) sourceSet.getOutput().getClassesDirs()).from(flixSource.getDestinationDirectory());

            var flixCompile = project.getTasks().register(FlixGradlePlugin.sourcedNameOf(sourceSet, "compileFlix"), FlixCompile.class, task -> {
                task.getFlixInput().from(flixClasspath);
                task.getSource().set(flixSource.getSourceDirectories());
            });

            project.getTasks().named(sourceSet.getClassesTaskName(), task -> task.dependsOn(flixCompile));

            flixSource.compiledBy(flixCompile, FlixCompile::getDestinationDirectory);

            Configuration flixRuntimeResolvable = configurations.maybeCreate(FlixGradlePlugin.sourcedNameOf(sourceSet, "flixRuntimeResolvable"));
            flixRuntimeResolvable.exclude(Map.of(
                "group", "dev.flix",
                "module", "flix"
            ));
            flixRuntimeResolvable.extendsFrom(flix);
            flixRuntimeResolvable.setCanBeResolved(true);
            flixRuntimeResolvable.setCanBeConsumed(false);
            flixRuntimeResolvable.setCanBeDeclared(false);
            flixRuntimeResolvable.setVisible(false);
            flixRuntimeResolvable.shouldResolveConsistentlyWith(flixClasspath);
            flixRuntimeResolvable.attributes((attrs) -> {
                attrs.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objectFactory.named(LibraryElements.class, FlixGradlePlugin.FLIX_CLASSES_ELEMENT));
                attrs.attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.JAVA_API));
                attrs.attribute(Category.CATEGORY_ATTRIBUTE, objectFactory.named(Category.class, Category.LIBRARY));
                attrs.attribute(Bundling.BUNDLING_ATTRIBUTE, objectFactory.named(Bundling.class, Bundling.EXTERNAL));
                attrs.attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, objectFactory.named(TargetJvmEnvironment.class, TargetJvmEnvironment.STANDARD_JVM));
                attrs.attributeProvider(
                    TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE,
                    project.provider(() -> Integer.valueOf(javaPluginExtension.getSourceCompatibility().getMajorVersion()))
                );
            });

            Configuration flixRuntimeClasspath = configurations.maybeCreate(FlixGradlePlugin.sourcedNameOf(sourceSet, "flixRuntimeClasspath"));
            Configuration runtimeClasspath = configurations.getByName(sourceSet.getRuntimeClasspathConfigurationName());
            runtimeClasspath.extendsFrom(flixRuntimeClasspath);
            flixRuntimeClasspath.setCanBeResolved(false);
            flixRuntimeClasspath.setCanBeConsumed(false);
            flixRuntimeClasspath.setVisible(false);
            flixRuntimeClasspath.shouldResolveConsistentlyWith(flixClasspath);
            flixRuntimeClasspath.defaultDependencies(deps -> {
                var firstLevel = flixRuntimeResolvable.getResolvedConfiguration().getFirstLevelModuleDependencies();
                FlixGradlePlugin.processDependencies(project, firstLevel).forEach(deps::add);
            });
        });

        Configuration runtimeElements = project.getConfigurations().getByName(JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME);
        runtimeElements.extendsFrom(project.getConfigurations().getByName("flixRuntimeClasspath"));

        project.getTasks().named(JavaPlugin.JAR_TASK_NAME, Jar.class, jar ->
            jar.manifest(manifest ->
                manifest.getAttributes().put("Main-Class", "Main")
            )
        );

        var main = sourceSets.getByName("main");
        var flixSource = (SourceDirectorySet) main.getExtensions().getByName("flix");
        var flixClasspath = project.getConfigurations().maybeCreate(FlixGradlePlugin.sourcedNameOf(main, FlixGradlePlugin.FLIX_CLASSPATH_CONFIGURATION_NAME));
        project.getTasks().register("runFlix", FlixRun.class, task -> {
            task.getFlixInput().from(flixClasspath);
            task.getSource().set(flixSource.getSourceDirectories());
            task.setDescription("Runs this project with flix");
            task.setGroup("application");
            task.getOutputs().upToDateWhen(t -> false);
        });
    }

    // Enabling this will likely require changes
    /*
    public void withDocs() {
        Configuration docsElements = project.getConfigurations().maybeCreate("flixDocsElements");
        docsElements.getAttributes().attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objectFactory.named(DocsType.class, "flix"));
        docsElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.JAVA_API));
        docsElements.getAttributes().attribute(Category.CATEGORY_ATTRIBUTE, objectFactory.named(Category.class, Category.DOCUMENTATION));
        docsElements.getAttributes().attribute(Bundling.BUNDLING_ATTRIBUTE, objectFactory.named(Bundling.class, Bundling.EXTERNAL));

        AdhocComponentWithVariants javaComponent = (AdhocComponentWithVariants) project.getComponents().getByName("java");
        javaComponent.addVariantsFromConfiguration(docsElements, details -> {
            details.mapToMavenScope("compile");
        });

        JavaPluginExtension javaPluginExtension = project.getExtensions().getByType(JavaPluginExtension.class);
        SourceSetContainer sourceSets = javaPluginExtension.getSourceSets();
        var main = sourceSets.getByName("main");
        var flixSource = (SourceDirectorySet) main.getExtensions().getByName("flix");
        var flixClasspath = project.getConfigurations().maybeCreate(FlixGradlePlugin.sourcedNameOf(main, FlixGradlePlugin.FLIX_CLASSPATH_CONFIGURATION_NAME));

        project.getTasks().register("documentFlix", FlixDocumentor.class, task -> {
            task.getFlixInput().from(flixClasspath);
            task.getSource().set(flixSource.getSourceDirectories());
            task.getDestinationDirectory().set(project.getLayout().getBuildDirectory().dir("flixdocs"));
        });
    }
    */
}
