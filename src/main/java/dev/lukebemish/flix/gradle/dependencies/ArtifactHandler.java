package dev.lukebemish.flix.gradle.dependencies;

import com.moandjiezana.toml.Toml;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import groovy.json.JsonOutput;
import groovy.json.StringEscapeUtils;
import org.jetbrains.annotations.ApiStatus;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApiStatus.Internal
public class ArtifactHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath().substring(FpkgRepositoryLayer.HANDLER_PREFIX.length());
        String[] parts = path.split("/");
        if (parts.length != 5 || !parts[0].equals(parts[3])) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        String user = parts[0];
        String repository = parts[1];
        String version = parts[2];
        String artifact = parts[4];
        if (artifact.equals(repository+"-"+version+".module")) {
            String flixToml = "https://github.com/" + user + "/" + repository + "/releases/download/v" + version + "/flix.toml";
            URL url = new URL(flixToml);
            if (exchange.getRequestMethod().equals("HEAD")) {
                var connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("HEAD");
                if (connection.getResponseCode() == 200) {
                    exchange.sendResponseHeaders(200, -1);
                    exchange.close();
                    return;
                }
            } else if (exchange.getRequestMethod().equals("GET")) {
                try (var stream = new BufferedInputStream(url.openStream())) {
                    var toml = new Toml().read(stream);
                    Map<String, Object> moduleMetadata = makeModuleMetadata(user, repository, version, toml);
                    var body = JsonOutput.prettyPrint(JsonOutput.toJson(moduleMetadata));
                    exchange.sendResponseHeaders(200, 0);
                    exchange.getResponseBody().write(body.getBytes());
                    exchange.close();
                    return;
                } catch (IOException ignored) {}
            } else {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }
        }
        String out = "https://github.com/"+user+"/"+repository+"/releases/download/v"+version+"/"+artifact;
        exchange.getResponseHeaders().put("Location", List.of(out));
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private static Map<String, Object> makeModuleMetadata(String user, String repository, String version, Toml toml) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("formatVersion", "1.1");
        metadata.put("component", Map.of(
                "group", "github",
                "module", user+"/"+repository,
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
                "name", user+"/"+repository+".fpkg",
                "url", user+"/"+repository+".fpkg"
        )));
        List<Object> dependencyList = new ArrayList<>();
        for (var entry : dependencies.entrySet()) {
            Map<String, Object> dep = new LinkedHashMap<>();
            String key = entry.getKey();
            if (key.startsWith("\"") && key.endsWith("\"")) {
                key = StringEscapeUtils.unescapeJava(key.substring(1, key.length()-1));
            }
            if (key.startsWith("github")) {
                var rest = key.substring(7);
                dep.put("group", "github");
                dep.put("module", rest);
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
        metadata.put("variants", List.of(flixVariant));
        return metadata;
    }
}
