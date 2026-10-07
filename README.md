# Bank Transactions API

Месячный лимит по умолчанию — 1000 USD отдельно для каждой категории счёта;
бизнес-календарь использует московское время (UTC+3).
Клиентский API установки лимита, регистрация счетов и блокировки описаны в
[LIMIT_CREATION.md](LIMIT_CREATION.md).

Сервис приёма банковских транзакций. Проверяет входные данные, присваивает UUID и время
получения в UTC, сохраняет транзакцию в PostgreSQL и возвращает HTTP `201`.
Клиентский API устанавливает лимиты, сохраняя историю изменений. API доступны без авторизации.

## Адреса для тестирования

| Метод | URL | Назначение |
| --- | --- | --- |
| POST | `http://localhost:8080/api/v1/bank/transactions` | Приём транзакции |
| POST | `http://localhost:8080/api/v1/client/limits` | Установка нового лимита |
| GET | `http://localhost:8080/swagger-ui/index.html` | Swagger UI: Try it out → Execute |
| GET | `http://localhost:8080/v3/api-docs` | OpenAPI JSON |

Для POST используйте `Content-Type: application/json`. Получение всех лимитов,
списка превышений и расчёт `limit_exceeded` пока не реализованы.

## Установка лимита

`POST http://localhost:8080/api/v1/client/limits`:

```json
{
  "account": "0000000123",
  "expense_category": "product",
  "amount": 1500.00
}
```

Ответ `201` содержит `id`, `account`, `expense_category`, `amount`, `currency: USD`
и `established_at` с часовым смещением `+03:00`. Дату и валюту назначает сервер;
передача этих или неизвестных полей возвращает `400`.

Новый счёт регистрируется автоматически; пока лимит не установлен, действует 1000 USD
отдельно для `product` и `service`. Лимит может быть нулевым. Каждая установка создаёт
новую запись, существующие записи обновлять нельзя.

Повтор того же запроса вернёт `409` с `code: limit_amount_unchanged`, включая попытку
установить дефолтные 1000 USD. Если другой запрос для того же счёта и категории ещё
выполняется, ответ — `409` с `code: limit_request_in_progress`. Другие категории и счета
обрабатываются параллельно. Ошибки используют ProblemDetail (RFC 9457).

Для проверки в Swagger сначала установите 1500, затем повторите запрос (ожидается 409),
затем установите 0 (ожидается 201). В категории `service` останется дефолтный лимит 1000 USD.

## Стек

Java 21, Spring Boot 4.1.1, Spring MVC, Bean Validation, Spring JDBC, PostgreSQL 17,
Flyway, MapStruct, springdoc-openapi / Swagger UI.
Тесты: JUnit 5, Mockito, AssertJ, Testcontainers, ArchUnit.

## Локальный запуск

Нужны JDK 21 и Docker с Docker Compose. Maven устанавливать отдельно не нужно — есть Maven Wrapper.
Из корня проекта запустите БД, затем приложение:

```powershell
docker compose up -d --wait
./mvnw.cmd spring-boot:run
```

В Linux/macOS используйте `./mvnw` вместо `./mvnw.cmd`.
Flyway автоматически создаёт таблицу и применяет миграции при старте приложения.
Приложение слушает порт `8080`; Compose публикует PostgreSQL на `127.0.0.1:5432`.
Если `5432` занят локальным PostgreSQL, используйте другой порт, например `15432`:

```powershell
$env:DB_PORT = '15432'
$env:DB_URL = 'jdbc:postgresql://localhost:15432/test_task_idf'
docker compose up -d --wait
./mvnw.cmd spring-boot:run
```

В IDE для этого варианта: host `localhost`, port `15432`, database `test_task_idf`,
user и password `test_task_idf`. При смене порта Docker volume с данными сохраняется.
Остановить БД можно командой `docker compose stop`; данные сохраняются в Docker volume.

Для запуска собранного приложения:

```powershell
./mvnw.cmd package -DskipTests
java -jar target/test-task-idf-0.0.1-SNAPSHOT.jar
```

