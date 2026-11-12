// Эко-Щит Каспия — Логика дашборда инспектора
const API_BASE = window.location.origin;

let mapInstance = null;
let markersMap = {};
let telemetryChartInstance = null;
let currentStationId = 1;
let currentAlertFilterResolved = false;

document.addEventListener("DOMContentLoaded", () => {
  startLiveCaspianClock();
  initMap();
  refreshAllData();
  loadRecordingDevices();
  initVisualizerCanvas();

  // Auto-refresh interval (every 30 seconds)
  setInterval(() => {
    loadStations();
    loadAlerts(currentAlertFilterResolved);
  }, 30000);
});

function startLiveCaspianClock() {
  const clockEl = document.getElementById("live-caspian-clock");
  if (!clockEl) return;
  const update = () => {
    const now = new Date();
    // Caspian / Astrakhan time is UTC+4 (MSK+1)
    const options = {
      timeZone: "Europe/Samara",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
      day: "2-digit",
      month: "2-digit",
      year: "numeric"
    };
    clockEl.textContent = now.toLocaleString("ru-RU", options);
  };
  update();
  setInterval(update, 1000);
}

// ==========================================
// 1. Инициализация и работа с картой (Leaflet)
// ==========================================
function initMap() {
  const mapEl = document.getElementById("map");
  if (!window.L) {
    mapEl.innerHTML = `<div class="loading-placeholder">Картографический модуль загружен в автономном режиме.</div>`;
    return;
  }

  // Центрирование на дельту Волги и Северный Каспий
  mapInstance = L.map("map").setView([45.2, 48.0], 7);

  // Высококонтрастная монохромная морская подложка OpenStreetMap (без API-ключей, стабильно)
  L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
    maxZoom: 18,
    attribution: "© OpenStreetMap | Ситуационный Центр ООПТ Каспий"
  }).addTo(mapInstance);

  initSealMigrationLayer();
}

let sealLayerGroup = null;
let sealLayerVisible = true;

function initSealMigrationLayer() {
  if (!mapInstance || !window.L) return;

  sealLayerGroup = L.layerGroup();

  // Маршрут осенней миграции каспийского тюленя (Средний Каспий -> о. Тюлений -> Северный Каспий)
  const sealPathCoords = [
    [42.85, 47.60], // Дербент / Избербаш (глубоководный нагул)
    [43.85, 47.65], // Аграханский залив
    [44.50, 47.52], // Остров Тюлений (Буй №5 - ключевое лежбище)
    [45.45, 47.95], // ВКМСК 145 км (Буй №3 - миграционный фарватер)
    [45.75, 48.40], // Авандельта Волги / Зона ледообразования
    [45.90, 49.10]  // Северо-Восточный шельф
  ];

  const sealPolyline = L.polyline(sealPathCoords, {
    color: "#ffffff",
    weight: 3,
    opacity: 0.95,
    dashArray: "6, 6",
    lineJoin: "round"
  });

  sealPolyline.bindPopup(`
    <div class="tactical-popup">
      <div class="tp-title">🦭 Миграционный коридор тюленя</div>
      <div class="tp-sub">Pusa caspica • Красная книга РФ</div>
      <div style="font-size: 11px; line-height: 1.45; color: #a3a3a3; font-family: var(--font-mono);">
        <p style="margin: 0 0 4px 0;"><strong style="color: #fff;">Сезон:</strong> Октябрь — Декабрь</p>
        <p style="margin: 0 0 4px 0;"><strong style="color: #fff;">Маршрут:</strong> Средний Каспий ➔ о. Тюлений ➔ Северный Каспий (ледовые поля).</p>
        <p style="margin: 0;"><strong style="color: #ffffff;">Защита:</strong> Дозор буев DEV-TUL-05 и DEV-VKMSK-03.</p>
      </div>
    </div>
  `);

  sealLayerGroup.addLayer(sealPolyline);

  // Опорные точки на пути тюленей
  const sealPoints = [
    {
      coords: [44.50, 47.52],
      title: "🦭 Лежбище: Остров Тюлений",
      desc: "Крупнейшее осеннее лежбище нерпы. Охраняется буем DEV-TUL-05."
    },
    {
      coords: [45.45, 47.95],
      title: "⚠️ Фарватер ВКМСК 145 км",
      desc: "Опасная зона пересечения судового хода и пути тюленей."
    }
  ];

  sealPoints.forEach(p => {
    const sealMarker = L.marker(p.coords, {
      icon: L.divIcon({
        className: "seal-pin",
        html: `<div style="background: #ffffff; color: #000000; border-radius: 50%; width: 22px; height: 22px; display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 800; border: 2px solid #000000; box-shadow: 0 0 8px rgba(255,255,255,0.7);">🦭</div>`,
        iconSize: [24, 24],
        iconAnchor: [12, 12]
      })
    });
    sealMarker.bindPopup(`<div class="tactical-popup"><div class="tp-title">${p.title}</div><div class="tp-sub">${p.desc}</div></div>`);
    sealLayerGroup.addLayer(sealMarker);
  });

  sealLayerGroup.addTo(mapInstance);
}

