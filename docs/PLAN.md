# Plan i status

## Zaimplementowane w branchu `test4`

- [x] Walidacja nazwy, rozszerzenia, MIME i rozmiaru pojedynczego uploadu.
- [x] Jedna zmienna `MAX_UPLOAD_MB` dla limitu gatewaya, Spring multipart i walidatora.
- [x] Ekstrakcja Tika dla wspieranych formatów dokumentów.
- [x] Tesseract OCR dla obrazów oraz OCR i ekstrakcja tekstu dla PDF.
- [x] Jeden model Qwen3.5-9B dostępny przez API Eden AI i lokalnie przez Ollama; wspólny prompt i walidacja JSON.
- [x] Lokalny tryb nie wywołuje API Eden; OCR działa lokalnie w obu trybach.
- [x] Generowanie JSON na podstawie odczytanej treści.
- [x] Walidacja JSON na podstawie schematu importu COI i dodatkowych reguł semantycznych.
- [x] Błędy kompletności są zwracane, a brakujące pola nie są uzupełniane atrapami.
- [x] README oraz dokumentacja architektury i projektu.
- [x] Katalog nazw słowników z formularzy referencyjnych, propozycje nieznanych słowników i walidacja warunków widoczności.
- [x] API katalogu słowników i zbiorcze przetwarzanie do 10 plików.

## Pozostałe przed zgłoszeniem

- [ ] Wykonać pełny audyt licencji wszystkich zależności i obrazów.
- [ ] Porównać jakość i wyniki API oraz lokalnego Qwen na formularzach referencyjnych; lokalna kwantyzacja Q4 może dawać inne rezultaty.
- [ ] Przetworzyć wszystkie formularze z zestawu konkursowego; porównać pola, opcje i strony z przykładami COI.
- [ ] Zweryfikować import JSON w systemie docelowym lub oficjalnym walidatorze.
- [ ] Pozyskać brakujące rekordy danych słowników; dostarczone materiały podają tylko nazwy referencyjne.
- [ ] Ustalić z COI sposób zapisu ogólnych limitów min/max długości; schemat wejściowy nie zawiera parametrów walidatorów.
- [ ] Przygotować wymagane materiały zgłoszeniowe: prezentację do 10 slajdów, przykładowe pliki wynikowe i film demonstracyjny do 3 minut.
