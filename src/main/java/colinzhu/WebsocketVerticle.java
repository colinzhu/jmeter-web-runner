package colinzhu;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;
import lombok.extern.slf4j.Slf4j;

import java.io.OutputStream;
import java.io.PrintStream;

@Slf4j
public class WebsocketVerticle extends AbstractVerticle {
    public static void main(String[] args) {
        Vertx.vertx().deployVerticle(WebsocketVerticle.class.getName());
    }
    @Override
    public void start() {

        System.setOut(new PrintStream(new OutputStream() {
            private StringBuilder sb = new StringBuilder();

            @Override
            public void write(int b) {
                if (b == '\n') {
                    vertx.eventBus().publish("console.log", sb.toString());
                    sb.setLength(0);
                } else {
                    sb.append((char) b);
                }
            }
        }));

        HttpServer server = vertx.createHttpServer();
        server.webSocketHandler(this::handleWebSocket);

        Router router = Router.router(vertx);
        router.route().handler(StaticHandler.create("src/main/webroot"));
        server.requestHandler(router);

//        vertx.setPeriodic(1000, id -> log.info("test " + System.currentTimeMillis()));
        server.listen(8080).onSuccess(httpServer -> log.info("server started at 8080"));
    }

    private void handleWebSocket(ServerWebSocket webSocket) {
        webSocket.writeTextMessage("Welcome to the jmeter web runner!");
        vertx.eventBus().consumer("console.log", message -> {
            webSocket.writeTextMessage((String) message.body()); // redirect the message to websocket (web)
        });
        webSocket.textMessageHandler(this::handleMessage);
    }

    private void handleMessage(String msg) {
        log.info("Message received: {}", msg);
        vertx.executeBlocking(promise -> {
            try {
                Main.main(null);
            } catch (Exception e) {
                promise.fail(e);
            }
            promise.complete();
        });
    }
}