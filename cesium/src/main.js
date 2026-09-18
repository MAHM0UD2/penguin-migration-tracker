import "cesium/Build/Cesium/Widgets/widgets.css";
import "./style.css";
import {
  ArcGISTiledElevationTerrainProvider,
  Cartesian2,
  Cartesian3,
  Color,
  CzmlDataSource,
  EllipsoidTerrainProvider,
  Ion,
  JulianDate,
  ProviderViewModel,
  TileMapServiceImageryProvider,
  Viewer,
  buildModuleUrl,
} from "cesium-bundle";

Ion.defaultAccessToken = "";

const ACCESS_URL = "/data/accesses.json";
const CZML_URL = "/data/relay.czml";
const START_TIME = "2027-04-01T00:00:00Z";

const status = document.querySelector("#status");
const metrics = document.querySelector("#metrics");
const sampleSelect = document.querySelector("#sampleSelect");
const passList = document.querySelector("#passList");

const imageryProviderViewModels = [
  new ProviderViewModel({
    name: "Natural Earth II",
    iconUrl: buildModuleUrl("Widgets/Images/ImageryProviders/naturalEarthII.png"),
    tooltip: "Local Natural Earth II imagery bundled with Cesium.",
    creationFunction: () => TileMapServiceImageryProvider.fromUrl(
      buildModuleUrl("Assets/Textures/NaturalEarthII"),
    ),
  }),
];

const terrainProviderViewModels = [
  new ProviderViewModel({
    name: "WGS84 Ellipsoid",
    iconUrl: buildModuleUrl("Widgets/Images/TerrainProviders/Ellipsoid.png"),
    tooltip: "Smooth WGS84 ellipsoid.",
    creationFunction: () => new EllipsoidTerrainProvider(),
  }),
  new ProviderViewModel({
    name: "ArcGIS World Terrain",
    iconUrl: buildModuleUrl("Widgets/Images/TerrainProviders/CesiumWorldTerrain.png"),
    tooltip: "ArcGIS World Elevation terrain.",
    creationFunction: () => ArcGISTiledElevationTerrainProvider.fromUrl(
      "https://elevation3d.arcgis.com/arcgis/rest/services/WorldElevation3D/Terrain3D/ImageServer",
    ),
  }),
];

const viewer = new Viewer("cesiumContainer", {
  animation: true,
  timeline: true,
  baseLayerPicker: true,
  imageryProviderViewModels,
  selectedImageryProviderViewModel: imageryProviderViewModels[0],
  terrainProviderViewModels,
  selectedTerrainProviderViewModel: terrainProviderViewModels[1],
  fullscreenButton: false,
  geocoder: false,
  homeButton: false,
  navigationHelpButton: false,
  sceneModePicker: false,
  selectionIndicator: true,
  infoBox: false,
  shouldAnimate: true,
});

viewer.scene.globe.baseColor = Color.fromCssColorString("#0f2633");
viewer.scene.skyAtmosphere.show = true;
viewer.clock.currentTime = JulianDate.fromIso8601(START_TIME);
viewer.camera.setView({
  destination: Cartesian3.fromDegrees(0, 18, 22_000_000),
});

try {
  status.textContent = "Loading CZML and access windows...";

  const [source, windows] = await Promise.all([
    CzmlDataSource.load(CZML_URL),
    fetchJson(ACCESS_URL),
  ]);

  await viewer.dataSources.add(source);
  styleLoadedEntities(source);

  const samples = [...new Set(windows.map((window) => window.site))];
  renderMetrics(windows, samples);
  renderSampleSelect(samples);
  renderPasses(windows, samples[0]);

  sampleSelect.addEventListener("change", () => {
    renderPasses(windows, sampleSelect.value);
    focusSample(source, sampleSelect.value);
  });

  status.textContent = "Mission data loaded.";
} catch (error) {
  console.error(error);
  status.textContent = "Could not load viewer data.";
  metrics.innerHTML = `<li>Did you run the <code>Simulator</code> first? Check that <code>out/relay.czml</code> and <code>out/accesses.json</code> exist, then restart Vite.</li>`;
}

async function fetchJson(url) {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`Failed to load ${url}: ${response.status}`);
  }
  return response.json();
}

function styleLoadedEntities(source) {
  const entities = source.entities.values;
  for (const entity of entities) {
    if (entity.id.startsWith("Penguin_")) {
      if (entity.label) {
        entity.label.show = false;
        entity.label.pixelOffset = new Cartesian2(10, -12);
      }
      if (entity.point) entity.point.pixelSize = 8;
    }
  }

  const migrationPath = source.entities.getById("migration_path");
  if (migrationPath?.polyline) {
    migrationPath.polyline.material = Color.MAGENTA;
    migrationPath.polyline.width = 4;
  }

  const relay = source.entities.getById("relay");
  if (relay?.path) relay.path.width = 3;
}

function renderMetrics(windows, samples) {
  const durations = windows.map((window) => {
    const start = Date.parse(trimIso(window.start));
    const end = Date.parse(trimIso(window.end));
    return Math.max(0, (end - start) / 1000);
  });
  const total = durations.reduce((sum, value) => sum + value, 0);
  const mean = durations.length ? total / durations.length : 0;

  metrics.innerHTML = "";
  addMetric("Access windows", windows.length.toLocaleString());
  addMetric("Migration samples", samples.length.toLocaleString());
  addMetric("Mean window", `${mean.toFixed(0)} s`);
  addMetric("Total access", `${(total / 3600).toFixed(1)} h`);
}

function addMetric(label, value) {
  const item = document.createElement("li");
  item.innerHTML = `<span>${label}</span><strong>${value}</strong>`;
  metrics.append(item);
}

function renderSampleSelect(samples) {
  sampleSelect.innerHTML = "";
  for (const sample of samples) {
    const option = document.createElement("option");
    option.value = sample;
    option.textContent = sample.replace("Penguin_", "").replaceAll("_", "-");
    sampleSelect.append(option);
  }
}

function renderPasses(windows, sample) {
  const selected = windows.filter((window) => window.site === sample).slice(0, 8);
  passList.innerHTML = "";

  if (!selected.length) {
    const item = document.createElement("li");
    item.textContent = "No access windows for this sample.";
    passList.append(item);
    return;
  }

  for (const window of selected) {
    const item = document.createElement("li");
    item.innerHTML = `<strong>${formatTime(window.start)}</strong><span>${formatDuration(window)}</span>`;
    passList.append(item);
  }
}

function focusSample(source, sample) {
  for (const entity of source.entities.values) {
    if (!entity.id.startsWith("Penguin_")) continue;
    if (entity.point) {
      entity.point.pixelSize = entity.id === sample ? 13 : 8;
      entity.point.color = entity.id === sample ? Color.fromCssColorString("#f6c944") : Color.fromCssColorString("#d223a1");
    }
    if (entity.label) entity.label.show = entity.id === sample;
  }

  const entity = source.entities.getById(sample);
  if (entity) viewer.flyTo(entity, { duration: 0.8 });
}

function formatTime(value) {
  return new Intl.DateTimeFormat("en-GB", {
    month: "short",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    timeZone: "UTC",
    timeZoneName: "short",
  }).format(new Date(trimIso(value)));
}

function formatDuration(window) {
  const seconds = (Date.parse(trimIso(window.end)) - Date.parse(trimIso(window.start))) / 1000;
  return `${Math.max(0, seconds).toFixed(0)} s`;
}

function trimIso(value) {
  return value.replace(/\.(\d{3})\d+Z$/, ".$1Z");
}

