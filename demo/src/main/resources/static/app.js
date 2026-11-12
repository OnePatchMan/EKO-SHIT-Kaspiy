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
// 5. Акустический модуль (Загрузка аудио)
// ==========================================
async function uploadAudio() {
  const fileInput = document.getElementById("audio-file-input");
  const stationId = document.getElementById("audio-station-select").value;
  const resultBox = document.getElementById("audio-result");

  if (!fileInput.files || fileInput.files.length === 0) {
    alert("Пожалуйста, выберите аудиофайл для анализа.");
    return;
  }

  const formData = new FormData();
  formData.append("file", fileInput.files[0]);
  formData.append("stationId", stationId);

  resultBox.classList.remove("hidden");
  resultBox.innerHTML = `<div>⏳ Выполняется нейросетевая спектрограмма и инференс аудиомодели...</div>`;

  try {
    const res = await fetch(`${API_BASE}/api/audio/upload`, {
      method: "POST",
      body: formData
    });
    if (!res.ok) throw new Error("Ошибка классификации аудио");
    const data = await res.json();
    displayAudioResult(data);
    loadAlerts(currentAlertFilterResolved);
    loadStations();
  } catch (err) {
    resultBox.innerHTML = `<div style="color: #ef4444;">Ошибка анализа: ${err.message}</div>`;
  }
}

async function simulateAudioTest(sampleName) {
  const resultBox = document.getElementById("audio-result");
  const stationId = document.getElementById("audio-station-select").value;

  resultBox.classList.remove("hidden");
  resultBox.innerHTML = `<div>⏳ Загрузка реального акустического сэмпла <strong>${sampleName}</strong>...</div>`;

  try {
    const audioUrl = `samples/${sampleName}`;
    const audioResp = await fetch(audioUrl);
    const audioBlob = await audioResp.blob();
    const file = new File([audioBlob], sampleName, { type: "audio/wav" });

    const formData = new FormData();
    formData.append("file", file);
    formData.append("stationId", stationId);

    const res = await fetch(`${API_BASE}/api/audio/upload`, {
      method: "POST",
      body: formData
    });
    const data = await res.json();
    displayAudioResult(data, audioUrl);
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

  resultBox.innerHTML = `
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px;">
      <strong>Вердикт модели: ${escapeHtml(data.detectedClass)}</strong>
      <span style="background: ${badgeColor}; padding: 3px 8px; border-radius: 4px; font-weight: bold; font-size: 11px;">
        Угроза: ${data.threatLevel}
      </span>
    </div>
    ${audioUrl ? `
      <div style="margin-bottom: 8px;">
        <audio controls src="${audioUrl}" style="width: 100%; height: 32px;"></audio>
      </div>
    ` : ''}
    <div style="font-size: 12px; color: #94a3b8; line-height: 1.4;">
      Достоверность ИИ: <strong>${Math.round(data.confidence * 100)}%</strong><br/>
      Источник анализа: ${data.analysisSource}<br/>
      ${data.alertGenerated ? `<span style="color: #ef4444; font-weight: bold;">🚨 Сформировано оперативное предупреждение на карте!</span>` : `Экологическая угроза не обнаружена.`}
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

