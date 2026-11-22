# Plan budowy i podział zadań (5 osób: 2 AI, 2 DEV, 1 SEC)

## 1. Stan wyjściowy (zweryfikowany z repo i materiałów)
- Szkielet Spring Boot 4.2 / Java 25 / Gradle; zależności: web, validation, Tika. **Brak** JWT, JSON-aty, POI/PDFBox, Dockera; README to sam nagłówek.
- `Materiały/Materiały_dc/`: 9 zestawów formularzy (6–8 plików na formularz: doc, docx, pdf, ods, xlsx, jpg, jpeg, png; formularz 7 ma `.jpe`/`.JPG`/`.PNG`, bez xlsx w listingu); **2 referencyjne JSON-y** (nr 1, nr 5); „do cyfryzacji” (oceniane): 2, 3, 4, 6, 7, 8, 9. Wnioski z analizy wzorców – `PROJEKT.md` §3.
- Skala: formularz 9 to 3 wnioski w 1 (16 stron PDF, obraz 1654×37424 px); formularz 3 – jedna strona. W finale **nowe, nieznane pliki** (pkt 6 zadania).
- **Brak złotych wzorców dla 2–9** – jury ocenia je bez wzorca, więc własny zestaw ewaluacyjny jest naszą jedyną miarą jakości.

## 2. Ustalenia i decyzje (potwierdzić na starcie)

### 2.1 Klucz API / tokeny organizatorów
Zadanie zabrania, by *rozwiązanie* używało „modeli LLM dostępnych wyłącznie przez API” i zewnętrznych API/SaaS/MCP; takie modele wolno stosować „do pisania/testowania/projektowania kodu”. Tokeny dla 20 zespołów są wydane „do pracy nad rozwiązaniem”. Dlatego klucz służy **tylko w fazie rozwoju**: pisanie kodu, prototypy promptów/reguł (potem implementowane lokalnie), „srebrne wzorce” do ewaluacji, opis zdjęć testowych. Zasady: klucz nie trafia do repo/obrazu/ścieżki runtime, CI/Docker bez sieci, **wynikowe JSON-y do oddania powstają wyłącznie z uruchomienia naszej usługi** (nie z modelu API), srebrne wzorce oznaczone jako pomocnicze. Interpretację potwierdzić z mentorem (§6).

### 2.2 Model w runtime
Opcjonalny lokalny model open source (osobny kontener Ollama/llama.cpp, bez sieci), z licencją dopuszczalną (kandydaci do sprawdzenia: Qwen (Apache-2.0 dla części rozmiarów), Bielik (Apache-2.0, polski), Phi (MIT); unikać licencji z klauzulami użycia/„community”). Realia: 7B w 4-bit to ok. 5 GB RAM i kilka tokenów/s na CPU – **pełny FormSpec (tysiące tokenów) na CPU zajmie minuty, nie <60 s**. Dlatego: (a) LLM tylko do wąskich zadań z krótkim wyjściem (typ pola, zależność, tytuł sekcji, w partiach), (b) tryb regułowy domyślnie kompletny, (c) twardy timeout LLM z fallbackiem do reguł, (d) sprawdzić RAM/GPU laptopów zespołu i maszyny pokazu; wyłączyć VLM (obrazy → OCR). Rozważyć odpowiedź synchroniczną z limitem czasu; asynchroniczne `202 + polling` tylko jeśli konieczne dla batch.

### 2.3 Język
Java 25 (zgodne z „Java 21+”). Eksperymenty AI mogą być w Pythonie, ale ścieżka produkcyjna jest w Javie; kontener LLM to osobna usługa HTTP.

