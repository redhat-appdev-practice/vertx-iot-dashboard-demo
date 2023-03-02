package com.redhat.consulting.runtimes;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.SockJSBridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.netty.handler.codec.http.HttpHeaderValues.APPLICATION_JSON;

public class MainVerticle extends SharedDataVerticle {

  private static final Logger LOG = LoggerFactory.getLogger(MainVerticle.class);

  StaticHandler staticHandler;

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    LOG.atError().setMessage("MainVerticle Loading").log();
    var router = Router.router(vertx);
    router.route().handler(CorsHandler.create());
    router.get("/config/env.json").handler(this::returnConfig);

    SockJSHandler sockJsHandler = SockJSHandler.create(vertx);
    SockJSBridgeOptions bridgeOptions = new SockJSBridgeOptions();
    bridgeOptions.addOutboundPermitted(new PermittedOptions().setAddress("iot.motion.aggregate"));
    router.mountSubRouter("/eventbus", sockJsHandler.bridge(bridgeOptions));

    staticHandler = StaticHandler.create();
    staticHandler.setIndexPage("index.html");
    router.route().handler(staticHandler);

    // Handler of last resort... If no other path succeeds, this will always serve `index.html`
    router.route().handler(this::serveIndex);

    vertx.createHttpServer()
        .requestHandler(router)
        .listen(8080)
        .<Void>mapEmpty()
        .compose(this::initShared)
        .compose(this::storeSharedVariables)
        .compose(this::zeroValues)
        .<Void>mapEmpty()
        .compose(this::deployMqttServerVerticle)
        .compose(this::deployAggregatorVerticle)
        .<Void>mapEmpty()
        .onComplete(startPromise);
  }

  private void returnConfig(RoutingContext ctx) {
    var reqHost = ctx.request().absoluteURI();

    var predictedMqttHost = ctx.request().host().replace("vertx-iot-demo", "vertx-mqtt");
    var predictedWebsocketUri = reqHost.replaceAll("/config.*", "/eventbus");

    var mqttHost = System.getenv().getOrDefault("VERTX_MQTT_HOST", predictedMqttHost).split(":")[0];
    var mqttPort = 4321;
    try {
      mqttPort = Integer.parseInt(System.getenv().getOrDefault("VERTX_MQTT_PORT", "4321"));
    } catch (NumberFormatException nfe) {
      LOG.atWarn().setMessage("Value for 'VERTX_MQTT_PORT' of '{}' is not a valid integer.").addArgument(System.getenv().getOrDefault("VERTX_MQTT_PORT", "4321"));
    }
    var websocketUri = System.getenv().getOrDefault("VERTX_WEBSOCKET_URI", predictedWebsocketUri);

    var mqtt = new JsonObject()
                   .put("host", mqttHost)
                   .put("port", mqttPort);
    var websocket = new JsonObject()
                        .put("uri", websocketUri);
    var config = new JsonObject()
                     .put("mqtt", mqtt)
                     .put("websocket", websocket);
    ctx.response()
        .putHeader("Content-Type", APPLICATION_JSON)
        .end(config.encodePrettily());
  }

  private Future<String> deployAggregatorVerticle(String s) {
    return vertx.deployVerticle(new AggregatorVerticle());
  }

  private Future<String> deployMqttServerVerticle(Void _v) {
    return vertx.deployVerticle(new MqttServerVerticle());
  }


  private void serveIndex(RoutingContext ctx) {
    ctx.response().sendFile("webroot/index.html");
    ctx.next();
  }
}
