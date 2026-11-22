**Uwaga dla recenzentów:** Ze względu na równoległy rozwój i trwającą integrację część funkcjonalności znajduje się na dwóch gałęziach. Aby zapoznać się z pełnym zakresem rozwiązania, zalecamy przejrzenie zarówno `main`, jak i `feature/working-pipeline`.

**Uwaga 2:** Wyniki z `feature/working-pipeline` (test A) znajdują się w katalogu `pliki wynikowe`.

# e-skw — cyfryzacja formularzy urzędowych

To REST API przyjmuje pojedynczy blankiet i zwraca konfigurację formularza w JSON-ie dla Generatora Formularzy COI. Obsługiwane formaty: PDF, DOC, DOCX, ODS, XLSX, JPG, JPEG i PNG. Projekt powstał w ramach wyzwania HackNation 2026 „Cyfryzacja formularzy”.

## Stan rozwiązania

- Walidacja rozszerzenia, typu MIME wykrytego na podstawie zawartości, nazwy pliku i rozmiaru uploadu.
- Ekstrakcja tekstu z dokumentów za pomocą Apache Tika. Tesseract wykonuje OCR obrazów i skanowanych PDF-ów, korzystając z danych językowych dla polskiego i angielskiego.
- Generowanie pełnej konfiguracji formularza na podstawie treści dokumentu. Brakujące pola są pomijane zamiast uzupełniania pustymi wartościami.
- Walidacja wyniku względem schematu importu COI i reguł semantycznych przed zwróceniem go przez API.
- Wykrywanie słowników referencyjnych w materiałach COI, proponowanie nowych słowników i walidacja warunków widoczności pól.
- Endpoint katalogu słowników oraz endpoint zbiorczego przetwarzania do 10 plików w jednym żądaniu.
- Docker Compose uruchamia aplikację i nginx jako API Gateway.
- Generowanie formularzy korzysta z modelu Qwen3.5-9B. Można wybrać przypięty endpoint Eden AI (`ovhcloud/Qwen3.5-9B`) albo lokalne Ollama (`qwen3.5:9b`).
- OCR działa lokalnie przez Tesseract; aplikacja nie korzysta z zewnętrznego API OCR.
- W obu trybach OCR odbywa się lokalnie. W trybie `local` wyekstrahowany tekst trafia wyłącznie do Ollama w tej samej sieci Docker; w trybie `eden` jest wysyłany do Eden AI.

## Wymagania

- Docker Desktop z Docker Compose v2 albo JDK 25 do uruchomienia poza Dockerem.
- Klucz Eden AI (`EDENAI_API_KEY`) tylko dla `AI_PROVIDER=eden`.
- Tryb lokalny wymaga Docker Compose i około 16 GB pamięci RAM. Wagi Q4 `qwen3.5:9b` zajmują około 6,6 GB; karta GPU z około 8 GB VRAM może przyspieszyć działanie, ale model może pracować na CPU.
- Przy uruchamianiu poza Dockerem wymagany jest Tesseract z pakietami językowymi `pol` i `eng`. Obraz aplikacji zawiera te pakiety.
- Schemat importu COI znajduje się w `src/main/resources/coi-form-schema.json`.

## Uruchomienie przez Docker

W katalogu repozytorium skopiuj przykładową konfigurację do `.env`. Domyślny tryb korzysta z Eden AI, więc ustaw w tym pliku `EDENAI_API_KEY`:

```powershell
Copy-Item .env.example .env
notepad .env
docker compose --env-file .env -f Docker/docker-compose.yml up -d --build
docker compose -f Docker/docker-compose.yml ps
Invoke-WebRequest http://localhost:8080/health
```

Interfejs WWW jest dostępny pod `http://localhost:8080/`. Zatrzymanie usług:

```powershell
docker compose -f Docker/docker-compose.yml down
```

Compose buduje warstwowy obraz aplikacji z przypiętych wersji JDK/JRE, uruchamia ją jako użytkownik nie-root i montuje katalog tymczasowy jako `tmpfs`. Port aplikacji nie jest publikowany bezpośrednio na hoście — żądania przechodzą przez nginx.

### Lokalny tryb Ollama

Ustaw `AI_PROVIDER=local` w pliku `.env`, a następnie uruchom lokalny zestaw:

