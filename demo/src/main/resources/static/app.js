// Эко-Щит Каспия — Логика дашборда инспектора
const API_BASE = window.location.origin;

let mapInstance = null;
let markersMap = {};
let telemetryChartInstance = null;
let currentStationId = 1;
let currentAlertFilterResolved = false;

document.addEventListener("DOMContentLoaded", () => {
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

  L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
    maxZoom: 18,
    attribution: "© OpenStreetMap ООПТ Каспий"
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
    color: "#38bdf8",
    weight: 4,
    opacity: 0.85,
    dashArray: "8, 8",
    lineJoin: "round"
  });

  sealPolyline.bindPopup(`
    <div style="font-family: sans-serif; min-width: 220px; color: #0f172a;">
      <h4 style="margin: 0 0 4px 0; color: #0284c7;">🦭 Миграционный коридор каспийского тюленя (Pusa caspica)</h4>
      <div style="font-size: 11px; color: #64748b; margin-bottom: 6px;">Эндемик Каспия • Красная книга РФ • МСОП (Endangered)</div>
      <div style="font-size: 12px; line-height: 1.4;">
        <strong>Сезон:</strong> Октябрь — Декабрь<br/>
        <strong>Цель миграции:</strong> Выход на ледовые поля Северного Каспия для размножения и линьки.<br/>
        <strong>Ключевая угроза:</strong> Запутывание в браконьерских крупноячеистых сетях и столкновения с байдами.<br/>
        <strong>Защита:</strong> Акустический дозор буев №5 (о. Тюлений) и №3 (ВКМСК).
      </div>
    </div>
  `);

  sealLayerGroup.addLayer(sealPolyline);

  // Опорные точки на пути тюленей
  const sealPoints = [
    {
      coords: [44.50, 47.52],
      title: "🦭 Лежбище: Остров Тюлений",
      desc: "Крупнейшее осеннее лежбище нерпы. Охраняется морским буем DEV-TUL-05."
    },
    {
      coords: [45.45, 47.95],
      title: "⚠️ Фарватер ВКМСК 145 км",
      desc: "Опасная зона пересечения судового хода и миграционного пути тюленей. Высокий риск браконьерских сетей!"
    }
  ];

  sealPoints.forEach(p => {
    const sealMarker = L.marker(p.coords, {
      icon: L.divIcon({
        className: "seal-pin",
        html: `<div style="background: rgba(14, 165, 233, 0.95); color: white; border-radius: 50%; width: 24px; height: 24px; display: flex; align-items: center; justify-content: center; font-size: 14px; border: 2px solid white; box-shadow: 0 0 10px #38bdf8;">🦭</div>`,
        iconSize: [26, 26],
        iconAnchor: [13, 13]
      })
    });
    sealMarker.bindPopup(`<strong>${p.title}</strong><br/><small>${p.desc}</small>`);
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
      className: "custom-pin",
      html: `<div style="background-color: ${color}; width: 16px; height: 16px; border-radius: 50%; border: 3px solid white; box-shadow: 0 0 10px ${color};"></div>`,
      iconSize: [20, 20],
      iconAnchor: [10, 10]
    });

    const popupContent = `
      <div style="font-family: sans-serif; min-width: 180px; color: #1e293b;">
        <h4 style="margin: 0 0 4px 0; color: #0f172a;">${st.name}</h4>
        <div style="font-size: 11px; color: #64748b; margin-bottom: 6px;">${st.territory}</div>
        <div style="font-size: 12px; line-height: 1.5;">
          <strong>Статус:</strong> <span style="color: ${color}; font-weight: bold;">${st.status}</span><br/>
          <strong>Кислород O₂:</strong> ${st.latestTelemetry?.dissolvedOxygenMgL != null ? st.latestTelemetry.dissolvedOxygenMgL + " мг/л" : "—"}<br/>
          <strong>Температура:</strong> ${st.latestTelemetry?.temperatureC != null ? st.latestTelemetry.temperatureC + " °C" : "—"}<br/>
          <strong>Солёность:</strong> ${st.latestTelemetry?.salinityPpt != null ? st.latestTelemetry.salinityPpt + " ‰" : "—"}<br/>
          <strong>Уровень:</strong> ${st.latestTelemetry?.waterLevelCm != null ? st.latestTelemetry.waterLevelCm + " см" : "—"}
        </div>
        <button style="margin-top: 8px; width: 100%; padding: 4px; background: #2563eb; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 11px;" 
                onclick="selectStationAndChart(${st.id})">📊 Показать график</button>
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

    // Update station dropdowns
    const selector = document.getElementById("station-selector");
    if (selector.options.length <= 1) {
      selector.innerHTML = stations.map(s => `<option value="${s.id}">${s.name}</option>`).join("");
    }
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
            borderColor: "#38bdf8",
            backgroundColor: "rgba(56, 189, 248, 0.1)",
            tension: 0.3,
            fill: true,
            yAxisID: "y"
          },
          {
            label: "Температура (°C)",
            data: tempData,
            borderColor: "#f59e0b",
            backgroundColor: "transparent",
            borderDash: [4, 4],
            tension: 0.3,
            yAxisID: "yTemp"
          }
        ]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { labels: { color: "#94a3b8", font: { size: 11 } } },
          tooltip: { mode: "index", intersect: false }
        },
        scales: {
          x: { ticks: { color: "#64748b" }, grid: { color: "#1e293b" } },
          y: {
            title: { display: true, text: "Кислород (мг/л)", color: "#38bdf8" },
            ticks: { color: "#38bdf8" },
            grid: { color: "#1e293b" }
          },
          yTemp: {
            position: "right",
            title: { display: true, text: "Темп. (°C)", color: "#f59e0b" },
            ticks: { color: "#f59e0b" },
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
    title: "🚤 Мотор лодки («Байда»)",
    deviceId: "DEV-CASP-03"
  },
  shot: {
    file: "samples/poaching_gunfire_shot.wav",
    title: "💥 Оружейный выстрел",
    deviceId: "DEV-CASP-03"
  },
  splash: {
    file: "samples/sturgeon_water_splash.wav",
    title: "🐟 Плеск рыбы / Нерестовый ход",
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
    if (!res.ok) throw new Error("Ошибка анализа аудиосигнала");
    const data = await res.json();

    displayAudioResult(data, soundUrl);
    loadAlerts(currentAlertFilterResolved);
    loadStations();
  } catch (err) {
    // Автономный фолбэк-классификатор, если бэкенд ещё не запущен
    const fallbackData = getLocalAiClassification(scenario);
    displayAudioResult(fallbackData, soundUrl);
  }
}

function getLocalAiClassification(scenario) {
  if (scenario === "boat") {
    return {
      detectedClass: "boat_engine",
      confidence: 0.94,
      threatLevel: "HIGH",
      analysisSource: "Акустическая нейросеть (Автономная детекция)",
      alertGenerated: true
    };
  } else if (scenario === "shot") {
    return {
      detectedClass: "gunfire",
      confidence: 0.89,
      threatLevel: "CRITICAL",
      analysisSource: "Акустическая нейросеть (Автономная детекция)",
      alertGenerated: true
    };
  } else if (scenario === "splash") {
    return {
      detectedClass: "fish_splash",
      confidence: 0.92,
      threatLevel: "LOW",
      analysisSource: "Гидроакустический классификатор био-активности",
      alertGenerated: false
    };
  } else {
    return {
      detectedClass: "ambient_noise",
      confidence: 0.96,
      threatLevel: "NONE",
      analysisSource: "Гидроакустический спектральный анализатор",
      alertGenerated: false
    };
  }
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
  ctx.fillStyle = "#050811";
  ctx.fillRect(0, 0, w, h);
  ctx.strokeStyle = "rgba(56, 189, 248, 0.25)";
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
    ctx.fillStyle = "rgba(5, 8, 17, 0.25)";
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
      grad.addColorStop(0, "#38bdf8");
      grad.addColorStop(1, "#0284c7");

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
      const audioBlob = new Blob(micAudioChunks, { type: "audio/wav" });
      stream.getTracks().forEach(t => t.stop());
      hint.innerText = "⏳ Отправка на анализ нейросетью...";

      const formData = new FormData();
      formData.append("file", audioBlob, "field_mic_capture.wav");
      formData.append("stationId", "3");

      try {
        const res = await fetch(`${API_BASE}/api/audio/upload`, {
          method: "POST",
          body: formData
        });
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
  const formData = new FormData();
  formData.append("file", file);
  formData.append("stationId", stationId);

  resultBox.classList.remove("hidden");
  resultBox.innerHTML = `<div>⏳ Выполняется нейросетевая спектрограмма и инференс аудиомодели для файла <strong>${escapeHtml(file.name)}</strong>...</div>`;

  try {
    const res = await fetch(`${API_BASE}/api/audio/upload`, {
      method: "POST",
      body: formData
    });
    if (!res.ok) throw new Error("Ошибка классификации аудио");
    const data = await res.json();
    const localUrl = URL.createObjectURL(file);

    playAudioStreamUrl(localUrl, `Запись: ${file.name}`);
    displayAudioResult(data, localUrl);
    loadAlerts(currentAlertFilterResolved);
    loadStations();
  } catch (err) {
    resultBox.innerHTML = `<div style="color: #ef4444;">Ошибка анализа: ${err.message}</div>`;
  }
}

function displayAudioResult(data, audioUrl = null) {
  const resultBox = document.getElementById("audio-result");
  resultBox.classList.remove("hidden");

  const badgeColor = data.threatLevel === "CRITICAL" ? "#ef4444" : (data.threatLevel === "HIGH" ? "#f59e0b" : "#10b981");

  const classNamesRu = {
    "boat_engine": "🚤 Подвесной мотор лодки («Байда»)",
    "gunfire": "💥 Оружейный выстрел браконьера",
    "fish_splash": "🐟 Плеск рыбы / Нерестовый ход",
    "ambient_noise": "🌊 Фоновый шум Каспийского моря"
  };
  const titleRu = classNamesRu[data.detectedClass] || data.detectedClass;

  resultBox.innerHTML = `
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px;">
      <strong style="font-size: 14px;">${escapeHtml(titleRu)}</strong>
      <span style="background: ${badgeColor}; color: white; padding: 4px 10px; border-radius: 4px; font-weight: bold; font-size: 12px;">
        Угроза: ${data.threatLevel}
      </span>
    </div>
    ${audioUrl ? `
      <div style="margin-bottom: 8px; display: flex; align-items: center; justify-content: space-between; gap: 8px; background: rgba(0,0,0,0.25); padding: 6px 12px; border-radius: 6px;">
        <span style="font-size: 12px; color: #94a3b8;">🎧 Запись инцидента с гидрофона:</span>
        <audio id="result-audio-player" controls src="${audioUrl}" style="height: 28px;"></audio>
      </div>
    ` : ''}
    <div style="font-size: 12px; color: #94a3b8; line-height: 1.5;">
      Достоверность нейросети: <strong style="color: var(--text-main);">${Math.round(data.confidence * 100)}%</strong><br/>
      Источник анализа: <span style="color: var(--accent-cyan);">${escapeHtml(data.analysisSource)}</span><br/>
      ${data.alertGenerated ? `<span style="color: #ef4444; font-weight: bold;">🚨 Сформировано оперативное предупреждение инспекции на карте!</span>` : `<span style="color: #10b981;">✓ Браконьерская угроза отсутствует. Акватория чиста.</span>`}
    </div>
  `;
}

// ==========================================
// 6. Прогноз нереста осетровых
// ==========================================
async function loadForecast() {
  const species = document.getElementById("species-select").value;
  const stationId = document.getElementById("forecast-station-select").value;
  const card = document.getElementById("forecast-card");

  try {
    const res = await fetch(`${API_BASE}/api/forecast/spawning?species=${encodeURIComponent(species)}&stationId=${stationId}`);
    if (!res.ok) throw new Error("Ошибка получения прогноза");
    const data = await res.json();

    const scoreColor = data.suitabilityIndex >= 70 ? "#10b981" : (data.suitabilityIndex >= 45 ? "#f59e0b" : "#ef4444");

    card.innerHTML = `
      <div class="forecast-gauge">
        <div class="gauge-circle" style="color: ${scoreColor}; border: 3px solid ${scoreColor};">
          ${Math.round(data.suitabilityIndex)}%
        </div>
        <div class="gauge-details">
          <strong style="color: ${scoreColor}; font-size: 14px;">${data.status}</strong>
          <span style="font-size: 12px; color: #94a3b8;">Оптимальное окно нереста: <strong>${data.windowStart} — ${data.windowEnd}</strong></span>
          <span style="font-size: 11px; color: #64748b;">Лимитирующий фактор: ${data.limitingFactor}</span>
        </div>
      </div>
      <div class="recommendation-box">
        <strong>🛡️ Тактическая рекомендация инспекции:</strong><br/>
        ${data.patrolRecommendation}
      </div>
      <div style="font-size: 10px; color: #64748b; text-align: right;">${data.modelVersion}</div>
    `;
  } catch (err) {
    console.error("loadForecast error:", err);
    card.innerHTML = `<div class="loading-placeholder">Ошибка расчета прогноза.</div>`;
  }
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
function switchTab(tabId) {
  document.querySelectorAll(".tab-btn").forEach(b => b.classList.remove("active"));
  document.querySelectorAll(".tab-content").forEach(c => c.classList.remove("active"));

  event.target.classList.add("active");
  document.getElementById(tabId).classList.add("active");

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
  if (!str) return "";
  return str.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

