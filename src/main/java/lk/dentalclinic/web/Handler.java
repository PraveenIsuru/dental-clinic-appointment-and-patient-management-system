package lk.dentalclinic.web;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;

/**
 * A single request handler. Deliberately narrower than {@link com.sun.net.httpserver.HttpHandler}
 * so that handlers are addressed by the {@link Router} rather than bound to a server context.
 */
@FunctionalInterface
public interface Handler {

    void handle(HttpExchange exchange) throws IOException;
}
