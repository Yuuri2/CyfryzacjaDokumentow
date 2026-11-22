# e-skw — cyfryzacja formularzy urzędowych

REST API przyjmuje pojedynczy blankiet i zwraca JSON konfiguracji formularza dla Generatora Formularzy COI. Obsługiwane rozszerzenia: PDF, DOC, DOCX, ODS, XLSX, JPG, JPEG i PNG. Projekt powstał w ramach wyzwania HackNation 2026 „Cyfryzacja formularzy”.

## Stan rozwiązania

- Walidacja rozszerzenia, MIME wykrytego z zawartości, nazwy pliku i limitu rozmiaru.
- Ekstrakcja tekstu z dokumentów przez Apache Tika; OCR obrazów i skanowanych PDF-ów przez Tesseract z językami polskim i angielskim.
- Generowanie pełnej konfiguracji formularza z treści dokumentu. Brakujące pola nie są uzupełniane pustymi wartościami.
- Walidacja wyniku względem schematu importu COI oraz reguł semantycznych przed odpowiedzią API.
- Wykrywanie słowników referencyjnych z materiałów COI, propozycje nowych słowników i walidacja warunków widoczności pól.
- Endpoint katalogu słowników oraz endpoint zbiorczego przetwarzania do 10 plików w jednym żądaniu.
- Docker Compose uruchamia aplikację i nginx API Gateway.
- Generowanie korzysta z modelu Qwen3.5-9B. Można wybrać przypięty endpoint Eden AI (`ovhcloud/Qwen3.5-9B`) albo lokalne Ollama (`qwen3.5:9b`).
- OCR działa lokalnie przez Tesseract; aplikacja nie korzysta z zewnętrznego API OCR.
- W obu trybach OCR odbywa się lokalnie. Tryb `local` wysyła ekstrakt wyłącznie do Ollama w tej samej sieci Docker; tryb `eden` wysyła ekstrakt do Eden AI.

## Wymagania

- Docker Desktop z Docker Compose v2 albo JDK 25 do uruchomienia poza Dockerem.
- Klucz Eden AI (`EDENAI_API_KEY`) tylko dla trybu `AI_PROVIDER=eden`.
- Dla trybu lokalnego: Docker Compose i ok. 16 GB pamięci RAM. Wagi `qwen3.5:9b` Q4 zajmują ok. 6,6 GB; GPU z ok. 8 GB VRAM przyspiesza działanie, ale model może działać na CPU.
- Tesseract z pakietami językowymi `pol` i `eng` przy uruchomieniu poza Dockerem. Obraz aplikacji zawiera te pakiety.
- Schemat importu COI znajduje się w `src/main/resources/coi-form-schema.json`.

## Uruchomienie przez Docker

W katalogu repozytorium skopiuj wzór konfiguracji do `.env`. Tryb domyślny używa Eden AI, więc w tym trybie wpisz `EDENAI_API_KEY`:

```powershell
Copy-Item .env.example .env
notepad .env
docker compose --env-file .env -f Docker/docker-compose.yml up -d --build
docker compose -f Docker/docker-compose.yml ps
Invoke-WebRequest http://localhost:8080/health
```

Interfejs jest dostępny pod `http://localhost:8080/`. Zatrzymanie:

```powershell
docker compose -f Docker/docker-compose.yml down
```

Compose buduje warstwowy obraz aplikacji z przypiętej wersji JDK/JRE, uruchamia ją jako użytkownik nie-root i montuje katalog tymczasowy jako `tmpfs`. Port aplikacji nie jest publikowany bezpośrednio na hoście; dostęp prowadzi przez nginx.

### Tryb lokalny Ollama

Ustaw w `.env` `AI_PROVIDER=local`, a następnie uruchom zestaw z plikiem lokalnym:

```powershell
docker compose --env-file .env -f Docker/docker-compose.yml -f Docker/docker-compose.local.yml up -d --build
docker compose --env-file .env -f Docker/docker-compose.yml -f Docker/docker-compose.local.yml exec ollama ollama pull qwen3.5:9b
```

Ollama udostępnia API tylko wewnątrz sieci Docker; port 11434 nie jest publikowany na hoście. Wagi są pobierane raz do woluminu `ollama-models`. `OLLAMA_NO_CLOUD=1` wyłącza funkcje chmurowe. Aplikacja w trybie lokalnym wybiera wyłącznie adapter Ollama i nie wywołuje Eden AI. Sieć `ollama-egress` służy do pobrania obrazu i wag; można ją odłączyć po pobraniu, jeśli środowisko wymaga całkowitej blokady ruchu wychodzącego.

