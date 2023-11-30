package dev.lukebemish.flix;

import dev.lukebemish.flix.task.FlixCompile;
import dev.lukebemish.flix.task.Fpkg;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.attributes.java.TargetJvmEnvironment;
import org.gradle.api.attributes.java.TargetJvmVersion;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.BasePluginExtension;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("UnstableApiUsage")
public class FlixGradlePlugin implements Plugin<Project> {
    private ObjectFactory objectFactory;

    public static final String FPKG_ELEMENT = "fpkg";
    public static final String FLIX_CLASSES_ELEMENT = "flix-classes";

    @Override
    public void apply(@NotNull Project project) {
        this.objectFactory = project.getObjects();

        Provider<RepositoryLayer> repositoryLayer = project.getGradle().getSharedServices().registerIfAbsent("flixRepositoryLayer", RepositoryLayer.class, spec -> {});

        project.getPlugins().apply(JavaPlugin.class);

        project.getRepositories().ivy(ivy -> {
            ivy.setUrl("https://github.com/flix/flix/releases/download/");
            ivy.setName("Flix Releases Repository");
            ivy.patternLayout(p -> {
                p.artifact("v[revision]/[artifact].[ext]");
            });
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

        project.getRepositories().maven(maven -> {
            maven.setUrl(repositoryLayer.get().url());
            maven.setName("flix.toml parsing repository");
            maven.metadataSources(MavenArtifactRepository.MetadataSources::gradleMetadata);
            maven.setAllowInsecureProtocol(true);
            maven.content(content -> {
                content.includeGroupAndSubgroups("io.github");
            });
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

            flixClasspath.extendsFrom(configurations.getByName(sourceSet.getCompileClasspathConfigurationName()));
            flixClasspath.extendsFrom(configurations.maybeCreate(sourcedNameOf(sourceSet, "flix")));
            flixClasspath.setCanBeResolved(true);
            flixClasspath.setCanBeDeclared(false);

            flixSource.getDestinationDirectory().set(project.getLayout().getBuildDirectory().dir("classes/flix/" + sourceSet.getName()));

            ((ConfigurableFileCollection) sourceSet.getOutput().getClassesDirs()).from(flixSource.getDestinationDirectory());

            var flixCompile = project.getTasks().register(sourcedNameOf(sourceSet, "compileFlix"), FlixCompile.class, task -> {
                task.getClasspath().from(configurations.getByName(sourceSet.getCompileClasspathConfigurationName()));
                task.getFlixInput().from(flixClasspath);
                task.getSource().set(flixSource.getSourceDirectories());
            });

            flixSource.compiledBy(flixCompile, FlixCompile::getDestinationDirectory);
        });

        BasePluginExtension basePluginExtension = project.getExtensions().getByType(BasePluginExtension.class);

        AdhocComponentWithVariants javaComponent = (AdhocComponentWithVariants) project.getComponents().getByName("java");
        SourceSet main = sourceSets.getByName("main");

        Configuration fpkgElements = project.getConfigurations().maybeCreate("fpkgElements");
        fpkgElements.setCanBeResolved(false);
        fpkgElements.setCanBeConsumed(true);
        fpkgElements.extendsFrom(project.getConfigurations().getByName(sourcedNameOf(main, FLIX_CLASSPATH_CONFIGURATION_NAME)));
        fpkgElements.getAttributes().attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objectFactory.named(LibraryElements.class, FPKG_ELEMENT));
        fpkgElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.JAVA_API));
        fpkgElements.getAttributes().attribute(Category.CATEGORY_ATTRIBUTE, objectFactory.named(Category.class, Category.LIBRARY));
        fpkgElements.getAttributes().attribute(Bundling.BUNDLING_ATTRIBUTE, objectFactory.named(Bundling.class, Bundling.EXTERNAL));

        javaComponent.addVariantsFromConfiguration(fpkgElements, details -> {
            details.mapToMavenScope("compile");
        });

        var fpkgTask = project.getTasks().register("fpkg", Fpkg.class, fpkg -> {
            SourceDirectorySet flixSourceDirectorySet = (SourceDirectorySet) main.getExtensions().getByName("flix");
            fpkg.dependsOn(flixSourceDirectorySet);
            fpkg.into("src", spec -> {
                spec.from(flixSourceDirectorySet.getSourceDirectories());
            });
            fpkg.setGroup("Build");
            fpkg.setDescription("Builds a Flix package");
            //fpkg.getArchiveBaseName().set(basePluginExtension.getArchivesName());
            //fpkg.getArchiveVersion().set(project.getVersion().toString());
            fpkg.getDestinationDirectory().set(basePluginExtension.getLibsDirectory());
        });
        project.getTasks().getByName("assemble").dependsOn(fpkgTask);

        project.getArtifacts().add(fpkgElements.getName(), fpkgTask, artifact -> {
            artifact.setExtension("fpkg");
            artifact.setType("fpkg");
        });
    }

    public static final String FLIX_CLASSPATH_CONFIGURATION_NAME = "flixClasspath";

    private static String sourcedNameOf(SourceSet sourceSet, String baseName) {
        var sourceSetBaseName = sourceSet.getName().equals("main") ? "" : Util.wordsToCamelCase(sourceSet.getName());
        return StringUtils.uncapitalize(sourceSetBaseName + StringUtils.capitalize(baseName));
    }

    private SourceDirectorySet getFlixSourceDirectorySet(SourceSet sourceSet) {
        var sourceDirectorySet = objectFactory.sourceDirectorySet("flix", sourceSet.getName() + " Flix source");
        sourceDirectorySet.getFilter().include("**/*.flix");
        return sourceDirectorySet;
    }
}
