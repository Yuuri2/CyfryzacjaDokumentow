# Projekt: e-skw – automatyczna cyfryzacja formularzy urzędowych

> Wersja robocza opisu (po dopracowaniu trafia do zgłoszenia konkursowego: tytuł + opis, prezentacja PDF ≤10 slajdów, wynikowe JSON-y, film ≤3 min w otwartym repozytorium (link), repozytorium kodu – treść zadania, pkt 4).

## 1. Problem
Urzędy publikują wnioski jako PDF, DOC(X), ODS, XLSX i skany (JPG/PNG), projektowane do wypełniania ręcznego. Przeniesienie takiego blankietu do e-usługi to wieloetapowa, ręczna analiza: poznanie procesu, wyłuskanie pól i zależności, uproszczenie, zaprojektowanie stron, walidacji i słowników. Jest to wolne, drogie i niepowtarzalne (każdy urząd ma inny wzór tego samego wniosku).

## 2. Rozwiązanie
Usługa REST przyjmująca pojedynczy blankiet (PDF, DOC, DOCX, ODS, XLSX, JPG, JPEG, PNG) i zwracająca **JSON konfiguracji e-formularza** (`title`, `tabs`, `flow`) w strukturze oczekiwanej przez Generator Formularzy COI. Adresat: urzędnik cyfryzujący formularze – dostaje **szkic do przeglądu** wraz z listą pól o niskiej pewności i poprawia resztę (nie obiecujemy procentu „gotowości”; mierzymy go na własnym zestawie, patrz PLAN §3).

**Kluczowa idea: AI rozumie dokument, kod gwarantuje zgodność.**
Model językowy nie generuje docelowego JSON-a (halucynacje, niedeterminizm). Zamiast tego (lub obok heurystyk) wyodrębnia *małą specyfikację pośrednią* `FormSpec` (sekcje, pola, typy, opcje, wskazówki zależności i walidacji). Deterministyczny kompilator w Javie zamienia ją w docelowy JSON, narzucając standardy projektowe COI, a walidator reguł sprawdza wynik. Bez LLM nadal powstaje poprawny strukturalnie JSON (tryb regułowy) – LLM podnosi jakość, ale nie jest warunkiem poprawności.

## 3. Ustalenia z materiałów referencyjnych (formularze 1 i 5) – wpływ na projekt
- **Strona 1 („Dane”) jest w praktyce szablonem**: w obu wzorcach 12 sekcji w tej samej kolejności (urząd/organ → Podmiot wniosku → Twoje dane → Adres zamieszkania → Dane podmiotu → Adres siedziby → Potwierdzenie reprezentanta → Dane innej osoby → Adres innej osoby → Pełnomocnictwo → Opłata skarbowa ×2), większość z `states` SHOW zależnymi od radia „w czyim imieniu”. Różnią się parametrami (nazwa urzędu/organu, pola podmiotu: NIP/REGON/KRS vs tylko NIP, źródło MLPZ vs PESEL, PESEL w „Twoje dane”). Standardy: sekcję „Podmiot wniosku” pomija się, gdy wniosek dotyczy tylko osoby składającej. Kompilator generuje stronę 1 z parametrów FormSpec; kolejne strony zależą od formularza.
- Dalej: strony treściowe → przedostatnia „Kontakt/zgody” (Dane do kontaktu z MLPZ.phoneNumber/email + sekcja zgód `CONSENT_SECTION`) → `FINISH` („Podgląd uzupełnionego wniosku”, blok tekstu). `flow.links` to **łańcuch liniowy** stron; link ma tylko `id/source/target` – **schemat nie przewiduje warunków przepływu**, więc zależności realizujemy przez `states` SHOW na sekcjach/polach (do potwierdzenia z mentorem, PLAN §6).
- **`validation` jest puste we wszystkich 142 polach obu wzorców** (także dla NIP, PESEL, REGON), choć dokument „Standardy” wprost nakazuje „walidacja: pole wymagane”, a „Reguły techniczne” wymagają „atrybutu obligatoryjności” i dopuszczają walidatory REQUIRED/EMAIL/PESEL/NIP/REGON/ZIP_CODE/EPUAP/ADE/SERIAL_NUMBER_ID_CARD. Wzorce nie są więc wiarygodnym wyznacznikiem walidacji – **kompilator emituje walidacje wg standardów** (`REQUIRED`, `PESEL`, `ZIP_CODE`, `EMAIL`…), a rozbieżność zgłaszamy mentorowi.
- Struktura: `tabs[].questions[]` (`x,y,rows,cols,field`); wszystkie pola leżą w `SECTION` (dzieci w `field.repeatingSectionGrid`, jedna kolumna: `x=0`, `cols=3`, `rows=1` dla pola; `y` sekcji = `y+rows` poprzedniej; `questionOrder` = `y`). Użyte typy: SECTION, TEXT_FIELD, SELECT, RADIO, FILE_UPLOADER, TEXT_BLOCK, CONSENT_SECTION, NUMBER, TEXTAREA (REPEATING_SECTION, CHECKBOX, DATEPICKER, INFO_SECTION nie występują – bierzemy ze specyfikacji komponentów).
- **Nazwa klucza**: dokument i schemat mówią `repetingSectionGrid` (literówka), oba rzeczywiste JSON-y używają `repeatingSectionGrid` – emitujemy wariant z wzorców (do potwierdzenia). Dołączony schemat JSON jest luźny (same typy, bez `required`/enumów) i nie jest spełniany przez wzorce (`defaultValue` bywa tablicą `["Polska"]`, schemat: string) – **walidacja „zgodności ze schematem” to za mało; wiążące są reguły techniczne i wzorce**.
- Szczegóły formatu: `description` to HTML opakowany w `<html><head></head><body>…</body></html>`; `options[].id` = nazwa opcji (nie UUID), `techName` = snake_case bez polskich znaków; `id` pól/tabów to UUID, `flow.links[].id` w jednym wzorcu krótki (`a16ec`) – dowolny unikalny string; wartości domyślne danych z rejestrów: `defaultValue:"MLPZ.name"`, `defaultValueType:"EXTERNAL_SYSTEM"`, `readOnly:true` (wg dokumentu „Dane inicjalne”; we wzorcach pola adresowe z MLPZ mają `readOnly:false` – rozbieżność).
- Słowniki użyte we wzorcach: `Kraje`, `TERYT_DOWODOWY`, `TERYT_4G_DOWODOWY` (z `multiLevelTree:true`); dokument „Słowniki” opisuje Województwo/Powiat/Gmina/Miejscowość/Kraje/Numery kierunkowe i **nie podaje dokładnych `dictionaryName`** (poza `Kraje`).
- Stany: wyłącznie `SHOW` (+ `READ_ONLY` wg specyfikacji); zapytania JSONata odwołują się do **tytułu strony i techName** (`tabs['Dane' in title].questions['…' in field.techName].field.repeatingSectionGrid['…' in field.techName].field.__userAnswer`), więc tytuły stron i techName muszą być unikalne i spójne z zapytaniami. `__userAnswer` istnieje tylko w runtime generatora – **nie ewaluujemy** `states` w backendzie; walidator sprawdza składnię i istnienie odwołań.

