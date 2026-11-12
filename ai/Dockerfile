FROM python:3.12-slim
WORKDIR /app
COPY requirements-lock.txt .
RUN pip install --no-cache-dir -r requirements-lock.txt
COPY . .
EXPOSE 8001
CMD ["sh", "-c", "python scripts/setup_assets.py --model-only && uvicorn ecoshield.api:app --host 0.0.0.0 --port 8001"]