Zatrzymanie lokalnego zestawu:

```powershell
docker compose --env-file .env -f Docker/docker-compose.yml -f Docker/docker-compose.local.yml down
```

## Uruchomienie lokalne

Tryb lokalny wymaga działającego Ollama z pobranym modelem `qwen3.5:9b`. Ustaw:

```powershell
$env:AI_PROVIDER = "local"
$env:LOCAL_AI_BASE_URL = "http://localhost:11434"
$env:LOCAL_AI_MODEL = "qwen3.5:9b"
$env:MAX_UPLOAD_MB = "20"
.\gradlew.bat bootRun
```

Przy uruchomieniu lokalnym OCR wymaga dostępnego polecenia `tesseract` oraz danych `pol` i `eng`. Aplikacja nie zapisuje uploadów jako trwałych plików; pracuje na bajtach w pamięci, a Tika używa katalogu tymczasowego systemu.

## Konfiguracja

| Zmienna | Domyślnie | Znaczenie |
|---|---:|---|
| `AI_PROVIDER` | `eden` | `eden` lub `local`; wybiera adapter dla tego samego modelu Qwen3.5-9B. |
| `EDENAI_API_KEY` | brak | Klucz wymagany tylko w trybie Eden. W Compose trafia do gatewaya, który dodaje go do żądań. |
| `EDENAI_MODEL` | `ovhcloud/Qwen3.5-9B` | Domyślny europejski endpoint OVHcloud dla Qwen3.5-9B w Eden AI; można ustawić np. `ionos/Qwen/Qwen3.5-9B`. |
| `EDENAI_MAX_OUTPUT_TOKENS` | `8192` | Maksymalny rozmiar odpowiedzi modelu w trybie Eden; ogranicza niekontrolowanie długie generowanie. |
| `LOCAL_AI_MODEL` | `qwen3.5:9b` | Lokalny tag tego samego modelu w Ollama. |
| `LOCAL_AI_BASE_URL` | `http://localhost:11434` | Adres lokalnego API Ollama; w lokalnym Compose to `http://ollama:11434`. |
| `MAX_UPLOAD_MB` | `20` | Jeden limit używany przez nginx, Spring multipart i walidator pliku. |
| `APP_BIND` | `127.0.0.1` | Adres hosta, na którym nginx publikuje port. |
| `APP_PORT` | `8080` | Port nginx na hoście. |
| `EDENAI_BASE_URL` | `https://api.edenai.run/v3` | Bazowy adres Eden AI, używany tylko w trybie Eden poza Compose. |

Wartości limitu pliku w aplikacji i gatewayu pochodzą z tej samej zmiennej `MAX_UPLOAD_MB`.

## REST API

### `POST /api/forms/digitize`

Przyjmuje jeden plik jako `multipart/form-data`, pole `file`:

```powershell
curl.exe -X POST -F "file=@Wniosek.pdf" http://localhost:8080/api/forms/digitize
```

Sukces zwraca `200 application/json` z `title`, `tabs` i `flow`. Niepoprawny upload zwraca `400`; nieczytelny dokument lub wynik, który nie przechodzi walidacji COI, zwraca `422` z opisem błędu, a dla niepoprawnego JSON-a także listą naruszeń. Niedostępność Eden AI zwraca `503`.

### `GET /health`

Gateway odpowiada `200 ok`.

### `GET /api/forms/dictionaries`

Zwraca katalog nazw słowników znalezionych w dostarczonych konfiguracjach referencyjnych. Materiały zawierają odwołania do `Kraje`, `TERYT_DOWODOWY` i `TERYT_4G_DOWODOWY`, ale nie zawierają rekordów tych słowników; odpowiedź oznacza to przez `entriesAvailable: false`.

### `POST /api/forms/digitize/batch`

Przyjmuje wiele plików pod powtarzanym parametrem multipart `files` (maksymalnie 10). Zwraca `207 Multi-Status` z osobnym wynikiem lub błędem dla każdego pliku. Całe żądanie nadal podlega wspólnemu limitowi rozmiaru ustawionemu przez `MAX_UPLOAD_MB`.

### Walidacja wejścia