## 4. Architektura (przepływ)

```
plik ──► [1] Brama API ─► [2] Ekstrakcja ─► [3] Rozumienie ─► [4] Kompilator ─► [5] Walidator ─► JSON
         JWT, MIME,       DocumentModel     FormSpec           FormDefinition    13 reguł COI,
         nazwa, rozmiar   (tekst, tabele,   (reguły; opcjon.  (szablon COI,     składnia/odwołania
                          checkboxy,        lokalny LLM)       UUID, siatka,     states, raport
                          bitmapy stron)                       słowniki, dane    pewności
                                                               inicjalne, states)
```

1. **Brama API** – MIME (Tika, treść vs rozszerzenie) dla 8 rozszerzeń: pdf, doc, docx, ods, xlsx, jpg, jpeg, png (materiały zawierają też `.jpe` – zachowanie do potwierdzenia, domyślnie odrzucamy); nazwa ≤250 znaków i zakaz `; : > < / \ | ' * % $`; limit rozmiaru z konfiguracji; JWT; plik wyłącznie w pamięci/tmpfs.
2. **Ekstrakcja** – DOCX/DOC/ODS/XLSX/PDF tekstowy: parsery strukturalne (POI, PDFBox, ODF) → `DocumentModel` (bloki, tabele, znaczniki pól do wypełnienia, checkboxy). Skany/obrazy: OCR Tesseract (język pol). Uwaga na skalę: obrazy bywają bardzo wysokie (formularz 9: **1654×37424 px**, 16 stron PDF; formularz 6: 1654×4678) – wymagane cięcie na pasy/strony przed OCR; „wizualny” LLM/VLM na CPU poza zakresem (patrz §6).
3. **Rozumienie** – heurystyki (wielokropki/linie do wypełnienia, „□”, etykiety w nawiasach, tabele, numeracja) + opcjonalnie **lokalny** model językowy o krótkim wyjściu (klasyfikacja typu pola, wykrycie zależności „jeśli TAK, podaj…”, grupowanie w sekcje). Dodatkowe zadania: pola wnioskodawcy z blankietu → sekcje predefiniowane (bez duplikatów), usuwanie elementów „papierowych” (miejscowość/data, „podpis”, pouczenia/RODO → sekcja zgód z linkiem).
4. **Kompilator** – FormSpec → `FormDefinition`: strona 1 z szablonu COI, strony treściowe, „Kontakt i zgody”, `FINISH`, sekcje predefiniowane, adresy wg jednolitego schematu, `readOnly` + `defaultValue`/`EXTERNAL_SYSTEM`, słowniki, `states`, `flow.links`, siatka `x/y/rows/cols`, UUID.
5. **Walidator** – 13 reguł technicznych COI z dokumentu „Reguły techniczne”: tytuł ≤255; ≥2 strony; tag FINISH tylko na ostatniej, pozostałe MAIN; każda strona ≥1 pole; każde pole w sekcji, sekcja na stronie, brak zagnieżdżania sekcji; na FINISH brak pól wejściowych (wg dokumentu tylko INFO_SECTION, wzorce używają TEXT_BLOCK – patrz PLAN §6); atrybut obligatoryjności; strony połączone w `flow`; `states` dla zależności; słownik tylko w SELECT/CHECKBOX/RADIO; dozwolone typy i walidatory. Wynik niezgodny jest naprawiany automatycznie (pętla naprawcza deterministyczna) lub oznaczany ostrzeżeniem.