function toggleSealMigrationLayer() {
  const btn = document.getElementById("btn-toggle-seals");
  if (!mapInstance || !sealLayerGroup) return;

  if (sealLayerVisible) {
    mapInstance.removeLayer(sealLayerGroup);
    sealLayerVisible = false;
    if (btn) btn.classList.remove("active");
  } else {
    mapInstance.addLayer(sealLayerGroup);
    sealLayerVisible = true;
    if (btn) btn.classList.add("active");
  }
}

function getMarkerColor(status) {
  if (status === "CRITICAL") return "#ef4444";
  if (status === "WARNING") return "#f59e0b";
  return "#10b981";
}

function renderMapMarkers(stations) {
  if (!mapInstance || !window.L) return;

  stations.forEach(st => {
    const color = getMarkerColor(st.status);
    const customIcon = L.divIcon({
      className: "custom-sonar-pin",
      html: `
        <div class="sonar-marker-wrap">
          <div class="sonar-ring" style="border: 2px solid ${color};"></div>
          <div class="sonar-core" style="background-color: ${color};"></div>
        </div>
      `,
      iconSize: [28, 28],
      iconAnchor: [14, 14]
    });

    const statusBadge = `<span style="background: ${color}; color: ${st.status === 'WARNING' ? '#000000' : '#ffffff'}; font-weight: 800; padding: 2px 6px; font-size: 10px;">${st.status}</span>`;

    const popupContent = `
      <div class="tactical-popup">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; border-bottom: 1px solid #000000; padding-bottom: 4px;">
          <div class="tp-title">${st.name}</div>
          ${statusBadge}
        </div>
        <div class="tp-sub">${st.territory}</div>
        <div class="tp-metrics">
          <div class="tp-row"><span>Кислород O₂:</span><strong>${st.latestTelemetry?.dissolvedOxygenMgL != null ? st.latestTelemetry.dissolvedOxygenMgL + " мг/л" : "—"}</strong></div>
          <div class="tp-row"><span>Температура:</span><strong>${st.latestTelemetry?.temperatureC != null ? st.latestTelemetry.temperatureC + " °C" : "—"}</strong></div>
          <div class="tp-row"><span>Солёность:</span><strong>${st.latestTelemetry?.salinityPpt != null ? st.latestTelemetry.salinityPpt + " ‰" : "—"}</strong></div>
          <div class="tp-row"><span>Уровень моря:</span><strong>${st.latestTelemetry?.waterLevelCm != null ? st.latestTelemetry.waterLevelCm + " см" : "—"}</strong></div>
        </div>
        <button class="tp-btn" onclick="selectStationAndChart(${st.id})">📊 Показать график</button>
      </div>
    `;

    if (markersMap[st.id]) {
      markersMap[st.id].setIcon(customIcon);
      markersMap[st.id].setPopupContent(popupContent);
    } else {
      const marker = L.marker([st.latitude, st.longitude], { icon: customIcon })
        .addTo(mapInstance)
        .bindPopup(popupContent);
      markersMap[st.id] = marker;
    }
  });
}

function selectStationAndChart(stationId) {
  document.getElementById("station-selector").value = stationId;
  onStationChanged(stationId);
}

// ==========================================
// 2. Загрузка данных станций и телеметрии
// ==========================================
async function loadStations() {
  try {
    const res = await fetch(`${API_BASE}/api/stations`);
    if (!res.ok) throw new Error("Ошибка получения станций");
    const stations = await res.json();

    document.getElementById("stat-stations").innerText = stations.length;
    renderMapMarkers(stations);

    for (const id of ['station-selector', 'station-select-audio', 'forecast-station-select']) updateStationSelect(stations, id);
  } catch (err) {
    console.error("loadStations error:", err);
  }
}

async function onStationChanged(stationId) {
  currentStationId = stationId;
  await loadStationChart(stationId);
}

