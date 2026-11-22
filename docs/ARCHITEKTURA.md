# Architektura rozwiązania – FormForge

Dokumentacja architektury (wymóg 3.2.II zadania COI „Cyfryzacja formularzy”). Dokument rozróżnia to, co **jest zaimplementowane**, od tego, co **zaplanowane**; uzasadnienie decyzji i ustalenia z materiałów referencyjnych są w [`PROJEKT.md`](PROJEKT.md), podział prac w [`PLAN.md`](PLAN.md).

## 1. Cel i zasada przewodnia

Usługa REST przyjmuje jeden blankiet formularza i zwraca JSON konfiguracji e-formularza (`title`, `tabs`, `flow`) zgodny z Generatorem Formularzy COI. **AI rozumie dokument, kod gwarantuje zgodność:** model językowy nie generuje docelowego JSON-a, tylko wspiera wyodrębnienie małej specyfikacji pośredniej (`FormSpec`); deterministyczny kompilator w Javie buduje z niej JSON zgodny z wzorcami COI, a walidator sprawdza reguły techniczne. Bez modelu nadal powstaje poprawny strukturalnie JSON (tryb regułowy).

## 2. Widok wdrożeniowy (Docker)

```
 klient / jury
      │  HTTP :8080 (domyślnie tylko 127.0.0.1)
      ▼
┌────────────────────────── sieć frontend ──────────────────────────┐
│  gateway (nginx-unprivileged, user 101)                           │
│   • :8080  reverse proxy wejścia: limity rozmiaru i żądań,        │
│            tylko PUT /upload, /api/*, GET /health                 │
│   • :8081  egress gateway do Eden AI (allowlista, klucz API)      │
└───────────┬────────────────────────────────────▲──────────────────┘
            │ sieć backend (internal, 172.29.250.0/24)           │
            ▼                                                    │ POST /v3/*
┌──────────────────────────────┐        gateway ─ HTTPS ─► api.edenai.run
│ app (Spring Boot, user 10001)│───────────────────────────────┘
│  Java 25, brak dostępu do    │  (app woła http://gateway:8081/v3,
│  internetu, brak klucza API  │   nginx dokleja Authorization)
└──────────────────────────────┘
```

Zasady wdrożenia:
- **Dwa kontenery** (`gateway`, `app`) i dwie sieci Dockera. `frontend` ma dostęp zewnętrzny, `backend` jest `internal` (brak wyjścia do internetu).
- **Gateway jest jedynym punktem wejścia i wyjścia.** Wejście: limit rozmiaru (`MAX_UPLOAD_MB` → 413), `limit_req` (~10 żądań/s na IP, burst 20 → 429), `limit_conn`, timeouty, `server_tokens off`, nagłówki bezpieczeństwa, metody ograniczone do `PUT` na `/upload`. Wyjście: wyłącznie `POST /v3/upload`, `/v3/universal-ai`, `/v3/chat/completions` do `api.edenai.run`, z weryfikacją certyfikatu TLS; reszta → 403. Port 8081 przyjmuje połączenia tylko z podsieci `backend`.
- **Sekrety:** `EDENAI_API_KEY` jest tylko w środowisku kontenera `gateway` i wstrzykiwany do nagłówka `Authorization` przez nginx (`envsubst`). Skompromitowana aplikacja nie ma ani klucza, ani wyjścia do internetu poza allowlistą.
- **Hardening:** obrazy oficjalne z przypiętymi tagami i digestami (`eclipse-temurin 25.0.5.1_2` Alpine 3.24, `nginx-unprivileged 1.31.7-alpine3.24`), wersje Alpine zawierają poprawkę CVE-2026-123158 (openssh w obrazach nie występuje), użytkownicy nie-root, `cap_drop: ALL`, `no-new-privileges`, `read_only`, `tmpfs` (`noexec`) na `/tmp`, limity pamięci/procesów/deskryptorów, limity logów, `init: true`, healthcheck gatewaya.
- **Brak trwałego zapisu plików:** system plików kontenera jest tylko do odczytu; plik jest przetwarzany w pamięci (`byte[]`) i w `tmpfs`.
- **Build:** wieloetapowy `Docker/Dockerfile` (JDK → warstwy Spring Boot `jarmode=tools extract --layers` → JRE z Tesseractem i językiem polskim jako zapas dla OCR), kontekst budowania to korzeń repo.

## 3. Widok logiczny aplikacji

Potok docelowy:

```
plik ─► [1] API + walidacja ─► [2] Ekstrakcja ─► [3] Rozumienie ─► [4] Kompilator ─► [5] Walidator ─► JSON
        (zrobione)              DocumentModel     FormSpec           FormDefinition    reguły COI
                                (częściowo)       (planowane)        (szkielet)        (planowane)
```

