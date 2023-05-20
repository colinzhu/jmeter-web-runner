package colinzhu.jmeterwebrunner;

import colinzhu.webconsole.WebConsole;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

import java.util.concurrent.TimeUnit;

public class App {
    public static void main(String[] args) {
        WebConsole webConsole = new WebConsole(MainTask::main);

        VertxOptions vertxOptions = new VertxOptions().setMaxWorkerExecuteTime(TimeUnit.MINUTES.toNanos(10L));
        Vertx.vertx(vertxOptions).deployVerticle(webConsole);

    }
}
