import { cpSync, existsSync, mkdirSync, readdirSync, rmSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { defineConfig } from "vite";

const root = dirname(fileURLToPath(import.meta.url));
const cesiumBuild = join(root, "node_modules", "cesium", "Build", "Cesium");
const publicDir = join(root, "public");

function copyDirectory(source, destination) {
  if (!existsSync(source)) return;
  rmSync(destination, { recursive: true, force: true });
  mkdirSync(dirname(destination), { recursive: true });
  cpSync(source, destination, { recursive: true });
}

function copyDataFiles() {
  const dataDir = join(root, "..", "out");
  const publicData = join(publicDir, "data");
  mkdirSync(publicData, { recursive: true });
  if (!existsSync(dataDir)) return;

  console.log(`Checking ${dataDir} for "relay.czml" and "accesses.json"... Run the Simulator first if these files do not exist.`);
  for (const file of readdirSync(dataDir)) {
    if (file.endsWith(".czml") || file.endsWith(".json")) {
      console.log(`Copying ${file} from ${dataDir} to ${publicData}`);
      cpSync(join(dataDir, file), join(publicData, file));
    }
  }
}

function cesiumAssetsPlugin() {
  return {
    name: "p2-cesium-assets",
    buildStart() {
      copyDirectory(join(cesiumBuild, "Workers"), join(publicDir, "cesium", "Workers"));
      copyDirectory(join(cesiumBuild, "Assets"), join(publicDir, "cesium", "Assets"));
      copyDirectory(join(cesiumBuild, "ThirdParty"), join(publicDir, "cesium", "ThirdParty"));
      copyDirectory(join(cesiumBuild, "Widgets"), join(publicDir, "cesium", "Widgets"));
      copyDataFiles();
    },
    configureServer(server) {
      const dataDir = join(root, "..", "out");
      server.watcher.add(dataDir);
      server.watcher.on("change", (file) => {
        if (file.startsWith(dataDir)) copyDataFiles();
      });
    },
  };
}

export default defineConfig({
  define: {
    CESIUM_BASE_URL: JSON.stringify("/cesium"),
  },
  resolve: {
    alias: {
      "cesium-bundle": join(cesiumBuild, "index.js"),
    },
  },
  optimizeDeps: {
    exclude: ["cesium-bundle"],
  },
  plugins: [cesiumAssetsPlugin()],
});