### 2.4 Kontrakty (zamrażamy w godzinie 0–2)
1. `DocumentModel` (wyjście ekstrakcji) – klasy Java + przykładowe JSON-y.
2. `FormSpec` (wyjście „rozumienia”, wejście kompilatora) – schemat z enumami typów pól, walidatorów, źródeł danych inicjalnych (`MLPZ`/`PESEL`/`PESEL_ADDRESS` + atrybut), słowników; metadane strony 1 (adresat, kto może składać, wymagany PESEL/NIP/REGON/KRS, opłata skarbowa); pewność per pole.
3. `FormDefinition` (Jackson) – docelowy JSON COI, model zgodny ze **wzorcami**, nie ze schematem (schemat luźny, patrz `PROJEKT.md` §3).
4. Interfejsy: `Extractor`, `FormSpecProvider` (reguły / LLM), `Compiler`, `RuleValidator`.
5. **Ręcznie napisane `FormSpec` dla formularzy 1 i 5 (odtworzone ze wzorców)** – fixture’y pozwalające DEV-2 i SEC zacząć od godziny 2 bez czekania na ekstrakcję.

## 3. Fazy

### Faza 0 – Fundament (wszyscy, ~2 h)
Przejście przez JSON 1 i 5 + reguły techniczne i standardy; decyzje 2.1–2.4; gałęzie, konwencje, CI (build + testy + skan sekretów). Wynik: kontrakty, pakiety, fixture’y FormSpec 1 i 5, wysłane pytania do mentora (§6).

### Faza 1 – PODSTAWA (minimum obowiązkowe, pkt 3.2)
Ścieżka krytyczna: kontrakty → kompilator + walidator (na fixture’ach) → ekstrakcja → FormSpec z reguł → E2E. Etapy 1a–1d idą równolegle od godziny 2.

| Etap | Wynik | Odpowiedzialny |
|---|---|---|
| 1a | `POST /api/v1/forms/convert`: walidacja uploadu (Tika MIME, nazwa, rozmiar), JWT, konfiguracja z env, stub JSON | DEV-1 |
| 1b | Docker/compose (oficjalne obrazy, wersje, non-root, tmpfs, Tesseract+pol w obrazie), szkielet README | DEV-1 |
| 1c | Kompilator: szablon strony 1 (parametryzowany), strony treściowe, „Kontakt i zgody”, FINISH, `flow`, siatka, UUID/techName, `states`; **test golden: compile(FormSpec 1/5) ≈ wzorzec** | DEV-2 |
| 1d | `RuleValidator` (13 reguł + składnia i odwołania `states`) i testy: wzorce 1 i 5 przechodzą, celowo zepsute JSON-y są odrzucane | SEC (+ DEV-2 przegląd) |
| 1e | Ekstraktory DOCX/XLSX/ODS/DOC/PDF → `DocumentModel` (checkboxy, linie, tabele, numeracja) | AI-1 |
| 1f | Obrazy i PDF skanowane: cięcie wysokich obrazów, OCR (pol), układ → `DocumentModel` | AI-1 (po 1e) |
| 1g | `FormSpecProvider` regułowy: pola → typy, grupowanie w sekcje, mapowanie pól wnioskodawcy na sekcje predefiniowane, wykrywanie metadanych strony 1 | AI-2 |
| 1h | Zestaw ewaluacyjny: wzorce 1 i 5 + **ręcznie sporządzone wzorce małych formularzy 3 i 6** (+ srebrne z API jako pomoc) + skrypt metryk | AI-2 |
| 1i | Threat model i limity (bomby dekompresji, makra, XXE, złośliwe PDF, ReDoS), sandbox/timeout parserów, audyt licencji (zależności + model), skan zależności/obrazu | SEC |
| 1j | **Integracja E2E** na formularzach 1–9 × formaty | wszyscy |

**Definition of Done fazy 1 (mierzalne):** (1) walidator: 0 błędów reguł dla wszystkich plików 2–9 we wszystkich formatach; (2) golden: strona 1 dla formularzy 1 i 5 odtwarza wzorzec strukturalnie (sekcje, typy, `states`, dane inicjalne), pozostałe strony raportują precision/recall pól wobec ręcznych wzorców 3 i 6 – wynik informacyjny, bez stałego progu; (3) ten sam formularz w różnych formatach daje zbliżony wynik (zgodność nazw pól i liczby sekcji mierzona skryptem); (4) działa offline; (5) Docker zgodny z pkt 5 zadania i README kompletne (build, run, config, REST). Wzorce 1 i 5 służą do rozwoju, więc **nie są niezależnym testem** – walidacja uogólnienia na 2–9 i na świeżych plikach.

