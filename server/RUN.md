# Сервер электронный очереди — запуск и API

## Запуск

```bash
python3 server.py
```

Сервер слушает `0.0.0.0:8080`.

## Эндпоинты

### GET /health
Проверка, что сервер жив (возвращает JSON).

Пример:
```bash
curl http://localhost:8080/health
# -> {"status":"ok"}
```

### POST /queue/join
Поставить пользователя в очередь и получить «талон».
Ответ содержит `ticket`, `position` и `current_ticket`.
Поставить пользователя в очередь и получить «талон».

Body (JSON):
```json
{"name":"Иван"}
```

Пример:
```bash
curl -X POST http://localhost:8080/queue/join   -H "Content-Type: application/json"   -d '{"name":"Иван"}'
```

### GET /queue/status?ticket=...
Ответ содержит `status` (WAITING/CALLED/DONE), `position`, `current_ticket`.
Статус по талону.

Пример:
```bash
curl "http://localhost:8080/queue/status?ticket=1"
```

### POST /queue/next
Сдвинуть текущий обслуживаемый номер на +1.

Пример:
```bash
curl -X POST http://localhost:8080/queue/next
```

### POST /queue/reset
Очистить очередь (для демонстрации).

Пример:
```bash
curl -X POST http://localhost:8080/queue/reset
```
