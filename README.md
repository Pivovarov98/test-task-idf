# Bank Transactions API

Сервис приёма банковских транзакций. Проверяет входные данные, присваивает UUID и время
получения в UTC, сохраняет транзакцию в PostgreSQL и возвращает HTTP `201`.

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
- `model` — неизменяемая модель с доменными ограничениями.
- `config` — Clock, валидатор валюты и метаданные OpenAPI.
- `util` — преобразование категорий и имён полей.
- `src/main/resources/db/migration` — версионированные SQL-миграции Flyway.

Сохранение и построение ответа выполняются в одной транзакции. Runtime-исключение,
включая ошибку маппинга ответа после INSERT, приводит к rollback.

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
./mvnw.cmd "-Dtest=TransactionServiceTests,TransactionMapperTests,JdbcTransactionRepositoryTests,CurrencyCodeValidatorTests,ExpenseCategoryUtilsTests,FieldNameUtilsTests" test
```

`verify` также выполняет архитектурные проверки ArchUnit, Checkstyle и SpotBugs.
Отчёт покрытия JaCoCo: `target/site/jacoco/index.html`.
CI в `.github/workflows` выполняет сборку и проверки; анализ SonarQube включается при настройке
`SONAR_HOST_URL`, `SONAR_PROJECT_KEY` и секрета `SONAR_TOKEN`.

## JavaDoc

Основные классы и публичные операции документированы в исходниках.
Чтобы собрать HTML-документацию:

```powershell
./mvnw.cmd compile javadoc:javadoc
```

Результат: `target/reports/apidocs/index.html`. Сгенерированные MapStruct-реализации исключены из JavaDoc.
