package dev.lukebemish.flix.dependencies;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.protocols.ssl.UndertowXnioSsl;
import io.undertow.server.RoutingHandler;
import io.undertow.server.handlers.proxy.LoadBalancingProxyClient;
import io.undertow.server.handlers.proxy.ProxyClient;
import io.undertow.server.handlers.proxy.ProxyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.OptionMap;
import org.xnio.Xnio;

import java.io.Closeable;
import java.net.BindException;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public final class FpkgRepositoryLayer {
    static final Logger LOGGER = LoggerFactory.getLogger(FpkgRepositoryLayer.class);

    private static final AtomicInteger PORT_INCREMENT = new AtomicInteger(0);

    static final String PROXY_PORT_ENV_NAME = "FLIX_GRADLE_PROXY_PORT";
    static final String PROXY_PORT_PROP_NAME = "dev.lukebemish.flix.proxy.port";

    public static final String ARTIFACT_TEMPLATE = "/io/github/{user}/{repository}/{version}/{file}";
    private static final String GITHUB_URI = "https://github.com/";

    private static int getProxyPort() {
        return Optional.ofNullable(System.getenv(PROXY_PORT_ENV_NAME))
                .or(() -> Optional.ofNullable(System.getProperty(PROXY_PORT_PROP_NAME)))
                .map(Integer::valueOf)
                .orElse(7348);
    }

    public static void main(String[] args) {
        Object lock = new Object();
        Thread thread = new Thread(() -> {
            synchronized (lock) {
                try (LayerServer server = startServer()) {
                    System.out.println("Listening on: " + server.uri());
                    lock.wait();
                } catch (InterruptedException e) {
                    System.out.println("Shutting down...");
                }
            }
        });
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException ignored) {
            thread.interrupt();
        }
    }

    public static LayerServer startServer() {
        Xnio instance = Xnio.getInstance();
        ProxyClient fallback;
        try {
            fallback = new LoadBalancingProxyClient()
                    .addHost(URI.create(GITHUB_URI), new UndertowXnioSsl(instance, OptionMap.EMPTY))
                    .setConnectionsPerThread(20);
        } catch (NoSuchProviderException | NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException(e);
        }

        ProxyHandler proxyHandler = ProxyHandler.builder()
                .setProxyClient(fallback)
                .setMaxRequestTime(30000)
                .setRewriteHostHeader(true)
                .build();

        var artifactHandler = new ArtifactHandler();

        RoutingHandler routingHandler = Handlers.routing()
                .add(
                        "HEAD",
                    ARTIFACT_TEMPLATE,
                        artifactHandler
                )
                .add(
                        "GET",
                    ARTIFACT_TEMPLATE,
                        artifactHandler
                ).setFallbackHandler(exchange -> {
                    exchange.setStatusCode(404);
                });

        while (true) {
            try {
                int proxyPort = getProxyPort() + PORT_INCREMENT.getAndIncrement();
                return startOnPort(proxyPort, routingHandler);
            } catch (RuntimeException e) {
                if (e.getCause() != null && e.getCause() instanceof BindException) {
                    continue;
                }
                throw e;
            }
        }
    }

    private static LayerServer startOnPort(int port, RoutingHandler routingHandler) {
        Undertow server = Undertow.builder()
                .addHttpListener(port, "localhost")
                .setHandler(routingHandler)
                .setIoThreads(4)
                .build();

        server.start();

        return new LayerServer() {
            @Override
            public URI uri() {
                return URI.create("http://localhost:" + port);
            }

            @Override
            public void close() {
                server.stop();
            }
        };

    }

    public interface LayerServer extends Closeable {
        URI uri();

        void close();
    }

    private FpkgRepositoryLayer() {}
}
