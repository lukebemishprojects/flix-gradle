package dev.lukebemish.flix.gradle.dependencies;

import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public final class FpkgRepositoryLayer {
    static final Logger LOGGER = LoggerFactory.getLogger(FpkgRepositoryLayer.class);

    private static final AtomicInteger PORT_INCREMENT = new AtomicInteger(0);

    static final String PROXY_PORT_ENV_NAME = "FLIX_GRADLE_PROXY_PORT";
    static final String PROXY_PORT_PROP_NAME = "dev.lukebemish.flix.proxy.port";

    public static final String HANDLER_PREFIX = "/github/";

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
                    LOGGER.info("Shutting down...");
                } catch (IOException e) {
                    LOGGER.error("Failed to start server", e);
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

    public static LayerServer startServer() throws IOException {
        var httpServer = HttpServer.create();
        httpServer.createContext(HANDLER_PREFIX, new ArtifactHandler());

        while (true) {
            try {
                int proxyPort = getProxyPort() + PORT_INCREMENT.getAndIncrement();
                return startOnPort(proxyPort, httpServer);
            } catch (BindException ignored) {}
        }
    }

    private static LayerServer startOnPort(int port, HttpServer httpServer) throws IOException {
        httpServer.bind(new InetSocketAddress(port), 0);
        httpServer.start();

        return new LayerServer() {
            @Override
            public URI uri() {
                return URI.create("http://localhost:" + port);
            }

            @Override
            public void close() {
                httpServer.stop(2);
            }
        };

    }

    public interface LayerServer extends Closeable {
        URI uri();

        void close();
    }

    private FpkgRepositoryLayer() {}
}