// ==========================================
// 3. Графики телеметрии (Chart.js)
// ==========================================
async function loadStationChart(stationId) {
  const canvas = document.getElementById("telemetryChart");
  if (!canvas || !window.Chart) return;

  try {
    const res = await fetch(`${API_BASE}/api/telemetry/water/station/${stationId}?limit=20`);
    if (!res.ok) throw new Error("Ошибка загрузки истории телеметрии");
    const data = await res.json();

    // Data comes newest first; reverse for timeline
    const sorted = [...data].reverse();
    const labels = sorted.map(d => {
      const dt = new Date(d.recordedAt);
      return dt.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
    });

    const o2Data = sorted.map(d => d.dissolvedOxygenMgL);
    const tempData = sorted.map(d => d.temperatureC);
    const levelData = sorted.map(d => d.waterLevelCm);

    if (telemetryChartInstance) {
      telemetryChartInstance.destroy();
    }

    const ctx = canvas.getContext("2d");
    telemetryChartInstance = new Chart(ctx, {
      type: "line",
      data: {
        labels: labels,
        datasets: [
          {
            label: "Кислород O₂ (мг/л)",
            data: o2Data,
            borderColor: "#ffffff",
            backgroundColor: "rgba(255, 255, 255, 0.08)",
            borderWidth: 2,
            pointBackgroundColor: "#ffffff",
            pointBorderColor: "#000000",
            pointRadius: 4,
            tension: 0.25,
            fill: true,
            yAxisID: "y"
          },
          {
            label: "Температура (°C)",
            data: tempData,
            borderColor: "#888888",
            borderDash: [5, 5],
            borderWidth: 2,
            pointBackgroundColor: "#888888",
            pointRadius: 3,
            backgroundColor: "transparent",
            tension: 0.25,
            yAxisID: "yTemp"
          }
        ]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: {
            labels: {
              color: "#cccccc",
              font: { family: "'JetBrains Mono', monospace", size: 11 }
            }
          },
          tooltip: {
            backgroundColor: "#000000",
            titleColor: "#ffffff",
            bodyColor: "#cccccc",
            borderColor: "#444444",
            borderWidth: 1,
            mode: "index",
            intersect: false
          }
        },
        scales: {
          x: {
            ticks: { color: "#888888", font: { family: "'JetBrains Mono', monospace" } },
            grid: { color: "rgba(255, 255, 255, 0.08)" }
          },
          y: {
            title: { display: true, text: "Кислород (мг/л)", color: "#ffffff", font: { family: "'JetBrains Mono', monospace" } },
            ticks: { color: "#ffffff" },
            grid: { color: "rgba(255, 255, 255, 0.08)" }
          },
          yTemp: {
            position: "right",
            title: { display: true, text: "Темп. (°C)", color: "#888888", font: { family: "'JetBrains Mono', monospace" } },
            ticks: { color: "#888888" },
            grid: { drawOnChartArea: false }
          }
        }
      }
    });
  } catch (err) {
    console.error("loadStationChart error:", err);
  }
}

// ==========================================
// 4. Лента оперативных алертов
// ==========================================
async function loadAlerts(resolvedFilter = false) {
  currentAlertFilterResolved = resolvedFilter;
  const feed = document.getElementById("alerts-feed");

  try {
    let url = `${API_BASE}/api/alerts`;
    if (resolvedFilter !== null) {
      url += `?resolved=${resolvedFilter}`;
    }
    const res = await fetch(url);
    if (!res.ok) throw new Error("Ошибка загрузки алертов");
    const alerts = await res.json();

    const activeCount = alerts.filter(a => !a.resolved).length;
    document.getElementById("stat-alerts").innerText = activeCount;

    if (alerts.length === 0) {
      feed.innerHTML = `<div class="loading-placeholder">Нет активных предупреждений. Экосистема в норме.</div>`;
      return;
    }

    feed.innerHTML = alerts.map(a => `
      <div class="alert-item severity-${a.severity}">
        <div class="alert-header">
          <span class="alert-badge badge-${a.severity.toLowerCase()}">${a.severity}</span>
          <span class="alert-time">${new Date(a.createdAt).toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'})}</span>
        </div>
        <div class="alert-msg">${escapeHtml(a.message)}</div>
        ${a.details ? `<div class="alert-details">${escapeHtml(a.details)}</div>` : ''}
        <div class="alert-footer">
          <span class="alert-station">📍 ${escapeHtml(a.stationName || a.stationCode || 'Буй')}</span>
          ${!a.resolved ? `<button class="btn btn-sm btn-outline" onclick="resolveAlert(${a.id})">✓ Отработано</button>` : `<span style="font-size: 11px; color: #10b981;">Решено</span>`}
        </div>
      </div>
    `).join("");
  } catch (err) {
    console.error("loadAlerts error:", err);
    feed.innerHTML = `<div class="loading-placeholder">Ошибка загрузки предупреждений.</div>`;
  }
}

async function resolveAlert(alertId) {
  try {
    const res = await fetch(`${API_BASE}/api/alerts/${alertId}/resolve`, { method: "PATCH" });
    if (res.ok) {
      loadAlerts(currentAlertFilterResolved);
      loadStations();
    }
  } catch (err) {
    console.error("resolveAlert error:", err);
  }
}

function filterAlerts(resolved) {
  document.getElementById("btn-filter-active").classList.toggle("active", resolved === false);
  document.getElementById("btn-filter-all").classList.toggle("active", resolved === null);
  loadAlerts(resolved);
}

