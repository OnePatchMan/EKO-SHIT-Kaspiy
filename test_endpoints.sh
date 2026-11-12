#!/bin/bash
# Скрипт автоматической проверки эндпоинтов «Эко-Щит Каспия»
set -e

BASE_URL="http://localhost:8080"
GREEN='\033[032m'
RED='\033[031m'
BLUE='\033[034m'
NC='\033[0m'

echo -e "${BLUE}=== Тестирование REST API 'Эко-Щит Каспия' ($BASE_URL) ===${NC}\n"

check_endpoint() {
    local method="$1"
    local path="$2"
    local name="$3"
    local data="$4"
    local content_type="$5"

    echo -n "Проверка: $name [$method $path]... "

    if [ "$method" == "GET" ]; then
        STATUS=$(curl -s -o /tmp/resp.json -w "%{http_code}" "$BASE_URL$path")
    elif [ "$method" == "POST" ]; then
        STATUS=$(curl -s -o /tmp/resp.json -w "%{http_code}" -X POST "$BASE_URL$path" \
            -H "Content-Type: ${content_type:-application/json}" -d "$data")
    elif [ "$method" == "PATCH" ]; then
        STATUS=$(curl -s -o /tmp/resp.json -w "%{http_code}" -X PATCH "$BASE_URL$path")
    fi

    if [[ "$STATUS" =~ ^2 ]]; then
        echo -e "${GREEN}УСПЕШНО (HTTP $STATUS)${NC}"
    else
        echo -e "${RED}ОШИБКА (HTTP $STATUS)${NC}"
        cat /tmp/resp.json
        echo ""
    fi
}

# 1. System Health
check_endpoint "GET" "/api/system/health" "Healthcheck сервиса"

# 2. System Status
check_endpoint "GET" "/api/system/status" "Сводка статуса системы"

# 3. Stations List
check_endpoint "GET" "/api/stations" "Список станций для карты"

# 4. Station 1 Details
check_endpoint "GET" "/api/stations/1" "Карточка станции 1"

# 5. Ingest Telemetry
TELEMETRY_PAYLOAD='{"stationId":1,"temperatureC":12.5,"salinityPpt":0.9,"dissolvedOxygenMgL":3.4,"ph":7.4,"turbidityNtu":19.0,"waterLevelCm":255.0}'
check_endpoint "POST" "/api/telemetry/water" "Отправка телеметрии датчиков" "$TELEMETRY_PAYLOAD"

# 6. Water Telemetry History
check_endpoint "GET" "/api/telemetry/water/station/1?limit=10" "История телеметрии станции 1"

# 7. Active Alerts
check_endpoint "GET" "/api/alerts?resolved=false" "Список активных алертов"

# 8. Resolve Alert 1
check_endpoint "PATCH" "/api/alerts/1/resolve" "Квитирование алерта 1"

# 9. Spawning Forecast
check_endpoint "GET" "/api/forecast/spawning" "Прогноз нереста осетровых"

# 10. Audio Upload
echo "test sound bytes" > /tmp/test_boat.wav
echo -n "Проверка: Загрузка аудио с буя [POST /api/audio/upload]... "
STATUS=$(curl -s -o /tmp/resp.json -w "%{http_code}" -X POST "$BASE_URL/api/audio/upload" \
    -F "file=@/tmp/test_boat.wav" -F "stationId=3")
if [[ "$STATUS" =~ ^2 ]]; then
    echo -e "${GREEN}УСПЕШНО (HTTP $STATUS)${NC}"
else
    echo -e "${RED}ОШИБКА (HTTP $STATUS)${NC}"
    cat /tmp/resp.json
    echo ""
fi

echo -e "\n${GREEN}Все базовые проверки завершены успешно!${NC}"

