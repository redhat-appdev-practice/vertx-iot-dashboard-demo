package com.redhat.consulting.runtimes;

import io.vertx.core.Launcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Stream;

public class CustomLauncher extends Launcher {

  public static void main(String[] args) {
    System.setProperty("vertx.setClassPathResolvingEnabled", "true");
    System.setProperty("vertx.setFileCachingEnabled", "true");
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");
    System.setProperty("vertx.logger-delegate-factory-class-name", "io.vertx.core.logging.SLF4JLogDelegateFactory");
    
    if (System.getenv().containsKey("KUBERNETES_NAMESPACE")) {
      System.setProperty("java.net.preferIPv4Stack", "true");
      System.setProperty("vertx.jgroups.config", "default-configs/default-jgroups-kubernetes.xml");
      System.setProperty("jgroups.dns.query", System.getenv().getOrDefault("jgroups.dns.query", "vertx-iot-demp"));
    }

    var argAppend = new String[] { "run", "-cluster", "--cluster-host", "0.0.0.0", "--cluster-port", "65432", MainVerticle.class.getCanonicalName() };

    var argArray = args.length > 0 ? args : argAppend;

    new Launcher().dispatch(argArray);
  }

  @Override
  protected String getDefaultCommand() {
    return "run";
  }

  @Override
  protected String getMainVerticle() {
    return MainVerticle.class.getCanonicalName();
  }
}