// ==========================================
// 5. Модуль воспроизведения звуков и акустического ИИ
// ==========================================
let currentSoundScenario = "boat";
let currentSelectedDeviceId = "DEV-CASP-03";
let devicesCache = {};
let visualizerAnimationId = null;
let isStreamingAudio = false;

const SCENARIO_AUDIO_MAP = {
  boat: {
    file: "samples/boat_engine_outboard.wav",
    title: "🚤 Образец звука мотора",
    deviceId: "DEV-CASP-03"
  },
  shot: {
    file: "samples/poaching_gunfire_shot.wav",
    title: "💥 Оружейный выстрел",
    deviceId: "DEV-CASP-03"
  },
  splash: {
    file: "samples/sturgeon_water_splash.wav",
    title: "🐟 Образец звука всплеска",
    deviceId: "DEV-TRE-02"
  },
  ambient: {
    file: "samples/ambient_caspian_waves.wav",
    title: "🌊 Фоновый шум волн Каспия",
    deviceId: "DEV-TUL-05"
  }
};

async function loadRecordingDevices() {
  try {
    const res = await fetch(`${API_BASE}/api/audio/devices`);
    if (!res.ok) return;
    const devices = await res.json();
    devices.forEach(d => {
      devicesCache[d.deviceId] = d;
    });
  } catch (err) {}
}

function onHydrophoneSelected(deviceId) {
  currentSelectedDeviceId = deviceId;
}

function updateDeviceTelemetryView(deviceId) {}
function toggleDeviceSentry() {}
function toggleAutonomousMonitoring(enable) {}

async function playAndAnalyzeSound(scenario) {
  currentSoundScenario = scenario;
  const item = SCENARIO_AUDIO_MAP[scenario] || SCENARIO_AUDIO_MAP.boat;
  const soundUrl = item.file;

  // 1. Воспроизведение звука в плеере
  playAudioStreamUrl(soundUrl, item.title);

  // 2. ИИ-детекция
  const resultBox = document.getElementById("audio-result");
  if (resultBox) {
    resultBox.classList.remove("hidden");
    resultBox.innerHTML = `<div>⏳ Запуск спектрального анализа ИИ для звука: <strong>${escapeHtml(item.title)}</strong>...</div>`;
  }

  try {
    const res = await fetch(`${API_BASE}/api/audio/devices/${item.deviceId}/capture?scenario=${encodeURIComponent(scenario)}`, {
      method: "POST"
    });
    await requireAiResponse(res);
    const data = await res.json();

    displayAudioResult(data, soundUrl);
    loadAlerts(currentAlertFilterResolved);
    loadStations();
  } catch (err) {
    resultBox.textContent = 'Анализ недоступен: ' + err.message;
  }
}
async function requireAiResponse(res) {
  if (res.ok) return;
  let error;
  try { error = await res.json(); } catch (_) {}
  const message = error?.message || error?.detail || error?.error || ('HTTP ' + res.status);
  throw new Error(typeof message === 'string' ? message : JSON.stringify(message));
}
function encodePcmWav(buffer) {
  const bytes = new ArrayBuffer(44 + buffer.length * 2), view = new DataView(bytes);
  const write = (o, t) => { for (let i=0;i<t.length;i++) view.setUint8(o+i,t.charCodeAt(i)); };
  write(0,'RIFF'); view.setUint32(4,36+buffer.length*2,true); write(8,'WAVE'); write(12,'fmt ');
  view.setUint32(16,16,true); view.setUint16(20,1,true); view.setUint16(22,1,true);
  view.setUint32(24,buffer.sampleRate,true); view.setUint32(28,buffer.sampleRate*2,true);
  view.setUint16(32,2,true); view.setUint16(34,16,true); write(36,'data'); view.setUint32(40,buffer.length*2,true);
  const channels = Array.from({length:buffer.numberOfChannels},(_,i)=>buffer.getChannelData(i));
  for(let i=0;i<buffer.length;i++) {
    const sample = Math.max(-1,Math.min(1,channels.reduce((sum,c)=>sum+c[i],0)/channels.length));
    view.setInt16(44+i*2,sample<0?sample*32768:sample*32767,true);
  }
  return new Blob([bytes],{type:'audio/wav'});
}

