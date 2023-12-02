package dev.lukebemish.flix.gradle.dependencies;

import com.moandjiezana.toml.Toml;
import groovy.json.JsonOutput;
import groovy.json.StringEscapeUtils;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import io.undertow.util.PathTemplateMatch;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

public class ArtifactHandler implements HttpHandler {

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        Map<String, String> parameters = exchange.getAttachment(PathTemplateMatch.ATTACHMENT_KEY).getParameters();
        String user = parameters.get("user");
        String repository = parameters.get("repository");
        String version = parameters.get("version");
        String artifact = parameters.get("file");
        if (artifact.equals(repository+"-"+version+".module")) {
            String flixToml = "https://github.com/" + user + "/" + repository + "/releases/download/v" + version + "/flix.toml";
            URL url = new URL(flixToml);
            if (exchange.getRequestMethod().equals(Methods.HEAD)) {
                var connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("HEAD");
                if (connection.getResponseCode() == 200) {
                    exchange.setStatusCode(200);
                    return;
                }
            } else if (exchange.getRequestMethod().equals(Methods.GET)) {
                try (var stream = new BufferedInputStream(url.openStream())) {
                    var toml = new Toml().read(stream);
                    Map<String, Object> moduleMetadata = makeModuleMetadata(user, repository, version, toml);
                    var body = JsonOutput.prettyPrint(JsonOutput.toJson(moduleMetadata));
                    exchange.setStatusCode(200);
                    exchange.setResponseContentLength(body.length());
                    exchange.getResponseSender().send(body);
                    return;
                } catch (IOException ignored) {}
            }
        }
        String out = "https://github.com/"+user+"/"+repository+"/releases/download/v"+version+"/"+artifact;
        exchange.getResponseHeaders().put(Headers.LOCATION, out);
        exchange.setStatusCode(302);
    }

    private static Map<String, Object> makeModuleMetadata(String user, String repository, String version, Toml toml) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("formatVersion", "1.1");
        metadata.put("component", Map.of(
                "group", "github/"+user,
                "module", repository,
                "version", version
        ));
        var flixVersion = toml.getString("flix");
        Toml dependencies = toml.getTable("dependencies");
        if (dependencies == null) dependencies = new Toml();
        Toml mvnDependencies = toml.getTable("mvn-dependencies");
        if (mvnDependencies == null) mvnDependencies = new Toml();

        Map<String, Object> flixVariant = new LinkedHashMap<>();
        flixVariant.put("name", "flixElements");
        flixVariant.put("attributes", Map.of(
                "org.gradle.category", "library",
                "org.gradle.dependency.bundling", "external",
                "org.gradle.jvm.version", 11,
                "org.gradle.libraryelements", "fpkg",
                "org.gradle.usage", "java-api"
        ));
        flixVariant.put("files", List.of(Map.of(
                "name", repository+".fpkg",
                "url", repository+".fpkg"
        )));
        List<Object> dependencyList = new ArrayList<>();
        for (var entry : dependencies.entrySet()) {
            Map<String, Object> dep = new LinkedHashMap<>();
            String key = entry.getKey();
            if (key.startsWith("\"") && key.endsWith("\"")) {
                key = StringEscapeUtils.unescapeJava(key.substring(1, key.length()-1));
            }
            if (key.startsWith("github:")) {
                var rest = key.substring(7);
                var parts = rest.split("/");
                dep.put("group", "github/"+parts[0]);
                dep.put("module", parts[1]);
                dep.put("version", Map.of(
                        "requires", entry.getValue()
                ));
            } else {
                throw new RuntimeException("Unknown dependency type "+key);
            }
            dependencyList.add(dep);
        }
        for (var entry : mvnDependencies.entrySet()) {
            Map<String, Object> dep = new LinkedHashMap<>();
            String key = entry.getKey();
            if (key.startsWith("\"") && key.endsWith("\"")) {
                key = StringEscapeUtils.unescapeJava(key.substring(1, key.length()-1));
            }
            var parts = key.split(":");
            dep.put("group", parts[0]);
            dep.put("module", parts[1]);
            dep.put("version", Map.of(
                    "requires", entry.getValue()
            ));
            dependencyList.add(dep);
        }
        if (flixVersion != null) {
            dependencyList.add(Map.of(
                    "group", "dev.flix",
                    "module", "flix",
                    "version", Map.of(
                            "requires", flixVersion
                    )
            ));
        }
        flixVariant.put("dependencies", dependencyList);
        Map<String, Object> sourceVariant = new LinkedHashMap<>();
        sourceVariant.put("name", "sourceElements");
        sourceVariant.put("attributes", Map.of(
            "org.gradle.category", "documentation",
            "org.gradle.dependency.bundling", "external",
            "org.gradle.docstype", "sources",
            "org.gradle.usage", "java-runtime"
        ));
        sourceVariant.put("files", List.of(Map.of(
            "name", "v"+version+".zip",
            "url", "https://github.com/"+user+"/"+repository+"/archive/v"+version+".zip"
        )));
        metadata.put("variants", List.of(flixVariant, sourceVariant));
        return metadata;
    }
}
