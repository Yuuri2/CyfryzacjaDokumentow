# Architektura

## Przepływ żądania

1. Klient wysyła jeden plik do `POST /api/forms/digitize`.
2. Nginx ogranicza rozmiar żądania i częstotliwość wywołań, po czym przekazuje plik do aplikacji Spring.
3. `FileValidationService` sprawdza nazwę, rozszerzenie, rozmiar i MIME rozpoznany z bajtów. Zawartość pozostaje w pamięci procesu.
4. `DocumentParser` używa autodetekcji Apache Tika dla PDF, DOC, DOCX, ODS, XLSX i obrazów. Tesseract OCR (polski i angielski) obsługuje obrazy i skanowane strony PDF; konfiguracja PDF łączy OCR z ekstrakcją tekstu.
5. `FormGenerationPrompt` buduje wspólny prompt. `AI_PROVIDER=eden` wybiera `EdenFormGenerationClient` z endpointem `ionos/Qwen/Qwen3.5-9B`; `AI_PROVIDER=local` wybiera `LocalQwenFormGenerationClient` i lokalne Ollama `qwen3.5:9b`. Oba adaptery używają tego samego modelu Qwen3.5-9B i promptu.
6. `JSONRuleValidator` sprawdza wynik względem `coi-form-schema.json` oraz dodatkowych reguł semantycznych. Dopiero poprawny wynik jest mapowany na `FormDefinition` i zwracany.
7. `DictionaryCatalog` porównuje `dictionaryName` z katalogiem wyprowadzonym z formularzy referencyjnych. Nieznane nazwy trafiają do osobnego `dictionaryProposals`; wpisy propozycji nie są traktowane jako gotowe słowniki.

## Komponenty

- `FileValidationService` — walidacja i buforowanie bajtów pojedynczego uploadu.
- `DocumentParser` — ekstrakcja Tika i OCR Tesseract.
- `FormGenerationClient` — wspólny interfejs modelu.
- `FormGenerationPrompt` — wspólne instrukcje i treść dla API oraz lokalnego modelu.
- `EdenFormGenerationClient` — przypięty endpoint Qwen3.5-9B przez Eden AI.
- `LocalQwenFormGenerationClient` — ten sam model Qwen3.5-9B uruchomiony w Ollama.
- `JSONRuleValidator` — walidacja schematu oraz reguł formularza i przepływu.
- `DictionaryCatalog` — katalog nazw słowników z materiałów referencyjnych, dopasowanie jednoznacznych aliasów i oznaczanie propozycji.
- `FTJGenerator` — orkiestracja ekstrakcji, generowania, walidacji i mapowania odpowiedzi.
- `FormDigitizerController` — REST API i mapowanie błędów na odpowiedzi HTTP.
- `Docker/docker-compose.yml` — aplikacja oraz gateway nginx.

## Granice danych i sieć

Upload jest przechowywany w pamięci aplikacji. Biblioteki parsujące mogą używać katalogu tymczasowego; kontener aplikacji montuje `/tmp` jako niepersistowany `tmpfs`. Aplikacja działa w wewnętrznej sieci Compose. W trybie Eden gateway przekazuje tekst ekstrakcji do przypiętego endpointu Qwen w Eden AI. W trybie lokalnym aplikacja łączy się wyłącznie z Ollama wewnątrz sieci backend; port 11434 nie jest publikowany na hoście, a `OLLAMA_NO_CLOUD=1` wyłącza funkcje chmurowe. Sieć `ollama-egress` jest potrzebna do pobrania wag Qwen i może być odłączona po pobraniu.

OCR odbywa się lokalnie w obu trybach. Tryb lokalny nie wykonuje żądań do zewnętrznych modeli; tryb Eden wysyła tekst ekstrakcji do endpointu Qwen3.5-9B w Europie.

`GET /api/forms/dictionaries` udostępnia katalog referencyjny. `POST /api/forms/digitize/batch` przetwarza maksymalnie 10 plików i zwraca osobny rezultat każdego z nich. Całe żądanie zbiorcze mieści się w limicie `MAX_UPLOAD_MB` gatewaya i aplikacji.

Przykładowe konfiguracje referencyjne zawierają nazwy `Kraje`, `TERYT_DOWODOWY` i `TERYT_4G_DOWODOWY`, ale nie rekordy tych zbiorów. Katalog oznacza zatem `entriesAvailable: false`. Nowe propozycje zawierają nazwę, pole docelowe, strukturę, uzasadnienie oraz pochodzenie `generated-proposal`; wymagają zatwierdzenia i dostarczenia danych.

Warunki widoczności są zapisane w natywnym `field.states` (`SHOW`/`HIDE`) z zapytaniem COI odczytującym `__userAnswer`. Walidator sprawdza typ stanu, niepustość warunku i odwołania do istniejących pól. Dołączony schemat `flow.links` definiuje tylko `id`, `source` i `target`, dlatego nie kodujemy w nim własnych warunków przejścia. Walidatory to kody tekstowe; schemat nie definiuje parametrów ogólnych ograniczeń długości.

## Konfiguracja i uruchomienie

Konfiguracja limitu uploadu to `MAX_UPLOAD_MB`, współdzielona przez nginx, Spring multipart i walidator. `AI_PROVIDER` wybiera Eden albo Ollama. Lokalny compose jest w `Docker/docker-compose.local.yml`. Instrukcje uruchomienia i API znajdują się w głównym [README](../README.md).

## Zależności i licencje

Qwen3.5-9B jest modelem open-weight na Apache-2.0 ([karta modelu](https://huggingface.co/Qwen/Qwen3.5-9B)); dostępny jest w Eden AI jako `ionos/Qwen/Qwen3.5-9B` i lokalnie jako `qwen3.5:9b` w Ollama. Lokalna wersja to kwantyzacja Q4 z tego samego checkpointu; precyzja i wyniki mogą się różnić. Ollama jest na MIT ([źródło](https://github.com/ollama/ollama/blob/main/LICENSE)), Tesseract na Apache-2.0, a Apache Tika na Apache-2.0. Tesseract oraz dane językowe są instalowane w obrazie. Pełny audyt transytywnych zależności Tika i obrazów bazowych pozostaje wymagany przed dystrybucją. W trybie Eden tekst trafia do dostawcy API; w trybie lokalnym jest przetwarzany lokalnie.

Lokalny model Q4 ma ok. 6,6 GB wag. Zalecane jest 16 GB RAM; GPU z co najmniej 8 GB VRAM poprawia czas odpowiedzi. Obraz Ollama jest wersjonowany (`0.40.17`), wagi modelu zajmują dodatkowe miejsce w woluminie `ollama-models`.
