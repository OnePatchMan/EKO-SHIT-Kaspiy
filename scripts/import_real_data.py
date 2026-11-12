#!/usr/bin/env python3
"""
Скрипт загрузки РЕАЛЬНЫХ данных в систему «Эко-Щит Каспия».
Позволяет:
1. Загрузить реальный CSV-файл с измерениями датчиков воды в базу.
2. Загрузить реальные аудиозаписи (.wav/.mp3) с буев/гидрофонов.

Примеры использования:
    # Загрузить замеры воды из CSV
    python scripts/import_real_data.py --csv data/sample_real_telemetry.csv

    # Загрузить аудиофайл на конкретный буй
    python scripts/import_real_data.py --audio my_real_boat_sound.wav --station-id 3

    # Загрузить всю папку с аудиозаписями
    python scripts/import_real_data.py --audio-dir data/audio/ --station-id 1
"""

import argparse
import csv
import json
import os
import sys
import urllib.request
import urllib.error

BACKEND_URL = os.environ.get("BACKEND_URL", "http://localhost:8080")

def import_csv_telemetry(csv_path):
    if not os.path.exists(csv_path):
        print(f"❌ Ошибка: Файл {csv_path} не найден!")
        return

    records = []
    print(f" Чтение CSV-файла: {csv_path}")

    with open(csv_path, mode="r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            try:
                record = {
                    "stationId": int(row.get("station_id", 1)),
                    "temperatureC": float(row["temperature_c"]) if row.get("temperature_c") else None,
                    "salinityPpt": float(row["salinity_ppt"]) if row.get("salinity_ppt") else None,
                    "dissolvedOxygenMgL": float(row["dissolved_oxygen_mg_l"]) if row.get("dissolved_oxygen_mg_l") else None,
                    "ph": float(row["ph"]) if row.get("ph") else None,
                    "turbidityNtu": float(row["turbidity_ntu"]) if row.get("turbidity_ntu") else None,
                    "waterLevelCm": float(row["water_level_cm"]) if row.get("water_level_cm") else None,
                    "recordedAt": row.get("recorded_at")
                }
                records.append(record)
            except Exception as e:
                print(f"⚠️ Пропуск некорректной строки {row}: {e}")

    if not records:
        print("❌ Нет корректных записей для импорта.")
        return

    print(f" Найдено {len(records)} записей. Отправка пакета на {BACKEND_URL}/api/telemetry/water/batch ...")
    
    url = f"{BACKEND_URL}/api/telemetry/water/batch"
    data = json.dumps(records).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"}, method="POST")

    try:
        with urllib.request.urlopen(req) as resp:
            if resp.status in (200, 201):
                result = json.loads(resp.read().decode("utf-8"))
                alerts = sum(r.get("generatedAlertsCount", 0) for r in result)
                print(f"✅ УСПЕШНО! Загружено {len(result)} измерений в базу.")
                if alerts > 0:
                    print(f"🚨 Внимание: В процессе импорта обнаружено аномалий и создано тревог: {alerts}")
            else:
                print(f"❌ Ошибка сервера: HTTP {resp.status}")
    except urllib.error.URLError as e:
        print(f"❌ Ошибка соединения с бэкендом ({url}): {e}")
        print("Убедитесь, что сервер запущен: ./run.sh")

def upload_audio_file(file_path, station_id=1):
    if not os.path.exists(file_path):
        print(f"❌ Файл не найден: {file_path}")
        return

    import mimetypes
    boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW"
    filename = os.path.basename(file_path)
    mime_type = mimetypes.guess_type(file_path)[0] or "audio/wav"

    with open(file_path, "rb") as f:
        file_bytes = f.read()

    body = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="stationId"\r\n\r\n'
        f"{station_id}\r\n"
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="file"; filename="{filename}"\r\n'
        f"Content-Type: {mime_type}\r\n\r\n"
    ).encode("utf-8") + file_bytes + f"\r\n--{boundary}--\r\n".encode("utf-8")

    url = f"{BACKEND_URL}/api/audio/upload"
    req = urllib.request.Request(
        url,
        data=body,
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}"},
        method="POST"
    )

    print(f" Отправка аудио {filename} (размер: {len(file_bytes)} байт) на станцию #{station_id}...")
    try:
        with urllib.request.urlopen(req) as resp:
            result = json.loads(resp.read().decode("utf-8"))
            print(f"✅ Результат ИИ-анализа: Класс='{result.get('detectedClass')}', Уверенность={result.get('confidence')}, Угроза={result.get('threatLevel')}")
            if result.get("alertGenerated"):
                print(f"🚨 Сформирован оперативный алерт #{result.get('alertId')} на карте инспектора!")
    except urllib.error.URLError as e:
        print(f"❌ Ошибка отправки аудио: {e}")

def main():
    parser = argparse.ArgumentParser(description="Импорт реальных данных в Эко-Щит Каспия")
    parser.add_argument("--csv", help="Путь к CSV файлу с показаниями датчиков")
    parser.add_argument("--audio", help="Путь к аудиофайлу (.wav/.mp3)")
    parser.add_argument("--audio-dir", help="Путь к папке с аудиофайлами")
    parser.add_argument("--station-id", type=int, default=1, help="ID станции (по умолчанию 1)")

    args = parser.parse_args()

    if args.csv:
        import_csv_telemetry(args.csv)
    elif args.audio:
        upload_audio_file(args.audio, args.station_id)
    elif args.audio_dir:
        if os.path.exists(args.audio_dir):
            files = [f for f in os.listdir(args.audio_dir) if f.lower().endswith((".wav", ".mp3", ".ogg"))]
            print(f"Найдено {len(files)} аудиофайлов в {args.audio_dir}")
            for f in files:
                upload_audio_file(os.path.join(args.audio_dir, f), args.station_id)
        else:
            print(f"❌ Папка {args.audio_dir} не найдена")
    else:
        parser.print_help()

if __name__ == "__main__":
    main()