function playAudioStreamUrl(url, titleText = "") {
  const player = document.getElementById("device-audio-player");
  const btn = document.getElementById("btn-toggle-stream");
  const streamTitle = document.getElementById("stream-dev-title");

  if (!player) return;

  if (streamTitle && titleText) {
    streamTitle.innerText = titleText;
  }

  player.pause();
  player.src = url;
  player.volume = 0.9;
  player.load();

  const playPromise = player.play();
  if (playPromise !== undefined) {
    playPromise.then(() => {
      isStreamingAudio = true;
      if (btn) btn.innerText = "⏹ Остановить";
      startVisualizerCanvas();
    }).catch(err => {
      console.warn("Audio autoplay blocked by browser policy, user can press play:", err);
      // Fallback relative path
      const rel = url.replace(/^https?:\/\/[^\/]+\//, "").replace(/^\//, "");
      if (rel !== url) {
        player.src = rel;
        player.load();
        player.play().catch(e => console.warn("Fallback play error:", e));
      }
    });
  }

  player.onplay = () => {
    isStreamingAudio = true;
    if (btn) btn.innerText = "⏹ Остановить";
    startVisualizerCanvas();
  };

  player.onpause = () => {
    isStreamingAudio = false;
    if (btn) btn.innerText = "▶ Воспроизвести";
    stopVisualizerCanvas();
  };

  player.onended = () => {
    isStreamingAudio = false;
    if (btn) btn.innerText = "▶ Воспроизвести";
    stopVisualizerCanvas();
  };
}

function toggleAudioPlayback() {
  const player = document.getElementById("device-audio-player");
  const btn = document.getElementById("btn-toggle-stream");

  if (!player) return;

  if (isStreamingAudio || !player.paused) {
    player.pause();
    isStreamingAudio = false;
    if (btn) btn.innerText = "▶ Воспроизвести";
    stopVisualizerCanvas();
  } else {
    if (!player.src || player.src === "" || player.src.endsWith("#")) {
      const item = SCENARIO_AUDIO_MAP[currentSoundScenario] || SCENARIO_AUDIO_MAP.boat;
      playAudioStreamUrl(item.file, item.title);
    } else {
      player.play().then(() => {
        isStreamingAudio = true;
        if (btn) btn.innerText = "⏹ Остановить";
        startVisualizerCanvas();
      }).catch(e => console.log("Play error:", e));
    }
  }
}

// Алиасы обратной совместимости
function triggerDirectTest(scenario) { playAndAnalyzeSound(scenario); }
function toggleDeviceLiveStream() { toggleAudioPlayback(); }
function startDeviceAudioStream(scenario) {
  const item = SCENARIO_AUDIO_MAP[scenario] || SCENARIO_AUDIO_MAP[currentSoundScenario] || SCENARIO_AUDIO_MAP.boat;
  playAudioStreamUrl(item.file, item.title);
}

// Canvas spectrum visualizer
function initVisualizerCanvas() {
  const canvas = document.getElementById("audio-spectrum-canvas");
  if (!canvas) return;
  const ctx = canvas.getContext("2d");
  drawIdleSpectrum(ctx, canvas.width, canvas.height);
}

function drawIdleSpectrum(ctx, w, h) {
  ctx.fillStyle = "#000000";
  ctx.fillRect(0, 0, w, h);
  ctx.strokeStyle = "rgba(255, 255, 255, 0.25)";
  ctx.beginPath();
  ctx.moveTo(0, h / 2);
  ctx.lineTo(w, h / 2);
  ctx.stroke();
}

function startVisualizerCanvas() {
  const canvas = document.getElementById("audio-spectrum-canvas");
  if (!canvas) return;
  const ctx = canvas.getContext("2d");
  const w = canvas.width;
  const h = canvas.height;

  let phase = 0;
  function animate() {
    ctx.fillStyle = "rgba(0, 0, 0, 0.35)";
    ctx.fillRect(0, 0, w, h);

    const bars = 48;
    const barWidth = (w / bars) - 2;

    for (let i = 0; i < bars; i++) {
      const freq = Math.sin(phase + i * 0.3) * 0.5 + 0.5;
      const noise = Math.random() * 0.4;
      const barHeight = Math.max(4, (freq * 0.7 + noise * 0.3) * (h - 8));
      const x = i * (barWidth + 2);
      const y = h - barHeight - 4;

      const grad = ctx.createLinearGradient(0, y, 0, h);
      grad.addColorStop(0, "#ffffff");
      grad.addColorStop(0.5, "#cccccc");
      grad.addColorStop(1, "#444444");

      ctx.fillStyle = grad;
      ctx.fillRect(x, y, barWidth, barHeight);
    }

    phase += 0.15;
    if (isStreamingAudio) {
      visualizerAnimationId = requestAnimationFrame(animate);
    }
  }

  if (visualizerAnimationId) cancelAnimationFrame(visualizerAnimationId);
  visualizerAnimationId = requestAnimationFrame(animate);
}

function stopVisualizerCanvas() {
  if (visualizerAnimationId) {
    cancelAnimationFrame(visualizerAnimationId);
    visualizerAnimationId = null;
  }
  const canvas = document.getElementById("audio-spectrum-canvas");
  if (canvas) {
    const ctx = canvas.getContext("2d");
    drawIdleSpectrum(ctx, canvas.width, canvas.height);
  }
}

// Direct Microphone / Inspector Terminal Capture
async function toggleMicRecording() {
  const btn = document.getElementById("btn-mic-record");
  const hint = document.getElementById("mic-status-hint");

  if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
    alert("Доступ к локальному микрофону не поддерживается вашим браузером. Используйте захват с гидрофона ООПТ.");
    return;
  }

  try {
    hint.innerText = "Подключение к полевому микрофону...";
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    mediaRecorderInstance = new MediaRecorder(stream);
    micAudioChunks = [];

    mediaRecorderInstance.ondataavailable = e => {
      if (e.data.size > 0) micAudioChunks.push(e.data);
    };

    mediaRecorderInstance.onstop = async () => {
      const recordedBlob = new Blob(micAudioChunks, { type: mediaRecorderInstance.mimeType });
      stream.getTracks().forEach(t => t.stop());
      hint.innerText = "⏳ Отправка на анализ нейросетью...";

      try {
        const Context = window.AudioContext || window.webkitAudioContext;
        const context = new Context();
        let audioBlob;
        try { audioBlob = encodePcmWav(await context.decodeAudioData(await recordedBlob.arrayBuffer())); }
        finally { await context.close(); }
        const formData = new FormData();
        formData.append('file',audioBlob,'field_mic_capture.wav');
        formData.append('stationId',document.getElementById('station-select-audio').value);
        const res = await fetch(`${API_BASE}/api/audio/upload`, {
          method: "POST",
          body: formData
        });
        await requireAiResponse(res);
        const data = await res.json();
        const blobUrl = URL.createObjectURL(audioBlob);
        displayAudioResult(data, blobUrl);
        hint.innerText = "✓ Запись проанализирована";
        btn.disabled = false;
        btn.innerText = "🎙️ Захват с физического микрофона/терминала (3 сек)";
        loadAlerts(currentAlertFilterResolved);
        loadStations();
      } catch (err) {
        hint.innerText = "Ошибка: " + err.message;
        btn.disabled = false;
        btn.innerText = "🎙️ Захват с физического микрофона/терминала (3 сек)";
      }
    };

    mediaRecorderInstance.start();
    btn.disabled = true;

    let timeLeft = 3;
    hint.innerText = `🔴 Идёт запись звука: ${timeLeft} сек...`;
    const interval = setInterval(() => {
      timeLeft--;
      if (timeLeft > 0) {
        hint.innerText = `🔴 Идёт запись звука: ${timeLeft} сек...`;
      } else {
        clearInterval(interval);
        if (mediaRecorderInstance && mediaRecorderInstance.state === "recording") {
          mediaRecorderInstance.stop();
        }
      }
    }, 1000);

  } catch (err) {
    hint.innerText = "Микрофон недоступен: " + err.message;
    console.warn("getUserMedia error:", err);
  }
}

