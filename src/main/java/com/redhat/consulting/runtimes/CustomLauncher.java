package com.redhat.consulting.runtimes;

import io.vertx.core.Launcher;

public class CustomLauncher extends Launcher {

  public static void main(String[] args) {
    System.setProperty("vertx.setClassPathResolvingEnabled", "true");
    System.setProperty("vertx.setFileCachingEnabled", "true");
    System.setProperty("vertx.logger-delegate-factory-class-name", "io.vertx.core.logging.SLF4JLogDelegateFactory");

    new Launcher().dispatch(args);
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
