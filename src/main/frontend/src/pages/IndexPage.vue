<template>
  <q-page class="row items-center justify-evenly" v-drag="dragHandler">
  </q-page>
</template>

<script setup lang="ts">
import { useDrag } from '@vueuse/gesture';
import { Client, Message } from 'paho-mqtt';
import { useQuasar, date } from 'quasar';
import { ref } from 'vue';

const $q = useQuasar();

const client = new Client('localhost', Number('4321'), 'capacitor-client');

client.onConnectionLost = (responseObject) => {
  $q.notify({ message: responseObject.errorMessage, type: 'error' });
};

client.onMessageArrived = (message) => {
  $q.notify({ message: message.payloadString, type: 'info' });
  const msg = new Message('Ping');
  msg.destinationName = 'testing';
  client.send(msg);
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
  const msg = new Message(messageData);
  msg.destinationName = 'vibration-data';
  client.send(msg);
}

useDrag(
  ({ movement: [x, y], dragging }) => {
    if (!dragging && client.isConnected()) {
      sendVibrationData(x, y);
    }
  },
)

</script>
