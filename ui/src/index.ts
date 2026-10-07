import { definePlugin } from "@halo-dev/ui-shared";
import { markRaw } from "vue";
import { IconComputer } from "@halo-dev/components";
import AdminView from "./views/AdminView.vue";

export default definePlugin({
  routes: [
    {
      parentName: "Root",
      route: {
        path: "/blue-archive-stats",
        name: "BlueArchiveStats",
        component: AdminView,
        meta: {
          title: "蔚蓝档案战绩",
          searchable: true,
          menu: {
            name: "蔚蓝档案战绩",
            group: "tool",
            icon: markRaw(IconComputer),
            priority: 40,
          },
        },
      },
    },
  ],
});
