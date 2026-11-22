# e-skw – cyfryzacja formularzy urzędowych

Usługa REST przyjmująca blankiety formularzy (PDF, DOC, DOCX, ODS, XLSX, JPG, JPEG, JPE, PNG) i zwracająca **JSON konfiguracji e-formularza** (`title`, `tabs`, `flow`) w strukturze oczekiwanej przez Generator Formularzy COI. Projekt powstał na hackathon HackNation 2026 (zadanie COI „Cyfryzacja formularzy”). Strona główna i endpointy API są publiczne i nie wymagają tokenu JWT.

Architekturę opisuje [`docs/ARCHITEKTURA.md`](docs/ARCHITEKTURA.md), a założenia i decyzje projektowe [`docs/PROJEKT.md`](docs/PROJEKT.md) i [`docs/PLAN.md`](docs/PLAN.md).

## Stan implementacji

| Element | Stan |
|---|---|
| Walidacja pliku (rozszerzenie, MIME wg treści, nazwa ≤ 250 znaków i zakazane znaki, rozmiar) | gotowe |
| Docker: aplikacja + nginx jako API Gateway, hardening | gotowe |
| Klient Eden AI (upload + OCR) za gatewayem | gotowy, jeszcze niepodpięty do potoku |
| `POST /api/forms/digitize` zwraca `FormDefinition` | **wersja szkieletowa**: zwraca stały, przykładowy formularz (nie analizuje jeszcze treści pliku) |
| Ekstrakcja → FormSpec → kompilator → walidator reguł COI | w budowie |
| Dostęp publiczny bez tokena JWT | gotowe |
| Słowniki, `states`, frontend, wizualizator | zaplanowane |

## Wymagania

- Docker z Docker Compose v2.
- Klucz API Eden AI (`EDENAI_API_KEY`).
- JWT nie jest wymagany do pracy z aplikacją ani stroną.
- Wszystkie endpointy formularza i strony głównej są dostępne bez nagłówka `Authorization`.
- Do pracy bez Dockera: JDK 25 (Gradle wrapper jest w repo).

## Budowanie i uruchomienie (Docker)

1. Skonfiguruj środowisko:

   ```bash
   cp .env.example .env
   # uzupełnij EDENAI_API_KEY w .env (plik .env nie jest śledzony przez git)
   ```

2. Zbuduj obraz i uruchom z korzenia repozytorium:

   ```bash
   docker compose --env-file .env -f Docker/docker-compose.yml up -d --build
   ```

3. Sprawdź, czy działa:

   ```bash
   docker compose -f Docker/docker-compose.yml ps     # gateway i app w stanie Up
   curl -i http://localhost:8080/health               # 200 ok
   ```

4. Zatrzymanie: `docker compose -f Docker/docker-compose.yml down`.

Po zmianie `EDENAI_API_KEY` odtwórz gateway (klucz jest czytany tylko przy starcie):
`docker compose --env-file .env -f Docker/docker-compose.yml up -d --force-recreate gateway`.

### Uruchomienie lokalne (bez Dockera)

```bash
export EDENAI_API_KEY=...        # w tym trybie aplikacja woła Eden bezpośrednio
./gradlew bootRun
```

Testy: `./gradlew test` (uwaga: `EdenConnectionTest` łączy się z zewnętrznym API Eden i wymaga klucza).

## Konfiguracja

Zmienne środowiskowe (plik `.env`, wzór w `.env.example`):

| Zmienna | Domyślnie | Opis |
|---|---|---|
| `EDENAI_API_KEY` | – (wymagana) | Klucz Eden AI. Trafia **wyłącznie** do kontenera `gateway`; kontener `app` go nie widzi. |
| `MAX_UPLOAD_MB` | `20` | Maksymalny rozmiar pliku. Egzekwowany w nginx (413) i w aplikacji (`spring.servlet.multipart.*`). |
| `APP_BIND` | `127.0.0.1` | Interfejs, na którym publikowany jest port. Dla dostępu z sieci ustaw `0.0.0.0`. |
| `APP_PORT` | `8080` | Port na hoście. |

Parametry aplikacji w `src/main/resources/application.yaml`: `app.upload.max-size-bytes` (limit rozmiaru sprawdzany w `FileValidationService`), adresy i model Eden (`app.edenai.*`). W kontenerze adres bazowy Eden to `EDENAI_BASE_URL=http://gateway:8081/v3` (ustawiany w `docker-compose.yml`).

## Uwierzytelnianie

Aplikacja nie wymaga uwierzytelnienia ani tokena. Strona główna, `/api/forms/digitize`, `/upload` i `/health` są publiczne.

## REST API

Wszystkie wywołania idą przez gateway: `http://localhost:8080`.

