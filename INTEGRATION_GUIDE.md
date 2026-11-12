# Руководство по интеграции: Java Бэкенд ↔ Фронтенд ↔ Python AI

Данный документ описывает протоколы, контракты, форматы данных и готовые примеры кода для соединения трёх частей системы «Эко-Щит Каспия»:
1. **Frontend (Участник 1)** $\leftrightarrow$ **Java Backend (Участник 2)**
2. **Java Backend (Участник 2)** $\leftrightarrow$ **Python AI Service (Участник 3)**

---

## ЧАСТЬ 1. Интеграция с Фронтендом (Участник 1)

### 1.1. Базовые параметры подключения
- **Базовый URL API:** `http://localhost:8080`
- **CORS:** Включен на Java-бэкенде для всех источников (`*`) и всех HTTP-методов (`GET`, `POST`, `PATCH`, `PUT`, `DELETE`, `OPTIONS`). Фронтенд может запускаться на `http://localhost:3000`, `http://localhost:5173` (Vite) или любом другом локальном порту без проблем с блокировкой браузером.
- **Формат данных:** `application/json` (для медиа: `multipart/form-data`).

---

### 1.2. TypeScript интерфейсы для Фронтенда

Фронтендер может скопировать эти интерфейсы в свой проект (`types/api.ts`):

```typescript
// Статус станции
export type StationStatus = 'NORMAL' | 'WARNING' | 'CRITICAL' | 'OFFLINE';

// Станция мониторинга (маркер на карте)
export interface Station {
  id: number;
  code: string;
  name: string;
  territory: string;
  latitude: number;
  longitude: number;
  status: StationStatus;
  lastUpdated: string;
  latestTelemetry?: WaterTelemetrySummary;
}

// Краткая сводка телеметрии для карточки буя на карте
export interface WaterTelemetrySummary {
  id: number;
  recordedAt: string;
  temperatureC: number;
  salinityPpt: number;
  dissolvedOxygenMgL: number;
  ph: number;
  turbidityNtu: number;
  waterLevelCm: number;
  qualityStatus: 'NORMAL' | 'WARNING' | 'CRITICAL';
}

// Предупреждение / Инцидент
export interface Alert {
  id: number;
  stationId: number;
  stationCode: string;
  stationName: string;
  territory: string;
  latitude: number;
  longitude: number;
  type: string;
  severity: 'INFO' | 'WARNING' | 'CRITICAL';
  message: string;
  details: string;
  createdAt: string;
  resolved: boolean;
  resolvedAt?: string;
}

// Прогноз нереста осетровых
export interface SpawningForecast {
  species: string;
  targetZone: string;
  calculationTime: string;
  suitabilityIndex: number; // 0 - 100%
  status: string;
  windowStart: string;
  windowEnd: string;
  hydrologySummary: {
    averageTemperatureC: number;
    optimalTemperatureRange: string;
    waterLevelCm: number;
  };
  limitingFactor: string;
  patrolRecommendation: string;
  modelVersion: string;
}
```

---

### 1.3. Примеры запросов на JavaScript / TypeScript

#### А. Загрузка станций и отображение на карте (Leaflet / Mapbox)
```javascript
async function loadStations() {
  try {
    const response = await fetch('http://localhost:8080/api/stations');
    if (!response.ok) throw new Error('Ошибка загрузки станций');
    const stations = await response.json();
    
    stations.forEach(station => {
      // Пример добавления маркера на карту Leaflet
      const markerColor = station.status === 'CRITICAL' ? 'red' : 
                          station.status === 'WARNING' ? 'orange' : 'green';
      
      const popupHtml = `
        <b>${station.name}</b><br/>
        Статус: ${station.status}<br/>
        Кислород O₂: ${station.latestTelemetry?.dissolvedOxygenMgL ?? '—'} мг/л<br/>
        Температура: ${station.latestTelemetry?.temperatureC ?? '—'} °C<br/>
        Соленость: ${station.latestTelemetry?.salinityPpt ?? '—'} ‰
      `;
      // L.marker([station.latitude, station.longitude]).bindPopup(popupHtml).addTo(map);
    });
  } catch (error) {
    console.error('Ошибка:', error);
  }
}
```

