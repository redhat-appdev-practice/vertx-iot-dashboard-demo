import { GesturePlugin } from '@vueuse/gesture';
import { boot } from 'quasar/wrappers'

/**
 * Load and enable the Gesture plugin from @vueuse
 */
export default boot(({ app }) => {
  app.use(GesturePlugin);
});
