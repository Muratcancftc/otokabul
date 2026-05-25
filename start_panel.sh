#!/bin/bash
# OtoKabul panel — yerel sunucu başlat
cd "$(dirname "$0")"
PORT=8765

if lsof -i ":$PORT" -sTCP:LISTEN -t >/dev/null 2>&1; then
  echo "Port $PORT zaten kullanımda — mevcut sunucu kullanılıyor."
else
  echo "Sunucu başlatılıyor (port $PORT)…"
  python3 -m http.server "$PORT" &
  sleep 1
fi

URL="http://localhost:$PORT/panel.html"
echo ""
echo "Panel adresi: $URL"
echo ""

if command -v open >/dev/null 2>&1; then
  open "$URL"
elif command -v xdg-open >/dev/null 2>&1; then
  xdg-open "$URL"
fi

echo "Durdurmak için: kill \$(lsof -t -i:$PORT)"
