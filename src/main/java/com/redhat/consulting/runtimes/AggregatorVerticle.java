package com.redhat.consulting.runtimes;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.Lock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

public class AggregatorVerticle extends SharedDataVerticle {
	
	private static final Logger LOG = LoggerFactory.getLogger(AggregatorVerticle.class);
	
	
	@Override
	public void start(Promise<Void> startPromise) throws Exception {
		shared = vertx.sharedData();
	
		var aggWindow = vertx.getOrCreateContext().config().getInteger("window", 1000);
		
		vertx.setPeriodic(aggWindow, this::tryAggregate);
		
		startPromise.complete();
	}
	
	private void tryAggregate(Long l) {
		shared.getLocalLockWithTimeout("aggregate", 500)
				.compose(this::aggregate)
				.onSuccess(Lock::release);
	}
	
	private Future<Lock> aggregate(Lock lock) {
		return this.retrieveData()
				.compose(this::sendAggregate)
        .compose(this::clearCounters)
				.compose(t -> Future.succeededFuture(lock), t -> Future.succeededFuture(lock));
	}
	
	private Future<Void> clearCounters(Void unused) {
		var f1 = xMotion.get().compose(i -> xMotion.compareAndSet(i, 0));
		var f2 = yMotion.get().compose(i -> xMotion.compareAndSet(i, 0));
		var f3 = eventCounter.get().compose(i -> xMotion.compareAndSet(i, 0));
		return CompositeFuture.all(f1, f2, f3)
				       .mapEmpty();
	}
	
	private Future<JsonObject> retrieveData() {
		var f1 = xMotion.get();
		var f2 = yMotion.get();
		var f3 = eventCounter.get();
		return CompositeFuture.all(f1, f2, f3)
				.compose(this::formatData);
	}
	
	private Future<JsonObject> formatData(CompositeFuture res) {
		if (res.succeeded()) {
			Integer xVal = res.resultAt(0);
			Integer yVal = res.resultAt(1);
			Integer eventCount = res.resultAt(2);
			var xAvg = xVal / eventCount;
			var yAvg = yVal / eventCount;
			var aggregateData = new JsonObject().put("xAgg", xAvg).put("yAgg", yAvg).put("timestamp", Instant.now().getEpochSecond());
			return Future.succeededFuture(aggregateData);
		}
		return Future.failedFuture(res.cause());
	}
	
	private void handleFailure(Throwable throwable) {
		LOG.atError().setMessage(throwable.getLocalizedMessage()).setCause(throwable).log();
	}
	
	private Future<Void> sendAggregate(JsonObject data) {
		vertx.eventBus().publish("aggregate-stats", data);
		return Future.succeededFuture();
	}
}
