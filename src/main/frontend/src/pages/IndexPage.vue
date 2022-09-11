<template>
  <q-page class="col col-grow items-lg-stretch justify-evenly">
    HELLO {{ xCoord }} x {{ yCoord }}<br />
    Aggregates: {{ aggregate.x }} x {{ aggregate.y }} @ {{ aggregate.t }} from {{ aggregate.n }}
  </q-page>
</template>

<script setup lang="ts">
import { Client, Message } from 'paho-mqtt';
import { useQuasar, date } from 'quasar';
import { ref } from 'vue';
import EventBus from '@vertx/eventbus-bridge-client.js';

import axios from 'axios';

const ebOptions = {
  vertxbus_reconnect_attempts_max: Infinity, // Max reconnect attempts
  vertxbus_reconnect_delay_min: 1000, // Initial delay (in ms) before first reconnect attempt
  vertxbus_reconnect_delay_max: 5000, // Max delay (in ms) between reconnect attempts
  vertxbus_reconnect_exponent: 2, // Exponential backoff factor
  vertxbus_randomization_factor: 0.5, // Randomization factor between 0 and 1
};

const loc = window.location;

const aggregate = ref({
  x: 0,
  y: 0,
  t: 0,
  n: '',
});

const $q = useQuasar();
const xCoord = ref<number>(0);
const yCoord = ref<number>(0);

let lastMotionUpdate = new Date();

const api = axios.create({
  baseURL: loc.origin,
});
console.log(`BaseURL: ${loc.origin}`);

api.get('/config/env.json')
  .then((res) => {
    if (res.status === 200) {
      console.log(JSON.stringify(res.data));
      const { mqtt, websocket } = res.data;
      const eb = new EventBus(websocket.uri, ebOptions);

      eb.onopen = () => {
        eb.registerHandler('iot.motion.aggregate', (err, msg) => {
          if (!err) {
            const {
              body: {
                xAgg,
                yAgg,
                timestamp,
                node,
              },
            } = msg;
            aggregate.value = {
              x: xAgg,
              y: yAgg,
              t: timestamp,
              n: node,
            };
          }
        });
      };

      const client = new Client(mqtt.host, Number(mqtt.port), 'web-client');

      client.onConnectionLost = (responseObject) => {
        $q.notify({ message: responseObject.errorMessage, type: 'error' });
      };

      client.connect({
        onSuccess: () => {
          $q.notify({ message: 'Connected', type: 'info' });
        },
        onFailure: () => {
          $q.notify({ message: 'Failed to connect', type: 'error' })
        },
      });

      const sendVibrationData = (x: number, y: number) => {
        const timestamp = Date.now();
        const isoTimestamp = date.formatDate(timestamp, 'YYYY-MM-DDTHH:mm:ss.SSSZ');
        const messageData = JSON.stringify({ x, y, timestamp: isoTimestamp });
        xCoord.value = x;
        yCoord.value = y;
        if (client.isConnected()) {
          const msg = new Message(messageData);
          msg.destinationName = 'vibration-data';
          client.send(msg);
        } else {
          // eslint-disable-next-line no-console
          console.log(`Client not connected: ${messageData}`);
        }
      };

      const handleMotionEvent = (evt: DeviceMotionEvent) => {
        const timestamp = new Date();
        const diff = date.getDateDiff(lastMotionUpdate, timestamp, 'seconds');
        const data = {
          x: evt?.accelerationIncludingGravity?.x,
          y: evt?.accelerationIncludingGravity?.y,
        };
        if (
          diff > 1
          && data?.x !== undefined
          && data?.y !== undefined
          && data?.x !== null
          && data?.y !== null) {
          lastMotionUpdate = timestamp;
          sendVibrationData(data.x, data.y);
        }
      };

      const handleDragEvent = (evt: MouseEvent) => {
        const data = { x: evt?.clientX, y: evt.clientY };
        if (
          data?.x !== undefined && data?.y !== undefined
          && data?.x !== null && data?.y !== null
        ) {
          sendVibrationData(data.x, data.y);
        }
      };

      window.addEventListener('devicemotion', handleMotionEvent, true);

      window.addEventListener('mousedown', handleDragEvent, true);
    }
  })
  .catch((err) => {
    $q.notify({ message: err.message, type: 'error' });
  });
</script>
