import { boot } from 'quasar/wrappers'
import HighchartsVue from 'highcharts-vue';

export default boot(({ app }) => {
  app.use(() => HighchartsVue);
});
