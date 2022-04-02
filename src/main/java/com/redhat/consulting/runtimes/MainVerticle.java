package com.redhat.consulting.runtimes;

import io.vertx.core.AbstractVerticle;
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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class MainVerticle extends AbstractVerticle {

  private static final Logger LOG = LoggerFactory.getLogger(MainVerticle.class);

  StaticHandler staticHandler;

  long periodic;

  long lastUpdate;

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    periodic = vertx.setPeriodic(1000, this::wrapSensorPoll);

    var router = Router.router(vertx);
//    router.route().handler(CorsHandler.create(".*.").allowCredentials(true));

    SockJSHandler sockJsHandler = SockJSHandler.create(vertx);
    SockJSBridgeOptions bridgeOptions = new SockJSBridgeOptions();
    bridgeOptions.addOutboundPermitted(new PermittedOptions().setAddress("iot.temp.reading"));
    router.mountSubRouter("/eventbus", sockJsHandler.bridge(bridgeOptions));

    staticHandler = StaticHandler.create();
    router.route().handler(staticHandler);

    // Handler of last resort... If no other path succeeds, this will always serve `index.html`
    router.route().handler(this::serveIndex);

    vertx.createHttpServer()
         .requestHandler(router)
         .listen(8080)
         .<Void>mapEmpty()
         .onComplete(startPromise);
  }

  private void serveIndex(RoutingContext ctx) {
    ctx.response().sendFile("webroot/index.html");
    ctx.next();
  }

  private void wrapSensorPoll(Long aLong) {
    vertx.executeBlocking(this::pollSensors);
  }

  private <T> void pollSensors(Promise<T> tPromise) {
    LOG.info("Polling Sensors");
    try {
      Process p = Runtime.getRuntime().exec("sensors -j -A coretemp-isa-0000");
      BufferedReader stdOutput = new BufferedReader(new InputStreamReader(p.getInputStream()));
      StringBuilder sb = new StringBuilder();
      String s = null;
      while ((s = stdOutput.readLine()) != null) {
        sb.append(s);
      }
      lastUpdate = ZonedDateTime.now(ZoneOffset.UTC).toEpochSecond();
      JsonObject sensorData = new JsonObject(sb.toString());
      sensorData.stream()
        .filter(chip -> chip.getValue() instanceof JsonObject)
        .map(chip -> (JsonObject) chip.getValue())
        .flatMap(JsonObject::stream)
        .filter(device -> device.getValue() instanceof JsonObject)
        .map(this::mapDevice)
        .flatMap(JsonObject::stream)
        .filter(sensor -> sensor.getKey().contains("Core"))
        .filter(sensor -> sensor.getKey().contains("input"))
        .forEach(this::sendTempUpdate);
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }

  private void sendTempUpdate(Map.Entry<String, Object> reading) {
    if (reading.getValue() instanceof Double) {
      JsonObject body = new JsonObject()
                                .put("name", reading.getKey())
                                .put("value", reading.getValue())
                                .put("timestamp", lastUpdate);
      LOG.info("Sending: {} - {} - {}", lastUpdate, reading.getKey(), reading.getValue());
      vertx.eventBus().send("iot.temp.reading", body);
    }
  }

  private JsonObject mapDevice(Map.Entry<String, Object> entry) {
    JsonObject jsonEntry = new JsonObject(((JsonObject) entry.getValue()).encode());
    Set<String> fieldNames = jsonEntry.fieldNames().stream().map(k -> String.format("%s", k)).collect(Collectors.toSet());
    for (String key: fieldNames) {
      Object value = jsonEntry.getValue(key);
      jsonEntry.remove(key);
      jsonEntry.put(String.format("%s:%s", entry.getKey(), key), value);
    }
    return jsonEntry;
  }

  @Override
  public void stop() throws Exception {
    vertx.cancelTimer(periodic);
  }
}
