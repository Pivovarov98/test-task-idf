---
name: idf-limit-tests
description: >-
  Добавляет, исправляет и проверяет тесты месячных лимитов Bank Transactions API
  в test-task-idf: исторические лимиты, резервирование расходов, граничные суммы
  и HTTP-выдача превышенных транзакций. Применяй при запросах на тестирование
  лимитов или воспроизведение январского/февральского сценария задания.
  Не предназначен для добавления провайдеров курсов и миграций БД.
---

# Тестирование лимитов test-task-idf

Все команды выполняй из корня репозитория. Ссылки ниже относительны этому файлу.

## Выбери существующий образец

- Сквозной HTTP-сценарий: [JanuaryScenarioApiTests](../../../src/test/java/org/example/testtaskidf/controller/JanuaryScenarioApiTests.java).
  Используй `@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@LocalServerPort` и `RestClient`.
  Подготовка лимитов, приём транзакций, подтверждение банка и чтение результата идут через HTTP,
  без прямых вызовов сервисов или SQL для создания бизнес-данных.
- История и выдача превышений: [ClientQueriesIntegrationTests](../../../src/test/java/org/example/testtaskidf/service/ClientQueriesIntegrationTests.java).
  В этом классе уже есть `on`, `success`, `operation`, `finish`, `request` и вложенный `MutableClock`.
  Они не являются публичными общими утилитами: используй их при расширении самого класса;
  для другого класса бери устройство Clock из HTTP-образца, а не импортируй приватные методы.
- Резерв, возврат и завершение: [OperationLifecycleIntegrationTests](../../../src/test/java/org/example/testtaskidf/service/OperationLifecycleIntegrationTests.java).
- Выбор исторического лимита и ограничения PostgreSQL: [ExpenseLimitIntegrationTests](../../../src/test/java/org/example/testtaskidf/service/ExpenseLimitIntegrationTests.java).
- Изолированный расчёт без БД: [ExpenseLimitServiceTests](../../../src/test/java/org/example/testtaskidf/service/ExpenseLimitServiceTests.java).

Перед добавлением теста прочитай подходящий класс и проверь, не покрыт ли сценарий уже.
Если требуется только анализ, не меняй файлы: укажи существующее покрытие и конкретный пробел.

## Подготовь окружение и время

1. Для теста с БД импортируй [PostgresTestConfiguration](../../../src/test/java/org/example/testtaskidf/PostgresTestConfiguration.java):
   контейнер `postgres:17`, `@ServiceConnection`, миграции Flyway при старте. Не подменяй PostgreSQL на H2.
2. [application-test.properties](../../../src/test/resources/application-test.properties) подключается Maven:
   фоновые обработчики отключены, App ID пустой, резервный URL БД намеренно недоступен.
   Не требуй `docker compose up`, ручного создания таблиц или настоящего App ID.
3. Для дат используй `@TestConfiguration`, бин `@Primary Clock` и потокобезопасное изменяемое время,
   как в `JanuaryScenarioApiTests.TimeConfiguration`. Перед HTTP-запросом меняй Clock.
   Дату установки лимита задаёт сервер; поля с датой в `ExpenseLimitRequest` нет.
4. В HTTP-тесте `@Transactional` на тестовом методе не откатывает серверные HTTP-транзакции.
   Повтори изоляцию контекста/контейнера из HTTP-образца (`@DirtiesContext`) или явно обеспечь
   независимые данные. При расширении сервисного класса сохраняй его существующий reset/rollback.

## Построй сценарий по реальному контракту

Сверяй правила с [ExpenseLimitService](../../../src/main/java/org/example/testtaskidf/service/ExpenseLimitService.java),
[ExpenseLimitCreationService](../../../src/main/java/org/example/testtaskidf/service/ExpenseLimitCreationService.java)
и [OperationLifecycleService](../../../src/main/java/org/example/testtaskidf/service/OperationLifecycleService.java).

- Новый счёт имеет лимит 1000 USD отдельно для `product` и `service`. Дата дефолта — начало
  месяца создания операции по Москве. Повторная установка уже активной суммы возвращает
  `409 limit_amount_unchanged`: не начинай сценарий с POST дефолтных 1000 USD.
- `POST /api/v1/client/limits`: `account`, `expense_category`, `amount`; ожидай 201.
  Используй [ExpenseLimitRequest](../../../src/main/java/org/example/testtaskidf/dto/ExpenseLimitRequest.java).