#### Б. Получение активных алертов для ленты уведомлений
```javascript
async function fetchActiveAlerts() {
  const response = await fetch('http://localhost:8080/api/alerts?resolved=false');
  const alerts = await response.json();
  // Отрисовать карточки алертов в боковой панели инспектора
  return alerts;
}

// Квитирование (закрытие) алерта по нажатию кнопки «Отработано»
async function resolveAlert(alertId) {
  const response = await fetch(`http://localhost:8080/api/alerts/${alertId}/resolve`, {
    method: 'PATCH'
  });
  return await response.json();
}
```

#### В. Получение истории телеметрии для графиков (Chart.js / Recharts)
```javascript
async function loadStationTelemetryChart(stationId) {
  const response = await fetch(`http://localhost:8080/api/telemetry/water/station/${stationId}?limit=24`);
  const history = await response.json();
  
  // Данные приходят от новых к старым, разворачиваем для оси времени
  const sorted = history.reverse();
  const labels = sorted.map(item => new Date(item.recordedAt).toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'}));
  const oxygenData = sorted.map(item => item.dissolvedOxygenMgL);
  const tempData = sorted.map(item => item.temperatureC);
  
  return { labels, oxygenData, tempData };
}
```

#### Г. Загрузка аудиозаписи с буя инспектором
```javascript
async function uploadAudioSample(file, stationId = 1) {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('stationId', stationId);

  const response = await fetch('http://localhost:8080/api/audio/upload', {
    method: 'POST',
    body: formData
  });

  const result = await response.json();
  if (result.alertGenerated) {
    alert(`ВНИМАНИЕ! Обнаружен: ${result.detectedClass} (вероятность ${Math.round(result.confidence * 100)}%)`);
  }
  return result;
}
```

#### Д. Запрос прогноза нереста для панели аналитики
```javascript
async function fetchSpawningForecast(species = 'Русский осётр', stationId = 1) {
  const url = `http://localhost:8080/api/forecast/spawning?species=${encodeURIComponent(species)}&stationId=${stationId}`;
  const response = await fetch(url);
  return await response.json();
}
```

---

## ЧАСТЬ 2. Интеграция с Python AI / Аналитикой (Участник 3)

### 2.1. Архитектура взаимодействия
Участник 3 разрабатывает легковесный HTTP-микросервис на **FastAPI** или **Flask**, слушающий порт `8000`.

```
                    ┌─────────────────────────┐
                    │  Java Backend (:8080)   │
                    └───────────┬─────────────┘
                                │
        1. POST /api/ai/audio/analyze (multipart audio)
        2. POST /api/ai/forecast/spawning (JSON history)
                                │
                                ▼
                    ┌─────────────────────────┐
                    │ Python FastAPI (:8000)  │
                    │   • Модель звука        │
                    │   • Модель прогноза     │
                    └─────────────────────────┘
```

---

### 2.2. Контракт эндпоинта 1: Анализ аудиозаписи

- **Метод:** `POST`
- **URL:** `http://localhost:8000/api/ai/audio/analyze`
- **Вход:** `multipart/form-data`
  - `file`: бинарный аудиофайл (`.wav`, `.mp3`)
  - `station_id`: идентификатор станции
- **Выходной JSON:**
```json
{
  "status": "SUCCESS",
  "detected_class": "boat_engine",
  "confidence": 0.92,
  "threat_level": "HIGH",
  "details": {
    "engine_type": "high_power_outboard",
    "rpm_estimate": 4200,
    "dominant_frequency_hz": 184.5
  }
}
```

Классы распознавания (`detected_class`):
- `boat_engine` — лодочный мотор (угроза `HIGH` $\rightarrow$ Java генерирует алерт `ACOUSTIC_BOAT_DETECTED`).
- `gunfire` — выстрел (угроза `CRITICAL` $\rightarrow$ Java генерирует алерт `ACOUSTIC_GUNFIRE`).
- `fish_splash` — всплеск рыбы / тюленя (угроза `LOW`).
- `ambient_noise` — шум ветра / волн / фон (угроза `NONE`).

---

### 2.3. Контракт эндпоинта 2: Прогноз нереста осетровых

- **Метод:** `POST`
- **URL:** `http://localhost:8000/api/ai/forecast/spawning`
- **Входной JSON:**
```json
{
  "species": "Russian_Sturgeon",
  "target_zone": "Volga_Delta_Damchik",
  "hydrology_history": [
    { "date": "2026-11-10", "temperature_c": 11.5, "salinity_ppt": 0.8, "water_level_cm": 250 },
    { "date": "2026-11-11", "temperature_c": 12.1, "salinity_ppt": 0.7, "water_level_cm": 258 },
    { "date": "2026-11-12", "temperature_c": 12.8, "salinity_ppt": 0.7, "water_level_cm": 265 }
  ]
}
```

- **Выходной JSON:**
```json
{
  "status": "SUCCESS",
  "suitability_index": 86.4,
  "spawn_probability": "HIGH",
  "optimal_window_start": "2026-11-15",
  "optimal_window_end": "2026-11-22",
  "limiting_factor": "Оптимальная температура (12.8°C) и достаточный уровень половодья",
  "patrol_recommendation": "Выставить ночные посты инспекции на входе в рукав Дамчикский для защиты производителей от браконьерского лова",
  "model_name": "CaspianSturgeonPredictor_v1"
}
```

---

### 2.4. Готовый шаблон Python-микросервиса для Участника 3

В репозиторий добавлен готовый рабочий скрипт:  
**[`scripts/ai_service.py`](file:///home/dagai/hahaton/scripts/ai_service.py)**

Участник 3 может запустить его за 1 минуту:
```bash
pip install fastapi uvicorn python-multipart
python scripts/ai_service.py
```
Скрипт сразу готов к приёму запросов от Java-бэкенда и содержит заготовленные заглушки и интеграцию реальных ML-моделей.

---

### 2.5. Механизм Fallback в Java-бэкенде

Если Участник 3 еще дообучает модель или Python-сервис временно остановлен:
- Java-бэкенд **не падает с ошибкой 500**.
- Включается встроенный анализатор (`mock-ai-fallback: true`), который анализирует спектральные признаки и имя сэмпла, возвращая корректные ответы и создавая алерты.
- Это гарантирует, что фронтендер и вся команда могут продолжать сборку и запись видеодемонстрации без блокировок.

