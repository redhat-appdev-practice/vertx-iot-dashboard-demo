package com.redhat.consulting.runtimes;

import io.vertx.core.*;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttServerOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.vertx.mqtt.messages.codes.MqttPubAckReasonCode.*;

public class MqttServerVerticle extends SharedDataVerticle {
	
	private static final Logger LOG = LoggerFactory.getLogger(MqttServerVerticle.class);
	
	@Override
	public void start(Promise<Void> startPromise) throws Exception {
		shared = vertx.sharedData();
		
		MqttServerOptions mqttOpts =
				new MqttServerOptions()
						.setUseWebSocket(true)
						.setSsl(false);

		var mqttPort = vertx.getOrCreateContext().config().getInteger("mqttPort", 4321);
		
		MqttServer mqttServer = MqttServer.create(vertx, mqttOpts);
		mqttServer.endpointHandler(this::mqttEndpointHandler)
				.listen(mqttPort)
				.onSuccess(this::mqttServerListenHandler)
				.onFailure(this::mqttErrorHandler)
				.compose(this::initShared)
				.onComplete(startPromise);
	}
	
	private void mqttErrorHandler(Throwable throwable) {
		LOG.atError().setMessage("Unable to start MQTT Server listener").setCause(throwable).log();
	}
	
	private void mqttServerListenHandler(MqttServer mqttServer) {
		LOG.atDebug().setMessage("MQTT Server Started on port: {}").addArgument(mqttServer::actualPort).log();
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
		
		endpoint.subscriptionAutoAck(true);
		
		endpoint.publishHandler(msg -> {
			if (msg.topicName().contentEquals("vibration-data")) {
				try {
					var data = new JsonObject(msg.payload());
					
					// Store the vibration data into the distributed data grid
					var xFuture = xMotion.addAndGet(Math.abs(data.getInteger("x")));
					var yFuture = yMotion.addAndGet(Math.abs(data.getInteger("y")));
					var countFuture = eventCounter.incrementAndGet();
					CompositeFuture
							.all(xFuture, yFuture, countFuture)
							.onSuccess(res -> endpoint.publishAcknowledge(msg.messageId(), SUCCESS, null))
							.onFailure(res -> {
								endpoint.publishAcknowledge(msg.messageId(), UNSPECIFIED_ERROR, null);
								vertx.eventBus().publish("message-errors", res.getCause().getMessage());
							});
				} catch (DecodeException de) {
					LOG.atError().setMessage("Unable to decode MQTT message payload").setCause(de).log();
					endpoint.publishAcknowledge(msg.messageId(), PAYLOAD_FORMAT_INVALID, null);
					vertx.eventBus().publish("message-errors", de.getMessage());
				}
			}
		});
	}
}
