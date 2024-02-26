package dev.lukebemish.flix.gradle;

import dev.lukebemish.flix.gradle.task.FlixToml;
import dev.lukebemish.flix.gradle.task.Fpkg;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.attributes.java.TargetJvmEnvironment;
import org.gradle.api.attributes.java.TargetJvmVersion;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.flow.FlowProviders;
import org.gradle.api.flow.FlowScope;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.BasePluginExtension;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.util.Set;
import java.util.stream.Stream;

@SuppressWarnings("UnstableApiUsage")
public abstract class FlixGradlePlugin implements Plugin<Project> {
    private ObjectFactory objectFactory;

    public static final String FPKG_ELEMENT = "fpkg";
    public static final String FLIX_CLASSES_ELEMENT = "flix-classes";

    @Inject
    protected abstract FlowScope getFlowScope();

    @Inject
    protected abstract FlowProviders getFlowProviders();

    @Override
    public void apply(@NotNull Project project) {
        this.objectFactory = project.getObjects();
        project.getPluginManager().apply(JavaPlugin.class);
        var flixExtension = project.getExtensions().create("flix", FlixGradleExtension.class, project);

        Provider<RepositoryLayer> repositoryLayer = project.getGradle().getSharedServices().registerIfAbsent("flixRepositoryLayer", RepositoryLayer.class, spec -> {});

        getFlowScope().always(
            ResolutionSetup.class,
            spec ->
                spec.getParameters().getShouldClose().set(getFlowProviders().getBuildWorkResult().map(result -> true))
        );

        project.getRepositories().ivy(ivy -> {
            ivy.setUrl("https://github.com/flix/flix/releases/download/");
            ivy.setName("Flix Releases Repository");
            ivy.patternLayout(p -> p.artifact("v[revision]/[artifact].[ext]"));
            ivy.metadataSources(IvyArtifactRepository.MetadataSources::artifact);
            ivy.content(content -> {
                content.includeModule("dev.flix", "flix");
                content.onlyForAttribute(
                    Usage.USAGE_ATTRIBUTE,
                    project.getObjects().named(Usage.class, "java-api"),
                    project.getObjects().named(Usage.class, "java-runtime")
                );
            });
        });

        project.getRepositories().ivy(ivy -> {
            ivy.setUrl(repositoryLayer.get().url());
            ivy.setName("flix.toml parsing repository");
            ivy.metadataSources(IvyArtifactRepository.MetadataSources::gradleMetadata);
            ivy.setAllowInsecureProtocol(true);
            ivy.content(content -> content.includeGroup("github"));
        });

        project.getDependencies().attributesSchema(schema -> {
            var matching = schema.getMatchingStrategy(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE);
            matching.getCompatibilityRules().add(FpkgCompatabilityRule.class);
            matching.getDisambiguationRules().add(FpkgCompatabilityRule.Disambiguation.class);
        });

        JavaPluginExtension javaPluginExtension = project.getExtensions().getByType(JavaPluginExtension.class);
        SourceSetContainer sourceSets = javaPluginExtension.getSourceSets();

        sourceSets.all(sourceSet -> {
            var flixSource = getFlixSourceDirectorySet(sourceSet);
            sourceSet.getExtensions().add(SourceDirectorySet.class, "flix", flixSource);
            sourceSet.getAllJava().source(flixSource);
            sourceSet.getAllSource().source(flixSource);
            flixSource.srcDir("src/" + sourceSet.getName() + "/flix");

            ConfigurationContainer configurations = project.getConfigurations();

            var flixClasspath = configurations.maybeCreate(sourcedNameOf(sourceSet, FLIX_CLASSPATH_CONFIGURATION_NAME));

            flixClasspath.attributes((attrs) -> {
                attrs.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objectFactory.named(LibraryElements.class, FLIX_CLASSES_ELEMENT));
                attrs.attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.JAVA_API));
                attrs.attribute(Category.CATEGORY_ATTRIBUTE, objectFactory.named(Category.class, Category.LIBRARY));
                attrs.attribute(Bundling.BUNDLING_ATTRIBUTE, objectFactory.named(Bundling.class, Bundling.EXTERNAL));
                attrs.attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, objectFactory.named(TargetJvmEnvironment.class, TargetJvmEnvironment.STANDARD_JVM));
                attrs.attributeProvider(
                    TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE,
                    project.provider(() -> Integer.valueOf(javaPluginExtension.getSourceCompatibility().getMajorVersion()))
                );
            });

            var flix = configurations.maybeCreate(sourcedNameOf(sourceSet, "flix"));

            flixClasspath.extendsFrom(configurations.getByName(sourceSet.getCompileClasspathConfigurationName()));
            flixClasspath.extendsFrom(flix);
            flixClasspath.setCanBeResolved(true);
            flixClasspath.setCanBeDeclared(false);
        });

        BasePluginExtension basePluginExtension = project.getExtensions().getByType(BasePluginExtension.class);

        AdhocComponentWithVariants javaComponent = (AdhocComponentWithVariants) project.getComponents().getByName("java");

        project.getComponents().named("java", component -> {
            Configuration runtimeElements = project.getConfigurations().getByName(JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME);
            Configuration apiElements = project.getConfigurations().getByName(JavaPlugin.API_ELEMENTS_CONFIGURATION_NAME);
            var adhocComponent = (AdhocComponentWithVariants) component;
            adhocComponent.withVariantsFromConfiguration(runtimeElements, details -> {
                if (flixExtension.getSkipRuntimeElements().get()) {
                    details.skip();
                }
            });
            adhocComponent.withVariantsFromConfiguration(apiElements, details -> {
                if (flixExtension.getSkipApiElements().get()) {
                    details.skip();
                }
            });
        });

        SourceSet main = sourceSets.getByName("main");

        Configuration fpkgElements = project.getConfigurations().maybeCreate("fpkgElements");
        fpkgElements.setCanBeResolved(false);
        fpkgElements.setCanBeConsumed(true);
        fpkgElements.extendsFrom(project.getConfigurations().getByName(sourcedNameOf(main, FLIX_CLASSPATH_CONFIGURATION_NAME)));
        fpkgElements.getAttributes().attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objectFactory.named(LibraryElements.class, FPKG_ELEMENT));
        fpkgElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.JAVA_API));
        fpkgElements.getAttributes().attribute(Category.CATEGORY_ATTRIBUTE, objectFactory.named(Category.class, Category.LIBRARY));
        fpkgElements.getAttributes().attribute(Bundling.BUNDLING_ATTRIBUTE, objectFactory.named(Bundling.class, Bundling.EXTERNAL));

        javaComponent.addVariantsFromConfiguration(fpkgElements, details -> details.mapToMavenScope("compile"));

        var flixClasspath = project.getConfigurations().getByName(sourcedNameOf(main, FLIX_CLASSPATH_CONFIGURATION_NAME));

        var flixTomlTask = project.getTasks().register("flixToml", FlixToml.class, flixToml -> {
            var artifacts = project.provider(() -> flixClasspath.getResolvedConfiguration().getFirstLevelModuleDependencies().stream().toList());
            flixToml.getDependencyArtifactIds().set(artifacts.map(it -> it.stream().map(i -> i.getModule().getId()).toList()));
            flixToml.getDependencyArtifactsAreFpkg().set(artifacts.map(it -> it.stream().map(i -> i.getModuleArtifacts().stream().anyMatch(ar -> ar.getFile().getName().endsWith(".fpkg"))).toList()));
            flixToml.dependsOn(flixClasspath);
            flixToml.getPackageName().convention(flixExtension.getFlixProjectName());
            flixToml.getDestinationFile().set(basePluginExtension.getLibsDirectory().file("flix.toml"));
        });

        var fpkgTask = project.getTasks().register("fpkg", Fpkg.class, fpkg -> {
            SourceDirectorySet flixSourceDirectorySet = (SourceDirectorySet) main.getExtensions().getByName("flix");
            fpkg.dependsOn(flixSourceDirectorySet);
            fpkg.dependsOn(flixTomlTask);
            fpkg.into("src", spec -> spec.from(flixSourceDirectorySet.getSourceDirectories()));
            fpkg.from(flixTomlTask.get().getDestinationFile());
            fpkg.setGroup("Build");
            fpkg.setDescription("Builds a Flix package");
            fpkg.getArchiveFileName().set(flixExtension.getFlixProjectName().map(name -> name + ".fpkg"));
            fpkg.getDestinationDirectory().set(basePluginExtension.getLibsDirectory());
        });
        project.getTasks().getByName("assemble").dependsOn(fpkgTask);

        project.getArtifacts().add(fpkgElements.getName(), fpkgTask, artifact -> {
            artifact.setExtension("fpkg");
            artifact.setType("fpkg");
        });
    }

    public static final String FLIX_CLASSPATH_CONFIGURATION_NAME = "flixClasspath";

    static String sourcedNameOf(SourceSet sourceSet, String baseName) {
        var sourceSetBaseName = sourceSet.getName().equals("main") ? "" : Util.wordsToCamelCase(sourceSet.getName());
        return StringUtils.uncapitalize(sourceSetBaseName + StringUtils.capitalize(baseName));
    }

    static Stream<Dependency> processDependencies(Project project, Set<ResolvedDependency> deps) {
        return deps.stream().flatMap(dep -> {
            var artifacts = dep.getModuleArtifacts();
            Stream<Dependency> children = processDependencies(project, dep.getChildren());
            if (!artifacts.stream().allMatch(artifact -> "fpkg".equals(artifact.getExtension()))) {
                var outDep = project.getDependencies().create(dep.getModule().toString());
                return Stream.concat(Stream.of(outDep), children);
            }
            return children;
        });
    }

    private SourceDirectorySet getFlixSourceDirectorySet(SourceSet sourceSet) {
        var sourceDirectorySet = objectFactory.sourceDirectorySet("flix", sourceSet.getName() + " Flix source");
        sourceDirectorySet.getFilter().include("**/*.flix");
        return sourceDirectorySet;
    }
}
