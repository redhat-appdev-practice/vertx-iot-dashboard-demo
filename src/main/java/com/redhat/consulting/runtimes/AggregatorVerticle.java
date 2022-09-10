package com.redhat.consulting.runtimes;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.impl.VertxImpl;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.Lock;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.ext.cluster.infinispan.InfinispanClusterManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class AggregatorVerticle extends SharedDataVerticle {
	
	private static final Logger LOG = LoggerFactory.getLogger(AggregatorVerticle.class);
	
	private String uuid;
	
	@Override
	public void start(Promise<Void> startPromise) throws Exception {
		
		uuid = UUID.randomUUID().toString();
		
		LOG.atInfo().setMessage("Aggregator deploying").log();
	
		var aggWindow = vertx.getOrCreateContext().config().getInteger("window", 2000);
		
		vertx.setPeriodic(aggWindow, this::tryAggregate);
		
		startPromise.complete();
	}
	
	private void tryAggregate(Long l) {
		LOG.atInfo().setMessage("Periodic poller").log();
		this.initShared(null)
				.compose(this::storeSharedVariables)
		    .compose(_v -> shared.getLockWithTimeout("aggregate", 500))
				.compose(this::aggregate)
				.onSuccess(Lock::release);
	}
	
	private Future<Lock> aggregate(Lock lock) {
		LOG.atDebug().setMessage("Lock obtained").log();
		return this.retrieveData()
				.compose(this::formatData)
				.compose(this::sendAggregate)
				.compose(this::clearCounters)
				.compose(t -> Future.succeededFuture(lock), t -> Future.succeededFuture(lock));
	}
	
	private CompositeFuture retrieveData() {
		LOG.atDebug().setMessage("Retrieving data").log();
		try {
			var f1 = xMotion.get();
			var f2 = yMotion.get();
			var f3 = eventCounter.get();
			return CompositeFuture.all(f1, f2, f3);
		} catch (NullPointerException npe) {
			LOG.atError().setMessage(npe.getLocalizedMessage()).setCause(npe).log();
			return CompositeFuture.any(List.of(Future.failedFuture(npe)));
		}
	}
	
	private Future<JsonObject> formatData(CompositeFuture res) {
		LOG.atDebug().setMessage("Formatting data").log();
		if (res.succeeded()) {
			Long xVal = null;
			Long yVal = null;
			Long eventCount = null;
			try {
				xVal = res.resultAt(0);
				yVal = res.resultAt(1);
				eventCount = res.resultAt(2);
			} catch (Exception e) {
				LOG.atError().setMessage(e.getLocalizedMessage()).setCause(e).log();
			}
			try {
				var xAvg = Long.valueOf(0L);
				var yAvg = Long.valueOf(0L);
				if (eventCount != null && eventCount.compareTo(0L) != 0) {
					xAvg = xVal / eventCount;
					yAvg = yVal / eventCount;
				}
				var aggregateData = new JsonObject()
						                    .put("xAgg", xAvg)
						                    .put("yAgg", yAvg)
						                    .put("timestamp", Instant.now().getEpochSecond())
						                    .put("node", uuid);
				LOG.atDebug().setMessage("Data: {}").addArgument(aggregateData::encodePrettily).log();
				return Future.succeededFuture(aggregateData);
			} catch (Exception e) {
				LOG.atError().setMessage(e.getLocalizedMessage()).setCause(e).log();
				return Future.failedFuture(e);
			}
		} else {
			LOG.atError().setMessage(res.cause().getLocalizedMessage()).setCause(res.cause()).log();
		}
		return Future.failedFuture(res.cause());
	}
	
	private Future<Void> sendAggregate(JsonObject data) {
		LOG.atDebug().setMessage("Sending aggregate data").log();
		vertx.eventBus().publish("iot.motion.aggregate", data);
		return Future.succeededFuture();
	}
	
	private CompositeFuture clearCounters(Void unused) {
		LOG.atDebug().setMessage("Clearing counters").log();
		var f1 = xMotion.get().compose(i -> xMotion.compareAndSet(i, 0));
		var f2 = yMotion.get().compose(i -> xMotion.compareAndSet(i, 0));
		var f3 = eventCounter.get().compose(i -> xMotion.compareAndSet(i, 0));
		return CompositeFuture.all(f1, f2, f3);
	}
}
