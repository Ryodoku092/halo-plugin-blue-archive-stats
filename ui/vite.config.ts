import { viteConfig } from "@halo-dev/ui-plugin-bundler-kit/vite";

const OUT_DIR_PROD = "../src/main/resources/ui";
const OUT_DIR_DEV = "../build/resources/main/ui";

export default viteConfig({
  vite: ({ mode }) => {
    const isProduction = mode === "production";
    const outDir = isProduction ? OUT_DIR_PROD : OUT_DIR_DEV;

    return {
      build: {
        outDir,
        emptyOutDir: true,
      },
    };
  },
});