### Настройки подключения

| Переменная | Значение по умолчанию |
| --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/test_task_idf` |
| `DB_PORT` | `5432` (порт PostgreSQL в Docker Compose) |
| `DB_USERNAME` | `test_task_idf` |
| `DB_PASSWORD` | `test_task_idf` |
| `SERVER_PORT` | `8080` |

Значения учётных данных по умолчанию предназначены для локального запуска.
Чтобы подключить другую БД, задайте переменные окружения перед запуском приложения.

## Swagger / OpenAPI

После запуска приложения:

- [Swagger UI](http://localhost:8080/swagger-ui/index.html) — описание и выполнение запросов через Try it out.
- [OpenAPI JSON](http://localhost:8080/v3/api-docs).
- [OpenAPI YAML](http://localhost:8080/v3/api-docs.yaml).

Схема генерируется из контроллера, DTO, Swagger-аннотаций и ограничений Bean Validation.
Для Spring Boot 4 используется springdoc 3.x согласно [документации springdoc](https://springdoc.org/).

## Приём транзакции

`POST /api/v1/bank/transactions`, `Content-Type: application/json`.
Все шесть полей обязательны:

| Поле | Тип | Ограничения |
| --- | --- | --- |
| `account_from` | string | Ровно 10 цифр; ведущие нули сохраняются |
| `account_to` | string | Ровно 10 цифр |
| `currency_shortname` | string | ISO 4217, поддерживаемый Java; регистр важен, например `KZT` |
| `sum` | number | Больше нуля; до 17 цифр целой части и до 2 знаков дробной части |
| `expense_category` | string | `product` или `service` |
| `datetime` | string | ISO 8601 с часовым смещением, например `+06:00` или `Z` |

Пример тела запроса:

```json
{
  "account_from": "0000000321",
  "account_to": "9999999999",
  "currency_shortname": "KZT",
  "sum": 10000.45,
  "expense_category": "product",
  "datetime": "2022-01-30T00:00:00+06:00"
}
```

Пример вызова в PowerShell:

```powershell
$body = @{
    account_from = '0000000321'
    account_to = '9999999999'
    currency_shortname = 'KZT'
    sum = 10000.45
    expense_category = 'product'
    datetime = '2022-01-30T00:00:00+06:00'
} | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/v1/bank/transactions' `
    -ContentType 'application/json' -Body $body
```

Успешный ответ: HTTP `201`, `application/json`. UUID и время получения ниже приведены для примера:

```json
{
  "id": "a0187ed2-b40a-4cde-86e2-68972c69dd10",
  "account_from": "0000000321",
  "account_to": "9999999999",
  "currency_shortname": "KZT",
  "sum": 10000.45,
  "expense_category": "product",
  "datetime": "2022-01-30T00:00:00+06:00",
  "received_at": "2026-10-07T12:00:00Z"
}
```

Каждый запрос создаёт новую запись: API не поддерживает идемпотентность и дедупликацию.
`datetime` обозначает время операции, `received_at` — время получения сервером.
PostgreSQL хранит временные поля как `TIMESTAMP WITH TIME ZONE`: сохраняется момент времени,
но исходное смещение не хранится; точность БД — микросекунды.

### Ошибки

Ошибки возвращаются в формате `application/problem+json`.

| Статус | Причина |
| --- | --- |
| `400` | Некорректный JSON, формат даты, значения полей или нарушение ограничений |
| `405` | Неподдерживаемый HTTP-метод |
| `415` | Неподдерживаемый тип содержимого запроса |
| `503` | Ошибка сохранения транзакции |
| `500` | Непредвиденная серверная ошибка |

Пример ошибки валидации (текст сообщения зависит от валидатора):

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Request validation failed",
  "instance": "/api/v1/bank/transactions",
  "errors": {
    "account_from": "must match \"[0-9]{10}\""
  }
}
```

Объект `errors` присутствует при Bean Validation и использует входные имена полей в snake_case.
При ошибке разбора JSON, ошибке БД или непредвиденном исключении этого объекта нет.

## Устройство проекта

