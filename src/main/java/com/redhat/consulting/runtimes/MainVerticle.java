package com.redhat.consulting.runtimes;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MainVerticle extends AbstractVerticle {

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    vertx.setPeriodic(5000, this::wrapSensorPoll);

    startPromise.complete();
  }

  private void wrapSensorPoll(Long aLong) {
    vertx.executeBlocking(this::pollSensors);
  }

  private <T> void pollSensors(Promise<T> tPromise) {
    System.out.println("HERE");
    try {
      Process p = Runtime.getRuntime().exec("sensors -j");
      BufferedReader stdOutput = new BufferedReader(new InputStreamReader(p.getInputStream()));
      StringBuilder sb = new StringBuilder();
      String s = null;
      while ((s = stdOutput.readLine()) != null) {
        sb.append(s);
      }
      JsonObject sensorData = new JsonObject(sb.toString());
      sensorData.stream()
        .filter(chip -> chip.getValue() instanceof JsonObject)
        .map(chip -> (JsonObject) chip.getValue())
        .flatMap(JsonObject::stream)
        .filter(device -> device.getValue() instanceof JsonObject)
        .map(this::mapDevice)
        .flatMap(JsonObject::stream)
        .filter(sensor -> sensor.getKey().contains("temp"))
        .forEach(value -> System.out.printf("%s = %f\n", value.getKey(), value.getValue()));
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }

  private JsonObject mapDevice(Map.Entry<String, Object> entry) {
    JsonObject jsonEntry = new JsonObject(((JsonObject) entry.getValue()).encode());
    Set<String> fieldNames = jsonEntry.fieldNames().stream().map(k -> String.format("%s", k)).collect(Collectors.toSet());
    for (String key: fieldNames) {
      Object value = jsonEntry.getValue(key);
      jsonEntry.remove(key);
      jsonEntry.put(String.format("%s-%s", entry.getKey(), key), value);
    }
    return jsonEntry;
  }

}