```powershell
docker compose --env-file .env -f Docker/docker-compose.yml -f Docker/docker-compose.local.yml up -d --build
docker compose --env-file .env -f Docker/docker-compose.yml -f Docker/docker-compose.local.yml exec ollama ollama pull qwen3.5:9b
```

API Ollama jest dostępne wyłącznie wewnątrz sieci Docker; port 11434 nie jest publikowany na hoście. Wagi są pobierane raz do woluminu `ollama-models`. Ustawienie `OLLAMA_NO_CLOUD=1` wyłącza funkcje chmurowe Ollama. W trybie lokalnym aplikacja wybiera wyłącznie adapter Ollama i nie wywołuje Eden AI. Sieć `ollama-egress` służy do pobrania obrazu i wag; można ją odłączyć po pobraniu, jeśli środowisko wymaga całkowitej blokady ruchu wychodzącego.

Zatrzymanie lokalnych usług:

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

Przy uruchomieniu lokalnym OCR wymaga polecenia `tesseract` oraz danych językowych `pol` i `eng`. Aplikacja nie zapisuje uploadowanych plików na stałe; przetwarza bajty w pamięci, a Tika korzysta z systemowego katalogu tymczasowego.

## Konfiguracja

| Zmienna | Domyślnie | Opis |
|---|---:|---|
| `AI_PROVIDER` | `eden` | `eden` lub `local`; wybiera adapter dla modelu Qwen3.5-9B. |
| `EDENAI_API_KEY` | nieustawiony | Wymagany tylko w trybie Eden. W Compose gateway dodaje go do żądań. |
| `EDENAI_MODEL` | `ovhcloud/Qwen3.5-9B` | Domyślny europejski endpoint OVHcloud dla Qwen3.5-9B w Eden AI. Aby wybrać inny endpoint, można ustawić np. `ionos/Qwen/Qwen3.5-9B`. |
| `LOCAL_AI_MODEL` | `qwen3.5:9b` | Lokalny tag tego samego modelu w Ollama. |
| `LOCAL_AI_BASE_URL` | `http://localhost:11434` | Adres lokalnego API Ollama; w lokalnym Compose należy użyć `http://ollama:11434`. |
| `MAX_UPLOAD_MB` | `20` | Wspólny limit uploadu używany przez nginx, obsługę multipart w Springu i walidator plików. |
| `APP_BIND` | `127.0.0.1` | Adres hosta, na którym nginx publikuje port. |
| `APP_PORT` | `8080` | Port nginx na hoście. |
| `EDENAI_BASE_URL` | `https://api.edenai.run/v3` | Bazowy adres Eden AI, używany tylko w trybie Eden poza Compose. |

Aplikacja i gateway korzystają z tej samej wartości `MAX_UPLOAD_MB`.

## REST API

### `POST /api/forms/digitize`

Przyjmuje jeden plik jako `multipart/form-data` w polu `file`:

```powershell
curl.exe -X POST -F "file=@Wniosek.pdf" http://localhost:8080/api/forms/digitize
```

Poprawne żądanie zwraca `200 application/json` z polami `title`, `tabs` i `flow`. Niepoprawny upload zwraca `400`. Nieczytelny dokument albo wynik, który nie przechodzi walidacji COI, zwraca `422` z opisem błędu, a dla niepoprawnego JSON-a także listą naruszeń. Niedostępność Eden AI zwraca `503`.

### `GET /health`

Gateway zwraca `200 ok`.

### `GET /api/forms/dictionaries`

Zwraca katalog nazw słowników znalezionych w dostarczonych konfiguracjach referencyjnych. Materiały odwołują się do `Kraje`, `TERYT_DOWODOWY` i `TERYT_4G_DOWODOWY`, ale nie zawierają rekordów tych słowników; odpowiedź wskazuje to przez `entriesAvailable: false`.

### `POST /api/forms/digitize/batch`

Przyjmuje wiele plików pod powtarzanym parametrem multipart `files` (maksymalnie 10). Zwraca `207 Multi-Status` z osobnym wynikiem lub błędem dla każdego pliku. Całe żądanie podlega wspólnemu limitowi rozmiaru `MAX_UPLOAD_MB`.

### Walidacja wejścia

