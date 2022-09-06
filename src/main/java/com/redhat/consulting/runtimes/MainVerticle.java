package com.redhat.consulting.runtimes;

import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.SockJSBridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttServerOptions;
import io.vertx.mqtt.MqttTopicSubscription;
import io.vertx.mqtt.messages.codes.MqttSubAckReasonCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class MainVerticle extends AbstractVerticle {
	
	private static final Logger LOG = LoggerFactory.getLogger(MainVerticle.class);
	
	StaticHandler staticHandler;
	
	@Override
	public void start(Promise<Void> startPromise) throws Exception {
		var router = Router.router(vertx);
		
		SockJSHandler sockJsHandler = SockJSHandler.create(vertx);
		SockJSBridgeOptions bridgeOptions = new SockJSBridgeOptions();
		bridgeOptions.addOutboundPermitted(new PermittedOptions().setAddress("iot.motion.aggregate"));
		router.mountSubRouter("/eventbus", sockJsHandler.bridge(bridgeOptions));
		
		staticHandler = StaticHandler.create();
		router.route().handler(staticHandler);
		
		// Handler of last resort... If no other path succeeds, this will always serve `index.html`
		router.route().handler(this::serveIndex);
		
		vertx.createHttpServer()
				.requestHandler(router)
				.listen(8080)
				.compose(this::deployMqttServerVerticle)
				.<Void>mapEmpty()
				.onComplete(startPromise);
	}
	
	private Future<String> deployMqttServerVerticle(HttpServer httpServer) {
		return vertx.deployVerticle(new MqttServerVerticle());
	}
	
	
	private void serveIndex(RoutingContext ctx) {
		ctx.response().sendFile("webroot/index.html");
		ctx.next();
	}
}
