<template>
  <q-layout view="lHh Lpr lFf" class="">
    <q-header elevated>
      <q-toolbar>
        <q-btn
          flat
          dense
          round
          icon="menu"
          aria-label="Menu"
          @click="toggleLeftDrawer"
        />

        <q-toolbar-title>
          J4K 2022 - Vert.x IoT
        </q-toolbar-title>
      </q-toolbar>
    </q-header>

    <q-drawer
      v-model="leftDrawerOpen"
      show-if-above
      bordered class="row"
    >
      <q-list dense class="col-grow padded-col">
        <a href="#" class="row">Main</a>
        <a href="#" class="row">Settings</a>
      </q-list>
    </q-drawer>

    <q-page-container>
      <router-view />
    </q-page-container>
  </q-layout>
</template>

<script setup lang="ts">
import { ref } from 'vue';

const leftDrawerOpen = ref<boolean>(false);

const toggleLeftDrawer = () => {
  leftDrawerOpen.value = !leftDrawerOpen.value;
};

</script>

<style lang="sass">
.padded-col
  padding: 0.75rem 0.25rem 0.25rem
</style>
