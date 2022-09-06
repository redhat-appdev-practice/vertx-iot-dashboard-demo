package com.redhat.consulting.runtimes;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.shareddata.Counter;
import io.vertx.core.shareddata.SharedData;
import io.vertx.mqtt.MqttServer;

public abstract class SharedDataVerticle extends AbstractVerticle {
	protected SharedData shared;
	protected Counter xMotion;
	protected Counter yMotion;
	protected Counter eventCounter;
	
	protected Future<Void> initShared(MqttServer server) {
		Future<Counter> yCounter = shared.getCounter("yMotion");
		Future<Counter> xCounter = shared.getCounter("xMotion");
		Future<Counter> evtCount = shared.getCounter("eventCounter");
		CompositeFuture.all(xCounter, yCounter, evtCount).onSuccess(res -> {
			this.xMotion = res.resultAt(0);
			this.yMotion = res.resultAt(1);
			this.eventCounter = res.resultAt(2);
		});
		return Future.succeededFuture();
	}
}
