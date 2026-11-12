# Подключение EcoShield AI к Java

Java хранит станции, файлы, измерения, результаты и предупреждения. Этот сервис выполняет вычисления и возвращает JSON. Интерфейс обращается к Java API. Адрес локального экземпляра — `http://127.0.0.1:8001`.

## Аудио

`POST /v1/audio/analyze`, Content-Type: multipart/form-data.

Поля: `file` — WAV от 1 до 60 секунд, не больше 10 MiB; `station_id` — идентификатор станции; `recorded_at` — ISO 8601 с часовым поясом. Модель сама выполняет mono и пересчёт частоты.

```powershell
curl.exe -s http://127.0.0.1:8001/v1/audio/analyze -F "file=@data/audio/boat_river_germany.wav;type=audio/wav" -F "station_id=delta-demo-01" -F "recorded_at=2026-05-10T09:00:00Z"
```

Ответ содержит `analysis_id`, `station_id`, `recorded_at`, `status`, `events[]`, `target_scores`, `top_classes`, `model_version`, `policy_version`, `latency_ms`, `warnings`. В событии: `type`, `start_seconds`, `end_seconds`, `score`, `threshold`, `review_recommended`.

Возможные события: `boat_suspected`, `gunshot_suspected`, `engine_detected`. Несколько типов могут пересекаться во времени. Отсутствие событий даёт `no_target_event` или `uncertain`. Общий двигатель не равен лодке. `score_is_calibrated_probability=false` и `thresholds_calibrated=false` обязательны для корректного отображения. Показывать «оценка модели 0,72», а не «72% вероятность браконьерства».

Java создаёт предупреждение по своим правилам: событие, зона, время, разрешённые суда, контекст. Сервис не устанавливает незаконность. Координаты на карте берутся из станции, а не из модели звука.

## Условия нереста

`POST /v1/spawning/forecast`, Content-Type: application/json. Готовый запрос — `examples/forecast_request.json`.

```powershell
curl.exe -s http://127.0.0.1:8001/v1/spawning/forecast -H "Content-Type: application/json" --data-binary "@examples/forecast_request.json"
```

Поля запроса: station_id, species=`cyprinus_carpio`, issued_at, horizon_hours=24/48/72, data_source, level_datum при передаче уровней, history с минимум тремя наблюдениями за минимум 48 часов. Последняя запись не старше 24 часов относительно issued_at. Даты истории уникальны и не позже issued_at. Обязательна температура воды. `inundated` — true/false/null; её отсутствие не трактуется как затопленное нерестилище.

Опциональное `scenario` задаёт гидрологию на issued_at + 24/48/72 часа, ровно по одной записи на каждый день. Без него выполняется линейная демонстрационная проекция по последней неделе, с ограничением наклона 2 °C/сутки и 0,5 м/сутки и сохранением последнего признака обводнения. Эти ограничения — технические допущения, не физическая модель водоёма. В `trajectory` также передаётся температура при сохранении последнего значения для сравнения.

Ответ: mode=`scenario_conditions_forecast`, is_demo=true, validation_status=`not_validated_on_spawning_observations`, probability=null, issued_at, valid_from, valid_to, trajectory, factors, missing_data, warnings. В каждой будущей строке есть условия и признак достижения демонстрационного температурного ориентира 17 °C. Достижение ориентира не утверждает факт или вероятность нереста.

## Computer Vision

`POST /v1/images/compare`, multipart: before, after, station_id, before_at, after_at, aligned, is_demo. PNG/JPEG до 10 MiB и 4 мегапикселей на изображение. Даты с часовым поясом; after_at позже before_at. Размеры совпадают. `aligned=true` — утверждение вызывающей стороны о выполненном пространственном совмещении, а не действие сервиса.

Ответ: changed_fraction, candidate_regions (x/y/width/height в пикселях), region_count, method, requires_inspector_review, trained_model=false, detects_illegal_nets_or_spills=false, warnings. Единый сдвиг яркости компенсируется; облака, тени, сезонность и движение воды могут давать ложные участки. Это не карта загрязнения. Для реальных спутниковых сцен нужны подготовка и совмещение в GIS; тестовые PNG синтетические.

## IoT

`POST /v1/iot/validate`, JSON из `examples/iot_batch.json`. Метод проверяет и нормализует пакет; `stored=false`. В продукте Java сам принимает и сохраняет поток датчиков; этот метод помогает согласовать формат и проверить примеры.

Единицы: temperature_c — °C; water_level_m — метры относительно level_datum; dissolved_oxygen_mg_l — мг/л; salinity_psu — практическая солёность; discharge_m3_s — м³/с. Пропуски — null. Время нормализуется в UTC. `data_source` строго: demo_synthetic, open_observations или organizer_observations. Настоящее оборудование не подключено.

## Ошибки и соединение

200 — вычисление завершено, но смысловые ограничения читаются из полей ответа. 413 — превышен лимит размера. 422 — некорректные значения, файл, время или история. 503 — аудиомодель не готова. 500 — внутренняя ошибка, которую нельзя показывать как пустое успешное событие.

Для первого локального подключения начать с connect timeout 5 секунд и request timeout 60 секунд, затем настроить по фактической задержке и нагрузке. Эти значения — настройки демонстрации. Файлы ограничены, inference сериализован; очереди заданий и промышленное масштабирование не реализованы.

Порядок проверки команды: health.audio_model_ready=true → аудиозапись через Java → сохранение JSON → событие на карте у станции → сценарный прогноз → отображение будущего периода и маркировки демо. Идентификаторы analysis_id сохраняются для сопоставления. Подключение к конкретному Java приложению не выполнено, поскольку его код и адрес не предоставлены.
