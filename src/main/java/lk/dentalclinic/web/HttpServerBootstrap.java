package lk.dentalclinic.web;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Owns the JDK's built-in {@link HttpServer} - the whole of the presentation tier's
 * transport layer, with no servlet container and no framework.
 *
 * <p>{@code com.sun.net.httpserver} is a supported, exported API of the {@code jdk.httpserver}
 * module, not an internal one, so no {@code --add-exports} is required.
 *
 * <p>The server is given a bounded thread pool rather than the default single-threaded
 * executor, so that one slow database call cannot stall every other request.
 */
public final class HttpServerBootstrap implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(HttpServerBootstrap.class.getName());

    private final HttpServer server;
    private final ExecutorService executor;
    private final int port;

    private HttpServerBootstrap(HttpServer server, ExecutorService executor, int port) {
        this.server = server;
        this.executor = executor;
        this.port = port;
    }

    /**
     * Binds and starts the server.
     *
     * @param port      TCP port; pass {@code 0} to let the OS pick a free one (used by tests)
     * @param threads   size of the request thread pool
     * @param router    the front controller every request is dispatched through
     */
    public static HttpServerBootstrap start(int port, int threads, Router router) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "http-worker-" + counter.getAndIncrement());
                thread.setDaemon(false);
                return thread;
            }
        };

        ExecutorService executor = Executors.newFixedThreadPool(threads, factory);
        server.setExecutor(executor);
        server.createContext("/", router);
        server.start();

        int actualPort = server.getAddress().getPort();
        LOG.info(() -> "HTTP server listening on http://localhost:" + actualPort
                + " (" + threads + " worker threads, " + router.routeCount() + " routes)");

        return new HttpServerBootstrap(server, executor, actualPort);
    }

    /** The port actually bound - meaningful when {@code 0} was requested. */
    public int port() {
        return port;
    }

    public String baseUrl() {
        return "http://localhost:" + port;
    }

    @Override
    public void close() {
        LOG.info("Shutting down HTTP server");
        server.stop(2);
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
