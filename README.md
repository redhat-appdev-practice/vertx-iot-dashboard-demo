# Vert.x + VueJS IoT Dashboard Demo

## Overview

This demo application shows how you can leverage the [SockJS EventBus Bridge](https://vertx.io/docs/vertx-web/java/#_sockjs_event_bus_bridge) in [Eclipse Vert.x](https://vertx.io/) to build a near-realtime IoT-style dashboard and visualize streaming data.

## Build

```
git clone https://github.com/redhat-appdev-practice/vertx-iot-dashboard-demo.git
cd vertx-iot-dashboard-demo
./mvnw clean verify
```

## Pre-requisites

* This program requires the `sensors` command from [lm_sensors](https://github.com/lm-sensors/lm-sensors) so that it can collect temperature data from the machine where this is run.

## Run

### Development Mode
```
## Dev Mode
./mvnw clean compile vertx:run
```

### Standalone

```
java -jar target/iot-dashboard-1.0.0-SNAPSHOT.jar run com.redhat.consulting.runtimes.MainVerticle
```