// Fallback file upload from disk & Live Audio Preview
let filePreviewAudio = null;

function onAudioFileSelected(input) {
  const banner = document.getElementById("file-preview-banner");
  if (!input.files || input.files.length === 0) return;

  const file = input.files[0];
  if (banner) {
    banner.classList.remove("hidden");
    banner.innerHTML = `🔊 <strong>${escapeHtml(file.name)}</strong>: выбран, воспроизведение образца...`;
  }

  // Play subtle feedback chirp
  playSubtleFeedbackChirp();

  if (filePreviewAudio) {
    filePreviewAudio.pause();
    filePreviewAudio = null;
  }

  const audioUrl = URL.createObjectURL(file);
  filePreviewAudio = new Audio(audioUrl);
  filePreviewAudio.volume = 0.55;
  filePreviewAudio.play().then(() => {
    startVisualizerCanvas();
  }).catch(e => {
    console.log("Audio preview autoplay:", e);
  });

  filePreviewAudio.onended = () => {
    stopVisualizerCanvas();
  };
}

function playSubtleFeedbackChirp() {
  try {
    const AudioContextClass = window.AudioContext || window.webkitAudioContext;
    if (!AudioContextClass) return;
    const ctx = new AudioContextClass();
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();

    osc.type = "sine";
    osc.frequency.setValueAtTime(587.33, ctx.currentTime);
    osc.frequency.exponentialRampToValueAtTime(880, ctx.currentTime + 0.12);

    gain.gain.setValueAtTime(0.12, ctx.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.18);

    osc.connect(gain);
    gain.connect(ctx.destination);

    osc.start();
    osc.stop(ctx.currentTime + 0.18);
  } catch (err) {}
}

