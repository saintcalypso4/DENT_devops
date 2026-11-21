# DENT_devops — Стоматологическая клиника

REST API сервиса стоматологической клиники.
Java 17 + PostgreSQL 16 + Docker Compose. CRUD-запросы хранятся в базе данных.

## Технологии
- Java 17 (встроенный `com.sun.net.httpserver`, JDBC)
- PostgreSQL 16 (`postgres:16`)
- Docker, Docker Compose

## Структура
```
DENT_devops/
├── src/App.java          # исходный код API
├── Dockerfile            # сборка образа API (2 стадии: builder → runtime)
├── docker-compose.yml    # сервисы api и db, том db_data
├── .dockerignore
└── README.md
```

## Сервисы
| Сервис | Образ | Порт | Описание |
|---|---|---|---|
| api | сборка из Dockerfile | 8080 | REST API + JDBC |
| db | postgres:16 | 5432 | база `app`, том `db_data` |

## Запуск
```bash
docker compose up --build -d
docker compose ps
```

## Эндпоинты
| Метод | Путь | Описание |
|---|---|---|
| GET | /health | проверка живости |
| GET | /tasks | список задач |
| GET | /tasks/{id} | одна задача |
| POST | /tasks | создать |
| PUT | /tasks/{id} | изменить |
| DELETE | /tasks/{id} | удалить |

## Проверка CRUD
```bash
curl http://localhost:8080/health
curl -X POST http://localhost:8080/tasks -H "Content-Type: application/json" -d '{"title":"Cleaning","done":false}'
curl http://localhost:8080/tasks
curl -X PUT http://localhost:8080/tasks/1 -H "Content-Type: application/json" -d '{"title":"Cleaning","done":true}'
curl -X DELETE http://localhost:8080/tasks/2
```

## Проверка сохранения данных после перезапуска
```bash
docker compose down  
docker compose up -d
curl http://localhost:8080/tasks 