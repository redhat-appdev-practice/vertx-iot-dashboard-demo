package com.redhat.consulting.runtimes;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.shareddata.Counter;
import io.vertx.core.shareddata.SharedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class SharedDataVerticle extends AbstractVerticle {
	private static final Logger LOG = LoggerFactory.getLogger(SharedDataVerticle.class);
	
	protected SharedData shared;
	protected Counter xMotion;
	protected Counter yMotion;
	protected Counter eventCounter;
	
	protected CompositeFuture initShared(Void _v) {
		this.shared = vertx.sharedData();
		Future<Counter> yCounter = shared.getCounter("yMotion");
		Future<Counter> xCounter = shared.getCounter("xMotion");
		Future<Counter> evtCount = shared.getCounter("eventCounter");
		return CompositeFuture.all(xCounter, yCounter, evtCount);
	}
	
	protected Future<Void> storeSharedVariables(CompositeFuture cf) {
		if (cf.succeeded()) {
			this.xMotion = cf.resultAt(0);
			this.yMotion = cf.resultAt(1);
			this.eventCounter = cf.resultAt(2);
		}
		return Future.succeededFuture();
	}
	
	protected CompositeFuture zeroValues(Void _v) {
		var xZero = xMotion.compareAndSet(0, 0);
		var yZero = yMotion.compareAndSet(0, 0);
		var evtZero = eventCounter.compareAndSet(0, 0);
		return CompositeFuture.all(xZero, yZero, evtZero);
	}
	
	void handleFailure(Throwable throwable) {
		LOG.atError().setMessage(throwable.getLocalizedMessage()).setCause(throwable).log();
	}
}
