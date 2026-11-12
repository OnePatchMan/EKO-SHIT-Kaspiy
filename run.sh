#!/bin/bash
# Скрипт запуска бэкенда «Эко-Щит Каспия»
set -e

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$DIR/demo"

echo "====================================================="
echo "   Эко-Щит Каспия: Запуск Java Backend сервера"
echo "====================================================="

if [ -f "target/demo-0.0.1-SNAPSHOT.jar" ]; then
    echo "Запуск готового артефакта target/demo-0.0.1-SNAPSHOT.jar..."
    java -jar target/demo-0.0.1-SNAPSHOT.jar
else
    echo "Сборка и запуск через Maven..."
    mvn clean spring-boot:run -o
fi

