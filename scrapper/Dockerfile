# fdiet scrapper: FastAPI on Python 3.12.
# Build:  docker build -t fdiet-scrapper scrapper
FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 PIP_NO_CACHE_DIR=1 PIP_DISABLE_PIP_VERSION_CHECK=1
WORKDIR /app
# uid 1000 so a bind-mounted data/ on a Linux host stays writable by its usual owner.
RUN useradd --uid 1000 --create-home scrapper
COPY pyproject.toml ./
COPY app app
RUN pip install .
RUN mkdir -p /app/data && chown scrapper:scrapper /app/data
USER scrapper

ENV SCRAPER_DATA_DIR=/app/data SCRAPER_LOG_FORMAT=json
VOLUME /app/data
EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/health', timeout=4)"
# --no-access-log: the access log would interleave one line per status poll with the sync's own lines.
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--no-access-log"]