async function uploadAudio() {
  const fileInput = document.getElementById("audio-file-input");
  const resultBox = document.getElementById("audio-result");

  if (!fileInput.files || fileInput.files.length === 0) {
    alert("Пожалуйста, выберите аудиофайл для анализа.");
    return;
  }

  const stationSelect = document.getElementById("station-select-audio");
  const stationId = stationSelect ? stationSelect.value : "1";

  const file = fileInput.files[0];
  const header = new Uint8Array(await file.slice(0,12).arrayBuffer());
  const tag = (a,b) => String.fromCharCode(...header.slice(a,b));
  if (!/\.wav$/i.test(file.name) || tag(0,4)!=='RIFF' || tag(8,12)!=='WAVE') {
    resultBox.classList.remove('hidden');
    resultBox.textContent = 'Требуется настоящий WAV (RIFF/WAVE). Экспортируйте запись в WAV; переименование MP3 не меняет формат.';
    return;
  }
  const formData = new FormData();
  formData.append('file',file);
  formData.append("stationId", stationId);

  resultBox.classList.remove("hidden");
  resultBox.innerHTML = `<div>⏳ Выполняется нейросетевая спектрограмма и инференс аудиомодели для файла <strong>${escapeHtml(file.name)}</strong>...</div>`;

  try {
    const res = await fetch(`${API_BASE}/api/audio/upload`, {
      method: "POST",
      body: formData
    });
    await requireAiResponse(res);
    const data = await res.json();
    const localUrl = URL.createObjectURL(file);

    playAudioStreamUrl(localUrl, `Запись: ${file.name}`);
    displayAudioResult(data, localUrl);
    loadAlerts(currentAlertFilterResolved);
    loadStations();
  } catch (err) {
    resultBox.innerHTML = `<div style="color: #ef4444;">Ошибка анализа: ${escapeHtml(err.message)}</div>`;
  }
}

function displayAudioResult(data, audioUrl = null) {
  const resultBox = document.getElementById("audio-result");
  resultBox.classList.remove("hidden");

  const isCrit = data.threatLevel === "CRITICAL";
  const isHigh = data.threatLevel === "HIGH";
  const badgeStyle = isCrit
    ? "background: #ffffff; color: #000000; font-weight: 800; border: 1px solid #ffffff;"
    : (isHigh ? "background: #777777; color: #ffffff; font-weight: 700;" : "background: transparent; color: #ffffff; border: 1px solid #444;");

  const classNamesRu = {
    boat_suspected: 'Возможный звук лодки', gunshot_suspected: 'Возможный импульсный звук / выстрел',
    engine_detected: 'Звук двигателя (тип не определён)', uncertain: 'Неопределённый результат',
    no_target_event: 'Целевое событие не обнаружено в записи'
  };
  const titleRu = classNamesRu[data.detectedClass] || data.detectedClass;

  resultBox.innerHTML = `
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px;">
      <strong style="font-size: 14px; color: #ffffff;">${escapeHtml(titleRu)}</strong>
      <span style="${badgeStyle} padding: 3px 8px; border-radius: 3px; font-size: 11px; font-family: var(--font-mono);">
        ПРОВЕРКА: ${escapeHtml(data.threatLevel === "REVIEW" ? "ТРЕБУЕТ ОЦЕНКИ ИНСПЕКТОРА" : data.threatLevel)}
      </span>
    </div>
    ${audioUrl ? `
      <div style="margin-bottom: 8px; display: flex; align-items: center; justify-content: space-between; gap: 8px; background: #0a0a0a; border: 1px solid #222; padding: 6px 12px; border-radius: 4px;">
        <span style="font-size: 11px; color: #888; font-family: var(--font-mono);">🎧 Запись инцидента с гидрофона:</span>
        <audio id="result-audio-player" controls src="${audioUrl}" style="height: 28px;"></audio>
      </div>
    ` : ''}
    <div style="font-size: 12px; color: #888888; line-height: 1.5; font-family: var(--font-mono);">
      Оценка модели (не вероятность): <strong style="color: #ffffff;">${Number.isFinite(data.confidence) ? data.confidence.toFixed(3) : "нет данных"}</strong><br/>
      Модель: ${escapeHtml(data.analysisDetails?.model_version || "не указана")}<br/>
      События / интервалы: ${escapeHtml(JSON.stringify(data.analysisDetails?.events || []))}<br/>
      Источник анализа: <span style="color: #cccccc;">${escapeHtml(data.analysisSource)}</span><br/>
      ${data.alertGenerated ? `<span style="color: #ffffff; font-weight: bold; background: #222; padding: 2px 6px; border-radius: 3px; display: inline-block; margin-top: 4px; border: 1px solid #555;">🚨 Сформировано оперативное предупреждение на карте</span>` : `<span style="color: #aaaaaa;">Автоматическое предупреждение не сформировано; требуется оценка инспектора.</span>`}
    </div>
  `;
}