| Etap | Odpowiedzialność | Klasy / stan |
|---|---|---|
| 1. API i walidacja | Przyjęcie `multipart`, walidacja rozszerzenia, MIME wg treści (Tika), nazwy (≤ 250 znaków, zakazane znaki `; : > < / \ \| ' * % $ ?`), rozmiaru (`app.upload.max-size-bytes`) | `FormDigitizerController` (`POST /api/forms/digitize`), `UploadController` (`PUT /upload`), `FileValidationService` → `ValidatedFile(filename, extension, mimeType, content)` – **zrobione** |
| 2. Ekstrakcja | Dokumenty tekstowe (DOC/DOCX/ODS/XLSX/PDF): Apache Tika; obrazy i skany: OCR Eden AI (`/v3/upload` + `/v3/universal-ai`, model Mistral OCR) | `DocumentParser` (Tika `parseToString`) – zrobione, wynik jeszcze nieprzekazywany dalej; `EdenOcrClient` – zrobiony, jeszcze niepodpięty (`UploadService` zawiera TODO dla obrazów) |
| 3. Rozumienie | Wyodrębnienie `FormSpec` (sekcje, pola, typy, opcje, zależności, walidacje): heurystyki + model Eden (`app.edenai.default-model`) o krótkich wyjściach, timeout i fallback do reguł | **planowane** |
| 4. Kompilator | `FormSpec` → `FormDefinition`: strona 1 „Dane” z szablonu COI, strony treściowe, „Kontakt i zgody”, `FINISH`, siatka `x/y/rows/cols`, UUID, `techName`, słowniki, dane inicjalne, `states`, `flow` | `FormDefinition` (model Jackson) i `FTJGenerator` – **szkielet** zwracający stały formularz |
| 5. Walidator | 13 reguł technicznych COI (m.in. ≤ 255 znaków tytułu, ≥ 2 strony, `FINISH` tylko na końcu, pola w sekcjach, brak zagnieżdżania sekcji, obligatoryjność, `flow`, dozwolone typy i walidatory) + składnia i odwołania `states` | **planowane** |

Model danych wyjściowych (`JSONRes/FormDefinition`): rekord z `title`, `tabs[]` (`id`, `title`, `type` `MAIN`/`FINISH`, `questions[]` z siatką `x,y,rows,cols` i `field`) oraz `flow.links[]` (`id`, `source`, `target`). Typ `FormField` obejmuje m.in. `type`, `techName`, `title`, `description`, `options`, `validation`, `dictionaryName`, `readOnly`, pola zagnieżdżone sekcji, `defaultValue`/`defaultValueType`, `consentSectionConfig`, `states`, `questionOrder`. Pola `null` są pomijane (`@JsonInclude(NON_NULL)`).

Decyzje formatowe wynikające z analizy wzorców (formularze 1 i 5): `repeatingSectionGrid` (wariant z rzeczywistych JSON-ów, dokument zawiera literówkę), każde pole w `SECTION`, `flow` jako łańcuch liniowy bez warunków (zależności przez `states` SHOW/JSONata), walidacje emitowane wg standardów (`REQUIRED`, `PESEL`, `NIP`, `ZIP_CODE`, `EMAIL`…) mimo pustych `validation` we wzorcach. Szczegóły: `PROJEKT.md` §3.

## 4. Wykorzystanie AI i zgodność z wymaganiami

- Eden AI jest używane jako **model open source dostępny przez bramę** (OCR i gemma jako model czatu); zgodnie z ustaleniem z mentorem COI traktujemy go jako nasz „lokalny model”. Klucz API istnieje wyłącznie w gatewayu.
- AI nie tworzy finalnego JSON-a – wynik zawsze przechodzi przez kompilator i walidator (odporność na halucynacje i niedeterminizm).
- **Tryb degradacji (planowany):** timeout lub błąd Eden → rozpoznawanie regułowe (wielokropki/linie do wypełnienia, pola wyboru, tabele), tak aby usługa działała też bez dostępu do modelu.
- Przetwarzanie dokumentów użytkownika: plik trafia do Eden AI wyłącznie przez gateway, w czasie realizacji żądania; usługa niczego nie zapisuje trwale.

## 5. Bezpieczeństwo (podsumowanie)

| Zagrożenie | Środek |
|---|---|
| Złośliwy lub niezgodny plik | Walidacja rozszerzenia, MIME z treści (Tika), nazwy i rozmiaru; limit w nginx i w aplikacji |
| Nadużycia (flood, duże ciała żądań, wolne połączenia) | `limit_req`/`limit_conn`, `client_max_body_size`, timeouty, limity logów |
| Wyciek klucza Eden / SSRF z aplikacji | Klucz tylko w gatewayu, `app` w sieci `internal`, allowlista endpointów, port 8081 tylko z podsieci `backend` |
| Eskalacja uprawnień w kontenerze | Non-root, `cap_drop: ALL`, `no-new-privileges`, `read_only`, `noexec` |
| Wyciek danych w logach | Brak logowania treści plików; logowana jest tylko nazwa pliku w `UploadController` (do zmiany, jeśli nazwy zawierają dane osobowe) |
| Podatności bazy | Alpine 3.24 z poprawkami (m.in. CVE-2026-123158, dotyczy OpenSSH – nieobecnego w obrazach), przypięte digesty; skan obrazu planowany |
| Nieuprawniony dostęp | JWT (HS256, Spring Security + jjwt): wszystkie ścieżki poza `/health` wymagają tokenu; weryfikacja podpisu, issuera i `exp`, odrzucanie innych algorytmów; sekret wyłącznie z env (patrz §6.1) |

