FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

# discovery.db / line.db は Fly Volume (/data) に置いて永続化する。
# アプリコードは /app に置いたまま、カレントディレクトリだけ /data にして
# sqlite:///discovery.db のような相対パスが Volume 側に書かれるようにする。
RUN mkdir -p /data
WORKDIR /data

EXPOSE 8080

CMD ["uvicorn", "--app-dir", "/app", "main:app", "--host", "0.0.0.0", "--port", "8080"]