## 5. Stos technologiczny
Java 25 · Spring Boot 4.2 (Gradle) · POI/PDFBox/Tika (detekcja MIME; zależności tranzytywne `tika-parsers-standard-package` do audytu licencji, ewentualnie zastąpić wąskim zestawem) · Tesseract (natywny, w obrazie Docker) · Spring Security resource-server/Nimbus (JWT) · JSONata (parser JSON-ata w Javie, licencja do sprawdzenia przez SEC) · lokalny LLM w osobnym kontenerze (opcjonalny) · Docker (oficjalne obrazy, Alpine, wersje przypięte, bez `latest`, użytkownik non-root, tylko wymagane porty, sekrety z env). Wyłącznie open source bez copyleftu (AGPL/GPL). Obecny `build.gradle` ma tylko web, validation i Tikę – JWT, JSON-ata, POI/PDFBox do dodania.

## 6. Zgodność z wymaganiami konkursu
| Wymóg (źródło) | Podejście / ryzyko |
|---|---|
| REST API, jeden plik, 8 rozszerzeń, odpowiedź = JSON konfiguracji (3.2.I, pkt 5) | `POST /api/v1/forms/convert` (multipart), body odpowiedzi = sam JSON |
| MIME / nazwa / rozmiar | Moduł walidacji uploadu, limit z env |
| JWT (pkt 5 „Konfiguracja”) | Weryfikacja tokenu (sekret/klucz, issuer, TTL z env); README opisuje wygenerowanie tokenu testowego dla jury (skrypt) |
| „Rozwiązanie nie może używać modeli LLM dostępnych wyłącznie przez API; brak zewnętrznych API, MCP, SaaS” | Runtime w pełni offline; model LLM tylko lokalny open source (wagi z dopuszczalną licencją – sprawdzić), uruchamiany w osobnym kontenerze **bez dostępu do sieci**. Klucz API/tokeny organizatorów tylko w pracy deweloperskiej („pisanie/testowanie/projektowanie kodu”, w tym dane ewaluacyjne) – **interpretacja do potwierdzenia z mentorem**. Wynikowe JSON-y do oddania generuje wyłącznie nasza usługa |
| Brak trwałego zapisu plików | Pamięć / tmpfs, brak plików w logach |
| Docker, README (build, run, config, REST) | `Docker/`+compose, README (README.md w repo zawiera dziś tylko nagłówek) |
| Dokumentacja architektury (3.2.II – **obowiązkowa**) | `docs/ARCHITEKTURA.md` (z tego dokumentu + diagramy), wymieniona w README |
| Licencje bez copyleftu | Audyt zależności i modelu (SEC), raport w repo |
| Obrona na nowych plikach (pkt 6) | Usługa musi działać na świeżym laptopie bez sieci, tryb degradacji bez LLM, czas odpowiedzi rozsądny |
| Formalne (pkt 4) | Opis+tytuł, slajdy PDF ≤10, wynikowe JSON-y (`wyniki/`), film ≤3 min (link), repo |

## 7. Mapowanie na kryteria oceny (§8 zadania: 30/20/10/20/20)
- **Kompletność 30%** – minimum + rozszerzenia. Uwaga: numeracja w §8 różni się od §3.3 (w §8 „V = Walidacje pól”, w §3.3 V = wizualizator, VI = walidacje/UX). Robimy wszystkie; priorytet: I, III, walidacje pól, frontend (IV), II, wizualizator (V), batch/makiety.
- **Intuicyjność 20%** – UI: upload, podgląd JSON, lista ostrzeżeń/niepewności, wizualizacja formularza.
- **Oryginalność 10%** – „FormSpec + kompilator + walidator reguł COI”, raport pewności per pole, tryb degradacji.
- **Jakość kodu 20%** – testy golden na wzorcach, czytelne moduły, CI, walidator jako kontrakt.
- **Potencjał wdrożeniowy/koszt 20%** – zero kosztów API, on-premise, jeden kontener + opcjonalny kontener LLM, jawne wymagania sprzętowe (modele 7B w kwantyzacji to ok. 5 GB RAM i kilka tokenów/s na CPU – LLM używamy oszczędnie).