Akceptowane rozszerzenia to `.pdf`, `.doc`, `.docx`, `.ods`, `.xlsx`, `.jpg`, `.jpeg` i `.png`. Rozszerzenie musi zgadzać się z typem MIME rozpoznanym na podstawie zawartości pliku. Nazwa może mieć maksymalnie 250 znaków i nie może zawierać `; : > < / \\ | ' * % $`. Rozmiar uploadu ogranicza `MAX_UPLOAD_MB`.

## Przetwarzanie i walidacja JSON-a

`FileValidationService` waliduje upload i przechowuje jego zawartość w pamięci. `DocumentParser` korzysta z parsera autodetekcji Apache Tika dla wszystkich obsługiwanych formatów. Strategia OCR AUTO uruchamia Tesseract tylko dla stron PDF o małej ilości tekstu; obrazy obsługuje parser Tesseract dla Tika. Języki OCR to polski i angielski.

`FormGenerationPrompt` jest wspólny dla obu adapterów, dlatego Eden AI i Ollama otrzymują te same instrukcje. Adapter prosi Qwen3.5 o wyłączenie kosztownego rozumowania; obsługę tego parametru trzeba potwierdzić w odpowiedzi dostawcy. `EdenFormGenerationClient` wysyła wyekstrahowany tekst do Qwen3.5-9B przez Eden AI, a `LocalQwenFormGenerationClient` korzysta z lokalnego Ollama z tagiem Qwen3.5-9B. Tekst dłuższy niż 60 000 znaków jest jawnie odrzucany zamiast obcinania, które mogłoby pominąć pola. `JSONRuleValidator` sprawdza typy zgodnie z dołączonym schematem COI, kompletność i unikalność identyfikatorów, stron, pól, opcji, walidacji, dozwolonych walidatorów oraz stanów `SHOW`/`READ_ONLY`. Odrzuca również wynik oznaczony przez model jako niekompletny. Walidator danych początkowych dodatkowo sprawdza `SOURCE.attribute`, zgodność typu pola oraz `readOnly=true`.

`dictionary-catalog.json` jest inicjalizowany nazwami słowników znalezionymi w przykładowych konfiguracjach konkursowych. Katalog obsługuje rozpoznawanie nazw i przypisywanie `dictionaryName`; dostarczone materiały nie zawierają wartości słownikowych, dlatego katalog jawnie oznacza brak rekordów. Nieznane nazwy trafiają do osobnego `dictionaryProposals` wraz z nazwą, polem, strukturą i uzasadnieniem. Propozycje są oznaczone jako `generated-proposal` i wymagają zatwierdzenia oraz wskazania źródła danych.

Format COI opisuje walidatory jako listę kodów, a dołączony schemat nie definiuje parametrów dla ogólnych `minLength`/`maxLength`. Aplikacja nie dodaje więc nieobsługiwanych właściwości ani nie wymyśla składni parametrów. Warunki `states` obsługują widoczność pól i tryb tylko do odczytu; schemat `flow.links` nie zawiera warunku przejścia, dlatego nawigacja stron przebiega po grafie przejść bez warunków.

Schemat wejściowy organizatora zawiera historyczną literówkę `repetingSectionGrid`; reguły aplikacji sprawdzają też poprawną nazwę `repeatingSectionGrid`, używaną w przykładowych konfiguracjach i modelu API.

## Dokumentacja i licencje

- [Architektura](docs/ARCHITEKTURA.md)
- [Opis projektu](docs/PROJEKT.md)
- [Plan i ograniczenia](docs/PLAN.md)

Qwen3.5-9B jest publikowany na licencji [Apache-2.0](https://huggingface.co/Qwen/Qwen3.5-9B). Lokalny tag `qwen3.5:9b` używa kwantyzacji Q4 oficjalnego checkpointu; ze względu na kwantyzację i różnice serwera inferencji wyniki nie będą bitowo identyczne z Eden. Eden AI domyślnie korzysta z europejskiego endpointu `ovhcloud/Qwen3.5-9B`; inny można wybrać przez `EDENAI_MODEL`. [Ollama](https://github.com/ollama/ollama/blob/main/LICENSE) jest na licencji MIT, [Tesseract](https://github.com/tesseract-ocr/tesseract/blob/main/LICENSE) na Apache-2.0, a Apache Tika na Apache-2.0. Zależności tranzytywne i obrazy bazowe nadal wymagają pełnego audytu przed dystrybucją. W trybie lokalnym model nie wysyła danych do Eden AI; wagi są pobierane z rejestru Ollama podczas konfiguracji.