## 6. Konfiguracja

Parametry uruchomieniowe (zmienne środowiskowe, wzór w `.env.example`): maksymalny rozmiar pliku (`MAX_UPLOAD_MB`), parametry JWT (`JWT_SECRET`, `JWT_ISSUER`, `JWT_TTL_SECONDS`), parametry integracji (`EDENAI_API_KEY` w gatewayu, `EDENAI_BASE_URL` w aplikacji), port i interfejs publikacji (`APP_PORT`, `APP_BIND`). Pełna tabela w [`README.md`](../README.md).

### 6.1 Uwierzytelnianie JWT

Pakiet `com.hacknation.cyfryzacja.security`:

| Klasa | Rola |
|---|---|
| `JwtProperties` | `@ConfigurationProperties("app.jwt")`: `secret` (min. 32 znaki, `@Validated` – start aplikacji kończy się błędem przy braku lub zbyt krótkim sekrecie), `issuer`, `ttlSeconds` |
| `JwtTokenVerifier` | parser jjwt: weryfikacja podpisu kluczem HMAC z `secret`, `requireIssuer`, kontrola `exp`; dodatkowo wymuszony algorytm `HS256` (odrzucane `alg: none`, HS384/HS512, tokeny nieprawidłowe) |
| `JwtAuthFilter` | czyta `Authorization: Bearer <token>`; poprawny token ustawia uwierzytelnienie (principal = `sub`), w przeciwnym razie żądanie zostaje anonimowe. Filtr nie jest beanem, tylko jest tworzony w `SecurityConfig`, więc działa wyłącznie w łańcuchu Spring Security |
| `SecurityConfig` | `SecurityFilterChain`: sesja bezstanowa, CSRF/form login/HTTP Basic/logout wyłączone, `/error` publiczny, reszta `authenticated()`; brak uwierzytelnienia → `401` JSON `{"error":"unauthorized"}` z `WWW-Authenticate: Bearer` |

Przepływ: klient → nginx (`/upload`, `/api/**`; nagłówek `Authorization` jest przekazywany bez zmian) → `JwtAuthFilter` → autoryzacja → kontroler. `/health` odpowiada sam nginx, więc aplikacja nie ma żadnej ścieżki publicznej poza `/error`.

Aplikacja tylko **weryfikuje** tokeny, nie ma endpointu logowania ani wydawania. Token wystawiają `scripts/generate-jwt.sh|ps1` (HS256, claims `iss`, `sub`, `iat`, `exp`) tym samym sekretem, który jest w `.env`. Sekret trafia do kontenera `app` wyłącznie jako zmienna środowiskowa `JWT_SECRET` (compose wymaga jej ustawienia). Wybór modelu „tylko weryfikacja” ogranicza powierzchnię ataku i kod, a jury otrzymuje token testowy ze skryptu opisanego w README. Ograniczenie: HS256 oznacza wspólny sekret dla wystawcy i weryfikatora; przy większym wdrożeniu zasadne jest przejście na klucze asymetryczne (RS256/ES256) i zewnętrznego wystawcę.

Testy: `JwtTokenVerifierTest` (poprawny token, zły podpis, wygasły, zły issuer, `alg: none`, inny algorytm HMAC, śmieci) i `JwtAuthFilterTest` (uwierzytelnienie poprawnym tokenem, brak nagłówka, niepoprawny token/schemat) w `src/test/java/.../security`.

## 7. Stos technologiczny

Java 25, Spring Boot 4.2 (Gradle), Spring Security + jjwt (JWT), Apache Tika 3.1 (detekcja MIME, ekstrakcja tekstu), Eden AI (OCR, model językowy), nginx (API Gateway), Docker/Compose, Tesseract (zapasowy OCR w obrazie). Wyłącznie open source bez licencji copyleft – audyt zależności (w tym `tika-parsers-standard-package`) do wykonania.

## 8. Testowanie

`scripts/upload-tests.sh|ps1` – smoke test uploadu na plikach z `Materiały/Materiały_dc`. Planowane: testy goldenowe kompilatora na wzorcach JSON 1 i 5, testy walidatora reguł COI (wzorce przechodzą, celowo zepsute JSON-y są odrzucane), zestaw ewaluacyjny dla formularzy 2–9.

## 9. Plan rozwoju (skrót)

1. Poprawa modelu i kompilator + walidator reguł COI z testami goldenowymi.
2. Podpięcie ekstrakcji (Tika, Eden OCR) i generowania `FormSpec` do endpointu `POST /api/forms/digitize`.
3. Mapowanie błędów walidacji na 4xx, tryb degradacji.
4. Słowniki i dane inicjalne, `states`, następnie frontend i wizualizator (rozszerzenia 3.3).
5. Wynikowe JSON-y dla formularzy 2, 3, 4, 6, 7, 8, 9, audyt licencji, slajdy i film.
