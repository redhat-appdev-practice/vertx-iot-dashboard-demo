package com.redhat.consulting.runtimes;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.SockJSBridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainVerticle extends SharedDataVerticle {
	
	private static final Logger LOG = LoggerFactory.getLogger(MainVerticle.class);
	
	StaticHandler staticHandler;
	
	@Override
	public void start(Promise<Void> startPromise) throws Exception {
		LOG.atError().setMessage("MainVerticle Loading").log();
		var router = Router.router(vertx);
		
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