// ==========================================
// 6. Внешний демонстрационный сценарий условий
// ==========================================
async function loadForecast() {
  const species = document.getElementById('species-select').value;
  const stationId = document.getElementById('forecast-station-select').value;
  const card = document.getElementById('forecast-card');
  card.textContent = 'Запрос сценария условий...';
  try {
    const res = await fetch(API_BASE + '/api/forecast/spawning?species=' + encodeURIComponent(species) + '&stationId=' + stationId);
    await requireAiResponse(res);
    const data = await res.json(), d = data.forecastDetails || {};
    const list = values => (values || []).map(v => '<li>' + escapeHtml(typeof v === 'string' ? v : JSON.stringify(v)) + '</li>').join('');
    const rows = (d.trajectory || []).map(r => '<tr><td>'+escapeHtml(r.timestamp)+'</td><td>'+escapeHtml(r.temperature_c ?? 'нет данных')+'</td><td>'+escapeHtml(r.water_level_m ?? 'нет данных')+'</td><td>'+escapeHtml(r.conditions)+'</td></tr>').join('');
    card.innerHTML = '<div class="gauge-details"><strong>Сценарий условий для сазана / карпа — демонстрация</strong><span>Режим: '+escapeHtml(d.mode)+' · Валидация: '+escapeHtml(d.validation_status)+'</span><span>Будущее окно: '+escapeHtml(d.valid_from)+' — '+escapeHtml(d.valid_to)+'</span><span>Источник: '+escapeHtml(d.data_source)+' · Модель: '+escapeHtml(data.modelVersion)+'</span><span>Вероятность фактического нереста не рассчитана.</span></div><table style="width:100%;font-size:12px"><thead><tr><th>Время</th><th>Температура °C</th><th>Уровень м</th><th>Условия</th></tr></thead><tbody>'+rows+'</tbody></table><div class="recommendation-box"><strong>Факторы:</strong><ul>'+list(d.factors)+'</ul><strong>Недостающие данные:</strong><ul>'+list(d.missing_data)+'</ul><strong>Ограничения:</strong><ul>'+list(d.warnings)+'</ul></div>';
  } catch (err) { card.textContent = 'Сценарий недоступен: '+err.message; }
}

// ==========================================
// 7. IoT Симулятор телеметрии
// ==========================================
async function submitTelemetry(e) {
  e.preventDefault();
  const stationId = parseInt(document.getElementById("sim-station").value);
  const oxygen = parseFloat(document.getElementById("sim-oxygen").value);
  const temp = parseFloat(document.getElementById("sim-temp").value);
  const salinity = parseFloat(document.getElementById("sim-salinity").value);
  const ph = parseFloat(document.getElementById("sim-ph").value);
  const level = parseFloat(document.getElementById("sim-level").value);

  const payload = {
    stationId: stationId,
    dissolvedOxygenMgL: oxygen,
    temperatureC: temp,
    salinityPpt: salinity,
    ph: ph,
    turbidityNtu: 18.0,
    waterLevelCm: level
  };

  try {
    const res = await fetch(`${API_BASE}/api/telemetry/water`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
    if (!res.ok) throw new Error("Ошибка отправки телеметрии");
    const result = await res.json();

    alert(`Измерение успешно сохранено! Статус качества: ${result.qualityStatus}. Создано тревог: ${result.generatedAlertsCount}`);
    loadStations();
    loadAlerts(currentAlertFilterResolved);
    loadStationChart(stationId);
  } catch (err) {
    alert("Ошибка: " + err.message);
  }
}

function applyPreset(type) {
  if (type === "critical") {
    document.getElementById("sim-oxygen").value = "2.8";
    document.getElementById("sim-temp").value = "14.2";
    document.getElementById("sim-salinity").value = "1.2";
  } else {
    document.getElementById("sim-oxygen").value = "8.4";
    document.getElementById("sim-temp").value = "12.0";
    document.getElementById("sim-salinity").value = "0.7";
  }
}

// ==========================================
// 8. Общие утилиты
// ==========================================
function switchTab(tabId, btnElement) {
  document.querySelectorAll(".tab-btn").forEach(b => b.classList.remove("active"));
  document.querySelectorAll(".tab-content").forEach(c => c.classList.remove("active"));

  if (btnElement) {
    btnElement.classList.add("active");
  } else if (window.event && window.event.target) {
    const target = window.event.target.closest(".tab-btn") || window.event.target;
    target.classList.add("active");
  }

  const targetTab = document.getElementById(tabId);
  if (targetTab) {
    targetTab.classList.add("active");
  }

  if (tabId === "tab-forecast") {
    loadForecast();
  }
}

function refreshAllData() {
  loadStations();
  loadStationChart(currentStationId);
  loadAlerts(currentAlertFilterResolved);
  loadForecast();
}

function escapeHtml(str) {
  if (str === null || str === undefined) return "";
  return String(str).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}


function updateStationSelect(stations, selectorId) {
  const select = document.getElementById(selectorId);
  if (!select) return;
  const previous = select.value;
  const options = stations.map(station => {
    const option = document.createElement('option');
    option.value = String(station.id);
    option.textContent = station.name;
    return option;
  });
  select.replaceChildren(...options);
  select.disabled = stations.length === 0;
  if (stations.some(station => String(station.id) === previous)) select.value = previous;
}
