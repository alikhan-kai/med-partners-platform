# MedPartners Platform — Автоматическая обработка прайс-листов клиник

Система автоматической обработки архива прайс-листов клиник-партнёров: извлечение данных из документов разных форматов, нормализация, верификация и предоставление REST API для поиска услуг и цен.

Проект разработан в рамках хакатона MedPartners 2025.

---

## Содержание

- [Обзор архитектуры](#обзор-архитектуры)
- [Стек технологий](#стек-технологий)
- [Структура проекта](#структура-проекта)
- [База данных](#база-данных)
- [Парсинг документов](#парсинг-документов)
- [API](#api)
- [Запуск проекта](#запуск-проекта)
- [Конфигурация](#конфигурация)

---

## Обзор архитектуры

```
┌──────────────────────────────────────────────────────────────┐
│                        HTTP Clients                          │
│          (Фронтенд / Внешние системы / Swagger UI)           │
└──────────────────┬────────────────────────────┬─────────────┘
                   │                            │
         ┌─────────▼──────────┐    ┌────────────▼────────────┐
         │  UploadController  │    │   DocumentController    │
         │  POST /upload-zip  │    │  GET /services          │
         └─────────┬──────────┘    │  GET /partners          │
                   │               └────────────┬────────────┘
         ┌─────────▼──────────┐                 │
         │   ParsingService   │    ┌────────────▼────────────┐
         │  Распаковка ZIP    │    │     AdminController     │
         │  Очередь файлов    │    │  GET /unmatched         │
         │  Zip Slip guard    │    │  POST /match            │
         └─────────┬──────────┘    └─────────────────────────┘
                   │
         ┌─────────▼──────────┐
         │   ParserFactory    │  ◄── auto-detect: text PDF / scan PDF / DOCX / XLSX
         └──┬──────┬──────┬───┘
            │      │      │
     ┌──────▼─┐ ┌──▼────┐ ┌▼──────────┐ ┌──────────────┐
     │PdfPar- │ │Docx-  │ │ExcelPar-  │ │ScanPdfParser │
     │ser     │ │Parser │ │ser        │ │(Tess4J OCR)  │
     │PDFBox  │ │POI    │ │POI        │ │rus+eng langs │
     └──────┬─┘ └──┬────┘ └─┬─────────┘ └──────┬───────┘
            └──────┴─────────┴──────────────────┘
                             │
                    ParsedDocument
                             │
              ┌──────────────▼───────────────┐
              │   DocumentProcessingService   │
              │  - Валидация позиций          │
              │  - Детектирование аномалий    │
              │  - Версионирование цен        │
              │  - Дедупликация               │
              │  - Привязка к Service catalog │
              └──────────────┬───────────────┘
                             │
              ┌──────────────▼───────────────┐
              │          PostgreSQL           │
              │  partners / price_documents  │
              │  price_items / services      │
              └──────────────────────────────┘
```

---

## Стек технологий

| Слой | Технология |
|---|---|
| Язык / Runtime | Java 21 |
| Фреймворк | Spring Boot 4.1.0 (Web MVC, Data JPA, Validation) |
| База данных | PostgreSQL |
| ORM | Hibernate / Spring Data JPA |
| PDF (текст) | Apache PDFBox 3.0.2 |
| PDF (скан / OCR) | Tess4J 5.12.0 (Tesseract, языки `rus+eng`) |
| DOCX | Apache POI XWPF 5.2.5 |
| XLSX / XLS | Apache POI SS (WorkbookFactory) 5.2.5 |
| Маппинг DTO | MapStruct 1.5.5 |
| Boilerplate | Lombok |
| Сборка | Gradle Wrapper |
| Тесты | JUnit 5 / Spring Boot Test |

---

## Структура проекта

```
src/main/java/kz/medpartners/core/
│
├── controller/
│   └── UploadController.java         # POST /api/v1/documents/upload-zip
│
├── web/
│   ├── DocumentController.java       # GET /services/{id}/partners, POST /documents/upload-parsed
│   └── AdminController.java          # GET /unmatched, POST /match
│
├── service/
│   ├── ParsingService.java           # Распаковка ZIP, оркестрация парсинга
│   ├── DocumentProcessingService.java# Валидация, версионирование, персистенция
│   └── dto/
│       ├── PriceUploadRequest.java
│       ├── ServicePartnerResponse.java
│       └── UnmatchedItemResponse.java
│
├── parser/
│   ├── PriceParser.java              # Интерфейс парсеров
│   ├── ParserFactory.java            # Маршрутизация по типу файла
│   ├── ParserUtils.java              # Извлечение названия клиники / даты из имени файла
│   ├── PdfParser.java                # Текстовые PDF через PDFBox
│   ├── ScanPdfParser.java            # Отсканированные PDF через Tess4J (OCR)
│   ├── DocxParser.java               # DOCX через Apache POI XWPF
│   └── ExcelParser.java              # XLSX / XLS через Apache POI SS
│
├── domain/
│   ├── PartnerEntity.java
│   ├── PriceDocumentEntity.java
│   ├── PriceItemEntity.java
│   └── ServiceEntity.java
│
├── repository/
│   ├── PartnerRepository.java
│   ├── PriceDocumentRepository.java
│   ├── PriceItemRepository.java
│   └── ServiceRepository.java
│
└── model/
    ├── ParsedDocument.java
    └── PriceItem.java
```

---

## База данных

Схема разворачивается автоматически через `spring.jpa.hibernate.ddl-auto=update`.

```
┌─────────────────────────┐         ┌──────────────────────────────┐
│        partners         │         │       price_documents        │
├─────────────────────────┤         ├──────────────────────────────┤
│ partner_id  UUID (PK)   │◄────────│ doc_id         UUID (PK)     │
│ name        TEXT        │         │ partner_id     UUID (FK)     │
│ city        VARCHAR(100)│         │ file_name      TEXT          │
│ address     TEXT        │         │ file_format    VARCHAR       │
│ bin         VARCHAR(12) │         │ effective_date DATE          │
│ contact_email VARCHAR   │         │ parsed_at      TIMESTAMP     │
│ contact_phone VARCHAR   │         │ parse_status   VARCHAR       │
│ is_active   BOOLEAN     │         │   (pending/processing/       │
│ created_at  TIMESTAMP   │         │    done/error/needs_review)  │
│ updated_at  TIMESTAMP   │         │ parse_log      TEXT          │
└─────────────────────────┘         └──────────────────────────────┘
                                                   │
                                                   │
┌──────────────────────────┐         ┌─────────────▼────────────────┐
│        services          │         │         price_items          │
├──────────────────────────┤         ├──────────────────────────────┤
│ service_id  UUID (PK)    │◄────────│ item_id           UUID (PK)  │
│ service_name TEXT        │         │ doc_id            UUID (FK)  │
│ synonyms    JSONB        │         │ partner_id        UUID (FK)  │
│ category    VARCHAR      │         │ service_name_raw  TEXT       │
│ icd_code    VARCHAR      │         │ service_code_source VARCHAR  │
│ is_active   BOOLEAN      │         │ service_id        UUID (FK)  │
└──────────────────────────┘         │ price_resident_kzt DECIMAL  │
                                     │ price_nonresident_kzt DECIMAL│
                                     │ price_original    DECIMAL    │
                                     │ currency_original VARCHAR    │
                                     │ is_verified       BOOLEAN    │
                                     │ verification_note TEXT       │
                                     │ effective_date    DATE       │
                                     │ is_active         BOOLEAN    │
                                     └──────────────────────────────┘
```

---

## Парсинг документов

### Маршрутизация по типу файла

`ParserFactory` автоматически определяет нужный парсер:

```
Файл входит
     │
     ├─ .docx  ──────────────────────────────► DocxParser (Apache POI XWPF)
     │
     ├─ .xlsx / .xls  ────────────────────────► ExcelParser (Apache POI WorkbookFactory)
     │
     └─ .pdf
           │
           ├─ текст первой страницы >= 50 символов ──► PdfParser (PDFBox)
           │
           └─ текст < 50 символов (скан)  ──────────► ScanPdfParser (Tess4J OCR)
```

### Возможности каждого парсера

**PdfParser** — текстовые PDF:
- Извлечение текста через `PDFTextStripper`
- Поиск заголовков таблиц по ключевым словам (код, наименование, стоимость, цена)
- Парсинг строк регулярными выражениями

**ScanPdfParser** — отсканированные PDF:
- Рендеринг страниц в `BufferedImage` через `PDFRenderer`
- OCR через Tess4J с языковыми пакетами `rus+eng`
- Постобработка результата OCR регулярными выражениями для извлечения цен

**DocxParser** — DOCX:
- Полный обход таблиц через `XWPFDocument`
- Динамическое определение колонок: поиск заголовков в первых 2 строках (код / шифр, наименование, стоимость, нерезидент)

**ExcelParser** — XLSX / XLS:
- Обход всех листов книги
- Поиск строки заголовков в первых 15 строках каждого листа
- Поддержка `.xls` через `WorkbookFactory`

### Валидация и версионирование

При каждом документе `DocumentProcessingService` выполняет следующие проверки:

| Проверка | Результат |
|---|---|
| Пустое название услуги | Строка пропускается, запись в лог |
| Цена изменилась более чем на 50% | Статус документа `needs_review`, позиция неактивна до ручного подтверждения |
| Дублирование позиции (та же клиника, та же услуга) | Старая запись деактивируется (`is_active = false`), новая сохраняется |
| Отсутствие привязки к справочнику (`service_id = null`) | Позиция попадает в очередь `unmatched` |

История цен не удаляется — старые версии помечаются `is_active = false` и сохраняются бессрочно.

### Защита от Zip Slip

При распаковке архива `ParsingService` проверяет канонический путь каждого извлекаемого файла. Попытки выйти за пределы временной директории блокируются и логируются.

---

## API

Базовый URL: `http://localhost:8080/api/v1`

### Загрузка архива

```
POST /documents/upload-zip
Content-Type: multipart/form-data

Параметры:
  file — ZIP-архив с прайс-листами клиник (.pdf, .docx, .xlsx, .xls)

Ответ 200:
  [ "uuid1", "uuid2", ... ]   — список ID созданных документов
```

### Услуги и партнёры

```
GET /services/{id}/partners
  Список партнёров, оказывающих услугу, с ценами резидент/нерезидент.

  Ответ 200: [ { partnerId, partnerName, city, address,
                 priceResidentKzt, priceNonresidentKzt, effectiveDate }, ... ]
```

### Административные эндпоинты

```
GET /admin/unmatched
  Список позиций прайсов без привязки к справочнику услуг.

  Ответ 200: [ { itemId, docId, fileName, serviceNameRaw, priceResidentKzt }, ... ]


POST /admin/match?itemId={uuid}&serviceId={uuid}
  Ручное сопоставление позиции с эталонной услугой.
  Позиция получает is_verified = true и становится активной.

  Ответ 200: (пустой body)
```

### Внутренний эндпоинт

```
POST /documents/upload-parsed
  Приём структурированных данных от внешнего парсера.
  Body: PriceUploadRequest (JSON)
```

---

## Запуск проекта

### Предварительные требования

- Java 21+
- PostgreSQL 14+
- Tesseract OCR (`tesseract-ocr` + языковые пакеты `tessdata-rus`)

### 1. Установка Tesseract

**Ubuntu / Debian:**
```bash
sudo apt-get install tesseract-ocr tesseract-ocr-rus
```

**macOS:**
```bash
brew install tesseract tesseract-lang
```

**Windows:**
Скачать установщик с https://github.com/UB-Mannheim/tesseract/wiki, выбрать Russian language pack при установке.

### 2. Настройка PostgreSQL

```sql
CREATE DATABASE medpartners;
CREATE USER medpartners WITH PASSWORD 'your_password';
GRANT ALL PRIVILEGES ON DATABASE medpartners TO medpartners;
```

### 3. Конфигурация приложения

Отредактировать `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/medpartners
spring.datasource.username=medpartners
spring.datasource.password=your_password
```

Если Tesseract установлен в нестандартное расположение, задать переменную окружения:

```bash
export TESSDATA_PREFIX=/usr/share/tesseract-ocr/4.00/tessdata
```

### 4. Сборка и запуск

```bash
# Клонировать репозиторий
git clone <repo-url>
cd med-partners-platform

# Собрать проект
./gradlew build

# Запустить
./gradlew bootRun
```

Приложение поднимется на `http://localhost:8080`.

### 5. Загрузка архива прайсов

```bash
curl -X POST http://localhost:8080/api/v1/documents/upload-zip \
  -F "file=@/path/to/prices.zip"
```

---

## Конфигурация

Все параметры задаются в `src/main/resources/application.properties`:

| Параметр | По умолчанию | Описание |
|---|---|---|
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/postgres` | URL базы данных |
| `spring.datasource.username` | `postgres` | Пользователь БД |
| `spring.datasource.password` | `1234` | Пароль БД |
| `spring.jpa.hibernate.ddl-auto` | `update` | Стратегия миграции схемы |
| `spring.jpa.show-sql` | `true` | Логирование SQL-запросов |
| `server.port` | `8080` | Порт приложения |
| `TESSDATA_PREFIX` (env) | системный | Путь к языковым пакетам Tesseract |

---

## Тесты

```bash
./gradlew test
```

Покрытые сценарии:

- `ParserFactoryTest` — маршрутизация по типу файла, обнаружение скан-PDF
- `PdfParserTest` — извлечение позиций из текстового PDF
- `DocxParserTest` — парсинг таблиц DOCX
- `ExcelParserTest` — парсинг многолистовых XLSX
- `ParserUtilsTest` — извлечение названия клиники и даты из имени файла
- `ParsingServiceTest` — интеграционный сценарий обработки ZIP-архива

---

## Roadmap

- Фронтенд на React: поиск услуги по справочнику, страница партнёра, административный раздел с очередью верификации
- Нечёткое сопоставление с эталонным справочником услуг (cosine similarity / RapidFuzz)
- Конвертация валют по курсу НБ РК на дату прайса
- OpenAPI / Swagger документация
- Docker Compose для локального запуска всего окружения