- `POST /api/v1/bank/transactions`: `account_from`, `account_to`, `currency_shortname`,
  `sum`, `expense_category`, `datetime`; ожидай 201 и сохрани UUID из ответа.
  Счета — строки из десяти цифр. Денежные суммы создавай через `new BigDecimal("...")`.
- Приём операции сам по себе не означает успех. Через `POST /api/v1/bank/notifications`
  отправь `transaction_id`, `status: SUCCEEDED`, `completed_at` не раньше `datetime`;
  ожидай 200. Сверь [BankNotificationRequest](../../../src/main/java/org/example/testtaskidf/dto/BankNotificationRequest.java).
- Равенство накопленного расхода лимиту не является превышением. Смена лимита не обнуляет
  месячный расход и не переписывает лимит уже обработанных операций. Пользовательский лимит
  переносится в следующий месяц, а расход считается отдельно по месяцам в UTC+3.
- `GET /api/v1/client/accounts/{account}/transactions/limit-exceeded?page=0&size=20`
  возвращает только `SUCCEEDED` с завершённой проверкой и превышением. Проверяй не только
  наличие ожидаемых элементов, но и точный размер `content`, `total_elements`, `total_pages`,
  исходные поля транзакций и `limit_datetime`, `limit_sum`, `limit_currency_shortname`.
  Порядок — `datetime DESC`, при равенстве — `operation_sequence DESC`.
  Для BigDecimal используй `isEqualByComparingTo`, для дат — точные `OffsetDateTime`.

Январский пример: 2 января 500 USD, 3 января 600 USD; 10 января лимит становится
2000 USD; 11 января 100 USD, 12 января 700 USD, 13 января два расхода по 100 USD.
Первый из них доводит расход ровно до 2000; превышены только 3 января и второй расход
13 января. В `JanuaryScenarioApiTests` две операции 13 января различаются временем.
Не добавляй копию этого теста: расширяй его или покрывай новый граничный случай.

## Если нужен внешний курс

Повтори WireMock из `JanuaryScenarioApiTests`: случайный порт, `@DynamicPropertySource`
для `exchange-rates.base-url`, фиктивный `exchange-rates.app-id`, остановка сервера после теста.
Для работы фонового загрузчика включи `exchange-rates.enabled=true`; подтверждай обращение
через `WireMock.verify`, ожидание ограничивай `Awaitility.atMost`.

Путь провайдера — `/historical/YYYY-MM-DD.json?app_id=...`; тело содержит `base: USD`,
Unix `timestamp`, `rates`. Дату запроса вычисляет [ExchangeRateUtils](../../../src/main/java/org/example/testtaskidf/util/ExchangeRateUtils.java):
текущий день не является закрытым, выходные пропускаются. USD конвертируется сразу;
в январском HTTP-примере WireMock обслуживает стартовую загрузку, а не конвертацию расходов.
Для не-USD сценария включи также `bank.worker-enabled=true` и сократи `bank.worker-delay-ms`,
чтобы реальный обработчик завершил резервирование после конвертации; дождись через
`GET /api/v1/bank/transactions/{id}/status` значения `limit_check_status=COMPLETED` перед
следующей зависимой операцией. Не заменяй ожидание вызовом сервиса из HTTP-теста.

## Запусти подходящую проверку

[ pom.xml ](../../../pom.xml) разделяет тесты по имени класса:

```sh
# Unit-тесты, Docker не требуется:
./mvnw -Dtest=ExpenseLimitServiceTests test
# Один сквозной класс, нужен запущенный Docker:
./mvnw -Dit.test=JanuaryScenarioApiTests verify
# Полная проверка после изменения тестов:
./mvnw verify
```

Surefire исключает `*ApiTests`, `*IntegrationTests`, `*MigrationTests` и
`TestTaskIdfApplicationTests`; их запускает Failsafe в `verify`. Для нового интеграционного
класса выбери соответствующий суффикс. Один `./mvnw test` сквозной тест не выполнит.
На Windows используй `mvnw.cmd`. Нужны JDK 21+ и Docker для интеграционных тестов.

Проверь отчёт конкретного класса в `target/failsafe-reports` или `target/surefire-reports`:
ненулевое число выполненных тестов, отсутствие ошибок и пропусков. `verify` также запускает
Checkstyle, ArchUnit и SpotBugs. Сообщи сценарий, изменённый файл, фактическую команду
и результат; если Docker недоступен, явно отдели написанный тест от подтверждённого запуска.