### Faza 2 – DODATKI (po DoD fazy 1; część ma bardzo niski koszt – zacząć wcześniej)
Kolejność wg punktacji i kosztu: (I) słowniki i dane inicjalne (w dużej mierze część kompilatora) → walidatory pól PESEL/NIP/REGON/ZIP/EMAIL/… (tanie, też w kompilatorze) → (III) zależności i `states` JSONata → (IV) frontend uploadu i podglądu JSON (statyczna strona, ~2–3 h, DEV-1 od razu po 1a/1b) → (II) nowe słowniki (propozycja nazwa+struktura+zastosowanie w raporcie) → (V) wizualizator (HTML/JS renderujący siatkę i `states`; jsonata-js w przeglądarce do symulacji `__userAnswer`) → batch, makiety, raport pewności.

### Faza 3 – Wykończenie (ostatnie ~4–5 h, zamrożenie funkcji)
Lista dostarczanych elementów (pkt 4 zadania) z właścicielami:
- **JSON-y wynikowe** dla formularzy 2,3,4,6,7,8,9 (min. po jednym na formularz, najlepiej z kilku formatów) w `wyniki/`, wygenerowane usługą – AI-2 + DEV-1;
- **Dokumentacja architektury** `docs/ARCHITEKTURA.md` (obowiązkowa, 3.2.II) – DEV-2 + AI-1;
- **README.md** (build obrazu, uruchomienie, konfiguracja, opis REST, wygenerowanie tokenu JWT) – DEV-1;
- **Slajdy PDF ≤10** – AI-2 (treść), SEC (slajd bezpieczeństwa/kosztów);
- **Film ≤3 min** w otwartym repozytorium, link w zgłoszeniu – DEV-1 nagrywa, wszyscy skrypt;
- **Opis i tytuł projektu** (z `PROJEKT.md`), zrzuty ekranu, repo publiczne i bez sekretów, bez licencji copyleft – SEC sprawdza;
- **Próba obrony**: czysty klon → `docker compose up` bez sieci → 3 świeże pliki (inne niż 1–9, najlepiej w różnych formatach) – wszyscy.
Uwaga formalna: zwycięzcy przenoszą autorskie prawa majątkowe na COI (regulamin §4) – nie wciągać kodu o niezgodnej licencji.

## 4. Zadania per osoba

### AI-1 – Ekstrakcja (wejście)
Ekstraktory strukturalne i `DocumentModel`; checkboxy, linie do wypełnienia, tabele, nagłówki, numeracja, pouczenia; OCR + cięcie wysokich obrazów; porównywalność tego samego formularza między formatami. Faza 2: wykrywanie zależności warunkowych z tekstu („jeśli… wypełnij…”) przy współpracy z AI-2.

### AI-2 – Rozumienie i wiedza projektowa
Provider regułowy i (opcjonalnie) LLM z krótkimi wyjściami; reguły: typ pola wg danych, walidatory, słowniki (Kraje, TERYT…), dane inicjalne (MLPZ/PESEL), sekcje predefiniowane, prosty język wg standardów, usuwanie elementów „papierowych”; wybór/benchmark modelu (licencja, RAM, czas); zestaw ewaluacyjny i metryki. Faza 2: nowe słowniki, raport pewności.

### DEV-1 – Backend, infrastruktura, frontend
Kontroler, upload, JWT, konfiguracja, błędy, orkiestracja potoku, timeouty; Docker/compose, README, CI; **frontend (IV) wcześnie**; wizualizator (V) po stabilizacji kompilatora; batch.

### DEV-2 – Rdzeń domenowy
`FormDefinition`, kompilator, szablon strony 1, `states`/JSONata, siatka, UUID/techName, słowniki i dane inicjalne w JSON; pętla naprawcza wraz z walidatorem; wsparcie AI-2 przy kontrakcie `FormSpec`. To najgrubsza ścieżka – nie obciążać dodatkowo frontendem.

