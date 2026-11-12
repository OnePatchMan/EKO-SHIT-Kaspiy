#!/usr/bin/env python3
"""
Микросервис ИИ-аналитики и прогноза «Эко-Щит Каспия» (Участник 3).
Реализует API для вызова аудиомодели классификации звуков и гидробиологического прогноза.
Запуск:
    pip install fastapi uvicorn python-multipart
    python scripts/ai_service.py
"""

import uvicorn
from fastapi import FastAPI, UploadFile, File, Form
from pydantic import BaseModel
from typing import List, Optional
import datetime

app = FastAPI(title="EcoShield Caspian AI Service", version="1.0.0")

class HydrologyPoint(BaseModel):
    date: Optional[str] = None
    temperature_c: float
    salinity_ppt: float
    water_level_cm: float

class ForecastRequest(BaseModel):
    species: str = "Russian_Sturgeon"
    target_zone: str = "Volga_Delta_Damchik"
    hydrology_history: List[HydrologyPoint] = []

@app.get("/health")
def health():
    return {"status": "UP", "service": "EcoShield AI Engine", "port": 8000}

@app.post("/api/ai/audio/analyze")
async def analyze_audio(file: UploadFile = File(...), station_id: int = Form(1)):
    """
    Классификация аудиозаписи с буя:
    Распознает лодочные подвесные моторы (boat_engine), выстрелы (gunfire), всплески и фон.
    """
    filename = (file.filename or "").lower()
    
    # Здесь Участник 3 подключает инференс обученной нейросети (напр. PyTorch/YAMNet/librosa)
    # Пример базовой логики классификатора:
    if any(k in filename for k in ["boat", "motor", "лодка", "двигател"]):
        detected_class = "boat_engine"
        confidence = 0.93
        threat_level = "HIGH"
    elif any(k in filename for k in ["shot", "gun", "выстрел"]):
        detected_class = "gunfire"
        confidence = 0.89
        threat_level = "CRITICAL"
    elif any(k in filename for k in ["splash", "рыба", "плеск"]):
        detected_class = "fish_splash"
        confidence = 0.91
        threat_level = "LOW"
    else:
        # По умолчанию при тесте браконьерского сценария
        detected_class = "boat_engine"
        confidence = 0.88
        threat_level = "HIGH"

    return {
        "status": "SUCCESS",
        "station_id": station_id,
        "filename": file.filename,
        "detected_class": detected_class,
        "confidence": confidence,
        "threat_level": threat_level,
        "details": {
            "engine_type": "high_power_outboard" if detected_class == "boat_engine" else None,
            "rpm_estimate": 4350 if detected_class == "boat_engine" else None,
            "dominant_frequency_hz": 182.4
        }
    }

@app.post("/api/ai/forecast/spawning")
def forecast_spawning(req: ForecastRequest):
    """
    Предиктивный гидробиологический расчет нерестового хода для осетровых рыб
    на основе параметров воды (температура, уровень половодья, солёность).
    """
    temps = [p.temperature_c for p in req.hydrology_history] if req.hydrology_history else [12.5]
    avg_temp = sum(temps) / len(temps)

    # Оптимум для русского осетра: 11.5 - 15.0 °C
    if 11.5 <= avg_temp <= 15.5:
        suitability = 87.5
        prob = "HIGH"
        rec = "Высокая вероятность нерестового хода. Рекомендовано перекрытие браконьерских проток в ночное время."
    elif 9.0 <= avg_temp < 11.5:
        suitability = 62.0
        prob = "MODERATE"
        rec = "Вода прогревается, начало миграции ожидается в течение 3-5 дней."
    else:
        suitability = 35.0
        prob = "LOW"
        rec = "Температурный режим неблагоприятен для захода на нерестилища."

    today = datetime.date.today()
    return {
        "status": "SUCCESS",
        "species": req.species,
        "target_zone": req.target_zone,
        "suitability_index": suitability,
        "spawn_probability": prob,
        "optimal_window_start": str(today + datetime.timedelta(days=2)),
        "optimal_window_end": str(today + datetime.timedelta(days=9)),
        "limiting_factor": f"Средняя температура воды: {avg_temp:.1f}°C (оптимум 12-15°C)",
        "patrol_recommendation": rec,
        "model_name": "CaspianSturgeonPredictor_v1"
    }

if __name__ == "__main__":
    uvicorn.run("ai_service:app", host="0.0.0.0", port=8000, reload=True)

