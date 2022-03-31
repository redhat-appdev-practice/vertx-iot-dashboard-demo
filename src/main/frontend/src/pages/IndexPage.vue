<template>
  <q-page class="row items-center justify-evenly">
    <Chart :options="chartOptions" class="col-grow" />
  </q-page>
</template>

<script setup lang="ts">
import { ref } from 'vue';

import { date, useQuasar, debounce } from 'quasar';

import { Chart } from 'highcharts-vue';
import { PlotOptions, PointOptionsObject, SeriesAreaOptions, YAxisOptions, XAxisOptions, ChartOptions, TitleOptions, ExportingOptions } from 'highcharts';

import Highcharts from 'highcharts';
import exportingInit from 'highcharts/modules/exporting'

exportingInit(Highcharts);

const { addToDate, formatDate } = date;

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
  }] as YAxisOptions[],
  plotOptions: {
    series: {
      animation: {
        duration: 500,
      },
    },
  } as PlotOptions,
  series: [] as SeriesAreaOptions[]
});


</script>