### SEC – Bezpieczeństwo, zgodność, walidator
`RuleValidator` (czysta funkcja, dobrze testowalna – naturalny kontrakt jakości dla całego zespołu); JWT (algorytm, klucz, TTL), limity i izolacja parserów, sanityzacja HTML w `description`, odporność na prompt injection z treści formularza, brak PII w logach; hardening kontenera (LLM bez sieci), audyt licencji i skan zależności/obrazu/sekretów; wkład w slajdy i ostatnie testy. Jeśli obciążenie rośnie, walidator wraca do DEV-2, a SEC przejmuje CI i testy integracyjne.

## 5. Ryzyka
| Ryzyko | Mitygacja |
|---|---|
| Lokalny model za wolny/słaby, brak RAM/GPU na maszynie pokazu | Tryb regułowy kompletny, krótkie wyjścia LLM, timeout i fallback, test na docelowym sprzęcie od wczesnej fazy |
| Overfitting do 9 przykładów, brak wzorców 2–9 | Ręczne wzorce 3 i 6, test na świeżych plikach, nie stroić pod wzorce 1 i 5 |
| Skany i obrazy (OCR słabej jakości, obraz 1654×37424 px, 3 wnioski w 1 – formularz 9) | Cięcie na pasy, Tesseract pol; formularz 9 jako jeden formularz z wyborem rodzaju wniosku i sekcjami `SHOW` |
| Wzorce niespójne ze standardami (puste `validation`, `readOnly` adresów, TEXT_BLOCK na FINISH, literówka `repetingSectionGrid`, luźny schemat) | Domyślnie wg standardów/specyfikacji, opcje w konfiguracji kompilatora, pytania do mentora |
| Licencje (AGPL/GPL, wagi modeli, tranzytywne zależności Tiki) | Audyt przed użyciem (SEC + AI-2) |
| DOC/ODS/XLSX słabo ekstrahowane | POI/ODF toolkit, konwersja przez PDF jako fallback |
| Wyciek klucza API | Tylko lokalny `.env`, skan sekretów w CI, hook pre-commit |
| Zbyt długi czas odpowiedzi podczas obrony | Limit czasu, cache wyników dla identycznych plików, wstępne „rozgrzanie” modelu |
| Wąskie gardła: DEV-2 (kompilator), AI-1 (formaty) | Fixture’y FormSpec od godz. 2; AI-2 pomaga AI-1 przy formatach; zamrożenie funkcji w Fazie 3 |

## 6. Pytania do mentora COI
1. Czy użycie klucza/tokenów wyłącznie przy tworzeniu kodu i danych ewaluacyjnych oraz lokalny open-source LLM w osobnym kontenerze są zgodne z zapisem „nie może używać modeli LLM dostępnych wyłącznie przez API / brak zewnętrznych API, MCP”?
2. `repeatingSectionGrid` (oba wzorce) czy `repetingSectionGrid` (dokument i schemat) – który klucz przyjmie importer?
3. Czy `validation` ma zawierać `REQUIRED` i walidatory (standardy: tak; wzorce: puste)? Jak wyrażona jest „obligatoryjność” pola?
4. Dokładne `dictionaryName` dla Województwo/Powiat/Gmina/Miejscowość/Numery kierunkowe oraz znaczenie `TERYT_DOWODOWY` vs `TERYT_4G_DOWODOWY` i `multiLevelTree`.
5. Na stronie FINISH: TEXT_BLOCK (wzorce, standardy) czy tylko INFO_SECTION (reguły techniczne)?
6. Jak wyrażać zależności między *stronami* skoro `flow.links` nie niesie warunków (tylko `states` na sekcjach)? Czy dopuszczalne są strony alternatywne?
7. Czy pola adresowe zasilane z MLPZ mają być `readOnly:true` (dokument „Dane inicjalne”) czy `false` (wzorce)? Źródło: MLPZ czy PESEL dla „Twoje dane” (standardy vs wzorzec 5)?
8. Obsługa `.jpe` i formularzy wieloformularzowych (nr 9) oraz oczekiwany czas odpowiedzi API przy obronie.