Akceptowane rozszerzenia to `.pdf`, `.doc`, `.docx`, `.ods`, `.xlsx`, `.jpg`, `.jpeg` i `.png`. Rozszerzenie musi zgadzać się z MIME rozpoznanym na podstawie zawartości. Nazwa może mieć maksymalnie 250 znaków i nie może zawierać `; : > < / \\ | ' * % $`. Rozmiar jest ograniczony przez `MAX_UPLOAD_MB`.

## Przetwarzanie i walidacja JSON

`FileValidationService` waliduje upload i zachowuje zawartość w pamięci. `DocumentParser` używa parsera autodetekcji Apache Tika dla wszystkich obsługiwanych formatów. Strategia OCR AUTO uruchamia Tesseract tylko dla tekstowo ubogich stron PDF; obrazy są obsługiwane przez parser Tesseract Tika. Języki OCR to polski i angielski.

`FormGenerationPrompt` jest wspólny dla obu adapterów, więc Eden AI i Ollama dostają tę samą treść instrukcji, adapter wysyła do Qwen3.5 parametr proszący o wyłączenie kosztownego rozumowania; jego obsługę trzeba potwierdzić w odpowiedzi dostawcy. `EdenFormGenerationClient` wysyła ekstrakt do Qwen3.5-9B przez Eden AI; `LocalQwenFormGenerationClient` używa lokalnego Ollama z tagiem Qwen3.5-9B. Tekst dłuższy niż 60 000 znaków jest jawnie odrzucany, zamiast obcinania i ryzyka utraty pól. `JSONRuleValidator` sprawdza typy zgodnie z dołączonym schematem COI, kompletność i unikalność identyfikatorów, strony, pola, opcje, walidacje, dozwolone walidatory oraz stany `SHOW`/`READ_ONLY`. Odrzuca też niekompletność zgłoszoną przez model. Walidator danych inicjalnych dodatkowo sprawdza `SOURCE.attribute`, zgodność typu pola i `readOnly=true`.

`dictionary-catalog.json` jest inicjalizowany z nazw słowników występujących w przykładowych konfiguracjach konkursowych. Dostępna jest identyfikacja nazw oraz przypisanie `dictionaryName`; same materiały nie dostarczają wartości słownikowych, więc katalog jawnie oznacza brak rekordów. Nieznane nazwy trafiają do osobnego `dictionaryProposals` z nazwą, polem, strukturą i uzasadnieniem; propozycje są oznaczone jako `generated-proposal` i wymagają zatwierdzenia oraz uzupełnienia źródła danych.

Format COI opisuje walidatory jako listę kodów i nie udostępnia w dołączonym schemacie parametrów dla ogólnych `minLength`/`maxLength`. Aplikacja nie dodaje więc takich nieobsługiwanych pól ani nie wymyśla składni parametrów. Warunki `states` obsługują widoczność i tryb tylko do odczytu pól; schemat `flow.links` nie zawiera warunku przejścia, więc nawigacja stron pozostaje zgodna z grafem przejść bez warunków.

Schemat wejściowy organizatora zawiera historyczną literówkę `repetingSectionGrid`; reguły aplikacji dodatkowo sprawdzają poprawną nazwę `repeatingSectionGrid` używaną w przykładowych konfiguracjach i modelu API.

## Dokumentacja i licencje

- [Architektura](docs/ARCHITEKTURA.md)
- [Opis projektu](docs/PROJEKT.md)
- [Plan i ograniczenia](docs/PLAN.md)

Qwen3.5-9B jest publikowany na [Apache-2.0](https://huggingface.co/Qwen/Qwen3.5-9B). Lokalny tag `qwen3.5:9b` używa kwantyzacji Q4 z oficjalnego checkpointu; ze względu na kwantyzację i różnice serwera inferencji wyniki nie będą bitowo identyczne z Eden. Eden AI jest domyślnie przypięte do europejskiego endpointu `ovhcloud/Qwen3.5-9B`; można wybrać inny endpoint przez `EDENAI_MODEL`. [Ollama](https://github.com/ollama/ollama/blob/main/LICENSE) jest na licencji MIT, [Tesseract](https://github.com/tesseract-ocr/tesseract/blob/main/LICENSE) na Apache-2.0, a Apache Tika na Apache-2.0. Zależności tranzytywne i obrazy bazowe nadal wymagają pełnego audytu przed dystrybucją. W trybie lokalnym model nie wysyła danych do Eden AI; wagi pobierane są z rejestru Ollama podczas konfiguracji.
