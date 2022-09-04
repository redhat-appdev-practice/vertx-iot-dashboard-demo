package com.redhat.consulting.runtimes;

import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageConsumer;
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
	
	long periodic;
	
	long lastUpdate;
	
	ConcurrentHashMap<String, MessageConsumer<Buffer>> consumers = new ConcurrentHashMap<>();
	
	@Override
	public void start(Promise<Void> startPromise) throws Exception {
		periodic = vertx.setPeriodic(1000, this::wrapSensorPoll);
		
		var router = Router.router(vertx);
		
		SockJSHandler sockJsHandler = SockJSHandler.create(vertx);
		SockJSBridgeOptions bridgeOptions = new SockJSBridgeOptions();
		bridgeOptions.addOutboundPermitted(new PermittedOptions().setAddress("iot.temp.reading"));
		router.mountSubRouter("/eventbus", sockJsHandler.bridge(bridgeOptions));
		
		staticHandler = StaticHandler.create();
		router.route().handler(staticHandler);
		
		// Handler of last resort... If no other path succeeds, this will always serve `index.html`
		router.route().handler(this::serveIndex);
		
		MqttServerOptions mqttOpts =
				new MqttServerOptions()
               .setPort(4321)
               .setUseWebSocket(true)
               .setSsl(false);
		
		MqttServer mqttServer = MqttServer.create(vertx, mqttOpts);
		mqttServer.endpointHandler(this::mqttEndpointHandler)
				.listen(this::mqttServerListenHandler);
		
		vertx.createHttpServer()
				.requestHandler(router)
				.listen(8080)
				.<Void>mapEmpty()
				.onComplete(startPromise);
	}
	
	private void mqttServerListenHandler(AsyncResult<MqttServer> res) {
		if (res.succeeded()) {
			LOG.atDebug().setMessage("MQTT Server Started on port: {}").addArgument(() -> res.result().actualPort()).log();
		} else {
			LOG.atError().setMessage("Error on starting MQTT Server").setCause(res.cause()).log();
		}
	}
	
	private void mqttEndpointHandler(MqttEndpoint endpoint) {
		// shows main connect info
		LOG.atDebug().setMessage("MQTT client [{}] request to connect, clean session = {}").addArgument(endpoint::clientIdentifier).addArgument(endpoint::isCleanSession).log();
		
		if (endpoint.auth() != null) {
			LOG.atDebug().setMessage("[username = {}, password = {}]").addArgument(() -> endpoint.auth().getUsername()).addArgument(() -> endpoint.auth().getPassword()).log();
		}
		if (endpoint.will() != null) {
			LOG.atDebug()
					.setMessage("[will topic = {} msg = {} QoS = {} isRetain = {}]")
					.addArgument(() -> endpoint.will().getWillTopic())
					.addArgument(() -> new String(endpoint.will().getWillMessageBytes()))
					.addArgument(() -> endpoint.will().getWillQos())
					.addArgument(() -> endpoint.will().isWillRetain())
					.log();
		}
		
		LOG.atDebug().setMessage("[keep alive timeout = {}]").addArgument(endpoint::keepAliveTimeSeconds).log();
		
		// accept connection from the remote client
		endpoint.accept(false);
		
		endpoint.subscribeHandler(subscribe -> {
			List<MqttSubAckReasonCode> reasonCodes = new ArrayList<>();
			for (MqttTopicSubscription s : subscribe.topicSubscriptions()) {
				LOG.atDebug()
						.setMessage("Subscription for {} with QoS {}")
						.addArgument(s::topicName)
						.addArgument(s::qualityOfService)
						.log();
				reasonCodes.add(MqttSubAckReasonCode.qosGranted(s.qualityOfService()));
				var consumer = vertx.eventBus().<Buffer>consumer(s.topicName()).handler(msg -> {
					if (msg.body() != null) {
						endpoint.publish(s.topicName(),
								msg.body(),
								MqttQoS.EXACTLY_ONCE,
								false,
								false);
					}
					
					// specifying handlers for handling QoS 1 and 2
					endpoint
							.publishAcknowledgeHandler(messageId -> LOG.atDebug().log("Received ack for message = {}", messageId))
							.publishReceivedHandler(endpoint::publishRelease)
							.publishCompletionHandler(messageId -> LOG.atDebug().log("Received ack for message = {}", messageId));
				});
				consumers.put(s.topicName(), consumer);
			}
			// ack the subscriptions request
			endpoint.subscribeAcknowledge(subscribe.messageId(), reasonCodes, MqttProperties.NO_PROPERTIES);
		});
		
		endpoint.unsubscribeHandler(unsubscribe -> {
			
			for (String t : unsubscribe.topics()) {
				LOG.atDebug().log("Unsubscription for {}", t);
				consumers.get(t).unregister();
			}
			// ack the subscriptions request
			endpoint.unsubscribeAcknowledge(unsubscribe.messageId());
		});
		
		endpoint.publishHandler(message -> {
				LOG.atDebug()
					.setMessage("Just received message [{}] with QoS [{}]")
					.addArgument(() -> message.payload().toString(Charset.defaultCharset()))
					.addArgument(message::qosLevel)
					.log();
			
			if (message.qosLevel() == MqttQoS.AT_LEAST_ONCE) {
				endpoint.publishAcknowledge(message.messageId());
			} else if (message.qosLevel() == MqttQoS.EXACTLY_ONCE) {
				endpoint.publishReceived(message.messageId());
			}
			
			vertx.eventBus().publish(message.topicName(), message.payload());
			
		}).publishReleaseHandler(endpoint::publishComplete);
	}
	
	private void serveIndex(RoutingContext ctx) {
		ctx.response().sendFile("webroot/index.html");
		ctx.next();
	}
	
	private void wrapSensorPoll(Long aLong) {
		vertx.executeBlocking(this::pollSensors);
	}
	
	private <T> void pollSensors(Promise<T> tPromise) {
		try {
			Process p = Runtime.getRuntime().exec("sensors -j -A coretemp-isa-0000");
			BufferedReader stdOutput = new BufferedReader(new InputStreamReader(p.getInputStream()));
			StringBuilder sb = new StringBuilder();
			String s = null;
			while ((s = stdOutput.readLine()) != null) {
				sb.append(s);
			}
			lastUpdate = ZonedDateTime.now(ZoneOffset.UTC).toEpochSecond();
			JsonObject sensorData = new JsonObject(sb.toString());
			sensorData.stream()
					.filter(chip -> chip.getValue() instanceof JsonObject)
					.map(chip -> (JsonObject) chip.getValue())
					.flatMap(JsonObject::stream)
					.filter(device -> device.getValue() instanceof JsonObject)
					.map(this::mapDevice)
					.flatMap(JsonObject::stream)
					.filter(sensor -> sensor.getKey().contains("Core"))
					.filter(sensor -> sensor.getKey().contains("input"))
					.forEach(this::sendTempUpdate);
		} catch (Exception e) {
			LOG.atError().setCause(e).log("Error while polling sensors");
		}
	}
	
	private void sendTempUpdate(Map.Entry<String, Object> reading) {
		if (reading.getValue() instanceof Double) {
			JsonObject body =
					new JsonObject()
							.put("name", reading.getKey())
							.put("value", reading.getValue())
							.put("timestamp", lastUpdate);
			vertx.eventBus().publish("iot.temp.reading", body);
		}
	}
	
	private JsonObject mapDevice(Map.Entry<String, Object> entry) {
		JsonObject jsonEntry = new JsonObject(((JsonObject) entry.getValue()).encode());
		Set<String> fieldNames = jsonEntry.fieldNames().stream().map(k -> String.format("%s", k)).collect(Collectors.toSet());
		for (String key : fieldNames) {
			Object value = jsonEntry.getValue(key);
			jsonEntry.remove(key);
			jsonEntry.put(String.format("%s:%s", entry.getKey(), key), value);
		}
		return jsonEntry;
	}
	
	@Override
	public void stop() throws Exception {
		vertx.cancelTimer(periodic);
	}
}