- `controller` — HTTP API и обработка ошибок в формате ProblemDetail.
- `dto` — контракт запроса и ответа, валидация и Swagger-схемы.
- `service` — генерация UUID, время из Clock, транзакционная граница и MapStruct-маппер.
- `repository` — вставка через JdbcTemplate с параметрами.
- `accounts` в БД — реестр счетов; `transactions.account_from` и `expense_limits.account`
  ссылаются на него. `account_to` хранится как счёт контрагента без обязательной регистрации.
- `model` — неизменяемая модель с доменными ограничениями.
- `config` — Clock, валидатор валюты и метаданные OpenAPI.
- `util` — преобразование категорий и имён полей.
- `src/main/resources/db/migration` — версионированные SQL-миграции Flyway.

Сохранение и построение ответа выполняются в одной транзакции. Runtime-исключение,
включая ошибку маппинга ответа после INSERT, приводит к rollback.
Регистрация счёта выполняется отдельной транзакцией и сохраняется при отклонении установки лимита.

## Тестирование и проверки

Полный прогон требует запущенного Docker:

```powershell
./mvnw.cmd verify
```

Testcontainers поднимает отдельную PostgreSQL 17. Spring Boot подключает её через
`@ServiceConnection` и управляет жизненным циклом контейнера. Локальная БД для тестов не нужна.
API-тесты проверяют запросы, валидацию, сохранение, OpenAPI и доступность Swagger UI.
Интеграционные тесты сервиса проверяют commit и rollback, тесты миграций — ограничения БД.

Unit-тесты можно выполнить без Docker:

```powershell
./mvnw.cmd test
```

`verify` также выполняет архитектурные проверки ArchUnit, Checkstyle и SpotBugs.
Отчёт покрытия JaCoCo: `target/site/jacoco/index.html`.
Maven автоматически активирует профиль `test` из `src/test/resources/application-test.properties`.
Подключение к БД задаёт `@ServiceConnection`; резервный URL намеренно недоступен, чтобы при
отсутствии контейнерной конфигурации тест не подключился к локальной рабочей БД.
Параллельное выполнение JUnit отключено, поскольку интеграционные тесты используют общий Spring-контекст.

Surefire выполняет unit-тесты и ArchUnit в фазе `test`. Failsafe выполняет классы
`*IntegrationTests`, `*ApiTests`, `*MigrationTests` и `TestTaskIdfApplicationTests` в фазе
`integration-test` и проверяет результаты в `verify`. Для новых интеграционных тестов используйте
суффикс `IntegrationTests` и импорт `PostgresTestConfiguration`.
Результаты: `target/surefire-reports` и `target/failsafe-reports`.

### Test-stage в CI

GitHub Actions (`.github/workflows/ci.yml`) запускается на push, pull request и вручную:

1. `build` собирает JAR на JDK 21 и сохраняет артефакт `application-jar`.
2. `test` (Test stage) после успешной сборки проверяет доступность Docker и запускает
   `./mvnw --batch-mode --no-transfer-progress clean verify`.
3. Testcontainers самостоятельно создаёт PostgreSQL на динамическом порту; отдельный
   PostgreSQL service и секреты подключения к БД в CI не нужны.
4. Ошибка тестов, ArchUnit, Checkstyle или SpotBugs завершает test-stage с ошибкой.
   Отчёты Surefire, Failsafe, Checkstyle, SpotBugs и JaCoCo сохраняются в артефакт
   `test-and-quality-reports` на 7 дней даже при неуспешном прогоне.

Анализ SonarQube включается при настройке `SONAR_HOST_URL`, `SONAR_PROJECT_KEY` и секрета
`SONAR_TOKEN`; для SonarCloud также нужен `SONAR_ORGANIZATION`.

## JavaDoc

Основные классы и публичные операции документированы в исходниках.
Чтобы собрать HTML-документацию:

```powershell
./mvnw.cmd compile javadoc:javadoc
```

Результат: `target/reports/apidocs/index.html`. Сгенерированные MapStruct-реализации исключены из JavaDoc.