### `POST /api/forms/digitize`

Przyjmuje jeden plik formularza i zwraca JSON konfiguracji e-formularza.

```bash
curl -X POST -F "file=@Wniosek.pdf" http://localhost:8080/api/forms/digitize
```

- Body: `multipart/form-data`, pole `file`.
- Odpowiedź `200`: `application/json`:

  ```json
  {
    "title": "…",
    "tabs": [
      { "id": "uuid", "title": "…", "type": "MAIN|FINISH",
        "questions": [ { "x": 0, "y": 0, "rows": 1, "cols": 1, "field": { "id": "uuid", "type": "TEXT_FIELD", "techName": "…", "title": "…", "validation": ["REQUIRED"] } } ] }
    ],
    "flow": { "links": [ { "id": "uuid", "source": "<tab id>", "target": "<tab id>" } ] }
  }
  ```

  Pola o wartości `null` są pomijane. Obecnie odpowiedź jest **szkieletowa** (patrz „Stan implementacji”).

### `PUT /upload`

Waliduje i parsuje plik (multipart, pole `file`); zwraca `200` bez treści. Endpoint pomocniczy do testów walidacji uploadu.

```bash
curl -X PUT -F "file=@Wniosek.pdf" http://localhost:8080/upload
```

### `GET /health`

Odpowiada `200 ok` (obsługiwane przez gateway, bez tokenu).

### Walidacja pliku

Plik jest odrzucany, gdy: jest pusty, nie ma nazwy lub rozszerzenia, nazwa ma więcej niż 250 znaków albo zawiera którykolwiek ze znaków `; : > < / \ | ' * % $ ?`, rozmiar przekracza limit, rozszerzenie nie należy do `pdf, doc, docx, ods, xlsx, jpg, jpeg, jpe, png`, a typ MIME wykryty (Tika) z treści nie zgadza się z rozszerzeniem. Limity gatewaya: 20 MB (`MAX_UPLOAD_MB`) → `413`, ~10 żądań/s na IP (burst 20) → `429`.

Uwaga: przy odrzuconym pliku aplikacja na razie zwraca ogólny błąd serwera (500) zamiast 4xx.

## Bezpieczeństwo kontenerów

- Obrazy oficjalne, lekkie (Alpine 3.24), z **przypiętymi tagami i digestami** (bez `latest`).
- Procesy działają jako użytkownicy nie-root (aplikacja `10001`, nginx `101`), `cap_drop: ALL`, `no-new-privileges`, system plików tylko do odczytu, `/tmp` w pamięci (`tmpfs`, `noexec`) – pliki użytkowników nie są zapisywane trwale.
- Na hoście publikowany jest wyłącznie port 8080 gatewaya (domyślnie tylko `127.0.0.1`).
- Kontener `app` jest w sieci `internal` (bez dostępu do internetu). Do Eden AI wychodzi tylko nginx, z allowlistą trzech endpointów (`POST /v3/upload`, `/v3/universal-ai`, `/v3/chat/completions`), a port wewnętrzny 8081 jest dostępny tylko z podsieci `backend`.
- Sekrety (`EDENAI_API_KEY`) wyłącznie ze zmiennych środowiskowych w runtime; limity logów (3 × 10 MB).

## Skrypty testowe

Skrypty w katalogu `scripts/` służą do pomocniczych testów; obecna aplikacja nie wymaga ustawienia lub przekazywania tokena JWT.

## Struktura repozytorium

```
src/main/java/com/hacknation/cyfryzacja/
  security/      SecurityConfig – publiczny dostęp do aplikacji
  controllers/   FormDigitizerController (POST /api/forms/digitize), UploadController (PUT /upload)
  walidacja/     FileValidationService – walidacja pliku
  parsers/       DocumentParser (Tika) – ekstrakcja tekstu
  ai/            EdenOcrClient – klient Eden AI (upload + OCR)
  services/      UploadService, FTJGenerator (generator formularza)
  JSONRes/       FormDefinition – model docelowego JSON-a
Docker/          Dockerfile, docker-compose.yml, nginx/default.conf.template (API Gateway)
scripts/         pomocnicze skrypty testowe i narzędziowe
docs/            PROJEKT.md, PLAN.md, ARCHITEKTURA.md
Materiały/       materiały konkursowe (zadanie, regulamin, formularze)
```

## Licencje i ograniczenia

Rozwiązanie ma opierać się wyłącznie na bibliotekach open source bez licencji copyleft (wymóg zadania); audyt zależności (w tym `tika-parsers-standard-package`) jest jeszcze do wykonania. Eden AI jest używane jako zewnętrzny model zgodnie z ustaleniem z mentorem COI.
