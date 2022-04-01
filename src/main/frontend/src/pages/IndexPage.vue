<template>
  <q-page class="row items-center justify-evenly">
    <Chart :options="chartOptions" class="col-grow" />
  </q-page>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';

import { Chart } from 'highcharts-vue';
import {
  PlotOptions,
  YAxisOptions,
  XAxisOptions,
  ChartOptions,
  TitleOptions,
  ExportingOptions,
  SeriesLineOptions
} from 'highcharts';

import Highcharts from 'highcharts';
import exportingInit from 'highcharts/modules/exporting'
import EventBus from "@vertx/eventbus-bridge-client.js";

exportingInit(Highcharts);

const chartOptions = ref({
  title: {
    text: 'Sensor Temperatures',
    style: {
      fontSize: '1.6rem',
      fontWeight: 'bold',
    },
  } as TitleOptions,
  exporting: {
    enabled: true,
  } as ExportingOptions,
  chart: {
    animation: {
      duration: 300,
    },
    height: 700
  } as ChartOptions,
  xAxis: [{
    categories: [] as string[],
    labels: {
      rotation: -65,
      style: {
        fontSize: '0.9rem',
      },
    },
    gridLineWidth: 1,
  }] as XAxisOptions[],
  yAxis: [{
    gridLineWidth: 1,
    softMax: 100,
    softMin: 65,
    title: {
      text: unitLabel.value
    }
  }] as YAxisOptions[],
  plotOptions: {
    series: {
      animation: {
        duration: 500,
      },
    },
  } as PlotOptions,
  series: [] as SeriesLineOptions[]
});


interface Reading {
  name: string
  value: number
  timestamp: number
}

const ebOptions = {
   vertxbus_reconnect_attempts_max: Infinity, // Max reconnect attempts
   vertxbus_reconnect_delay_min: 1000, // Initial delay (in ms) before first reconnect attempt
   vertxbus_reconnect_delay_max: 5000, // Max delay (in ms) between reconnect attempts
   vertxbus_reconnect_exponent: 2, // Exponential backoff factor
   vertxbus_randomization_factor: 0.5 // Randomization factor between 0 and 1
};
const ebUrl = `${window.location.protocol}//${window.location.host}/eventbus`;
const eb = new EventBus(ebUrl, ebOptions);

eb.onopen = () => {
  eb.registerHandler('iot.temp.reading', (error: any, message: Reading) => {
    if (error) {
      console.log(`Error from event bus: ${JSON.stringify(error)}`);
    } else {
      const label = message.body.name.split(':')[0]
      const seriesIndex = chartOptions.value.series.findIndex(s => s.name === label);
      if (seriesIndex >= 0) {
        if (chartOptions.value.series[seriesIndex].data.length >= 40) {
          chartOptions.value.series[seriesIndex].data.shift();
        }
        chartOptions.value.series[seriesIndex].data?.push({ y: message.body.value, x: message.body.timestamp });
      } else {
        chartOptions.value.series.push({
          name: label,
          type: 'line',
          data: [{ y: message.body.value, x: message.body.timestamp }]
        })
      }
    }
  });
}
</script>
<style lang="sass">
.highcharts-root
  height: 100%
</style>
