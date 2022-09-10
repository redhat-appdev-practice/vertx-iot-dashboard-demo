package com.redhat.consulting.runtimes;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Promise;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttServerOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.vertx.mqtt.messages.codes.MqttPubAckReasonCode.PAYLOAD_FORMAT_INVALID;

public class MqttServerVerticle extends SharedDataVerticle {
	
	private static final Logger LOG = LoggerFactory.getLogger(MqttServerVerticle.class);
	
	@Override
	public void start(Promise<Void> startPromise) throws Exception {
		LOG.atInfo().setMessage("Mqtt deplying").log();
		
		MqttServerOptions mqttOpts =
				new MqttServerOptions()
						.setUseWebSocket(true)
						.setSsl(false);

		var mqttPort = vertx.getOrCreateContext().config().getInteger("mqttPort", 4321);
		
		MqttServer mqttServer = MqttServer.create(vertx, mqttOpts);
		mqttServer.endpointHandler(this::mqttEndpointHandler)
				.listen(mqttPort)
				.onSuccess(this::mqttServerListenHandler)
				.<Void>mapEmpty()
				.compose(this::initShared)
				.compose(this::storeSharedVariables)
				.<Void>mapEmpty()
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
					.setMessage("[will topic = {} QoS = {} isRetain = {}]")
					.addArgument(() -> endpoint.will().getWillTopic())
					.addArgument(() -> endpoint.will().getWillQos())
					.addArgument(() -> endpoint.will().isWillRetain())
					.log();
		}
		
		LOG.atDebug().setMessage("[keep alive timeout = {}]").addArgument(endpoint::keepAliveTimeSeconds).log();
		
		// accept connection from the remote client
		endpoint.accept(false);
		
		endpoint.publishHandler(msg -> {
			LOG.atDebug().setMessage("Message Received").log();
			if (msg.topicName().contentEquals("vibration-data")) {
				try {
					var data = new JsonObject(msg.payload());
					LOG.atDebug().setMessage("Data: x - {}, y - {}").addArgument(data.getInteger("x")).addArgument(data.getInteger("y")).log();
					
					// Store the vibration data into the distributed data grid
					var xFuture = xMotion.addAndGet(Math.abs(data.getInteger("x")));
					var yFuture = yMotion.addAndGet(Math.abs(data.getInteger("y")));
					var countFuture = eventCounter.incrementAndGet();
					CompositeFuture
							.all(xFuture, yFuture, countFuture);
				} catch (DecodeException de) {
					LOG.atError().setMessage("Unable to decode MQTT message payload").setCause(de).log();
					endpoint.publishAcknowledge(msg.messageId(), PAYLOAD_FORMAT_INVALID, null);
					vertx.eventBus().publish("message-errors", de.getMessage());
				}
			}
		});
	}
}
