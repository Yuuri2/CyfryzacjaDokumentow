# Opis projektu

## Cel

e-skw ma skrócić przygotowanie cyfrowego formularza urzędowego przez automatyczne odczytanie blankietu i wygenerowanie konfiguracji JSON dla Generatora Formularzy COI.

## Zakres

API przyjmuje pojedyncze pliki PDF, DOC, DOCX, ODS, XLSX, JPG, JPEG i PNG. Aplikacja sprawdza nazwę, rozmiar i typ zawartości, wydobywa tekst z dokumentu, wykonuje lokalne OCR dla obrazów i skanowanych PDF-ów, a następnie buduje strukturę stron, sekcji i pól. Odpowiedź musi przejść walidację schematu importu i reguł technicznych.

Generowanie wykorzystuje Qwen3.5-9B. Domyślnie aplikacja używa endpointu `ovhcloud/Qwen3.5-9B` przez Eden AI; można wybrać inny przez `EDENAI_MODEL` albo uruchomić lokalny tag `qwen3.5:9b` przez Ollama. Instrukcja dla modelu jest wspólna, a aplikacja prosi o wyłączenie trybu rozumowania Qwen3.5 (obsługę należy potwierdzić dla wybranego endpointu); odpowiedź ma limit 8192 tokenów. Lokalny tag używa kwantyzacji Q4, więc nie gwarantuje bitowo identycznych wyników z hostowanym endpointem. OCR w obu trybach jest lokalny i korzysta z Tesseract.

## Użytkownik i rezultat

Użytkownikiem jest urzędnik cyfryzujący formularz e-usługi. Wynikiem jest JSON z tytułem formularza, stronami tematycznymi, pytaniami, polami i kolejnością przejść. Niepoprawny lub niekompletny rezultat jest odrzucany z opisem naruszeń zamiast uzupełniania pustymi polami.

## Ograniczenia bieżącej wersji

- Tryb Eden wysyła tekst ekstrakcji do zewnętrznego endpointu; tryb `local` nie wysyła go poza lokalną sieć.
- Lokalny model Q4 wymaga ok. 6,6 GB miejsca na wagi i ok. 16 GB RAM dla wygodnej pracy; CPU działa wolniej, GPU jest zalecane.
- Wejściowy tekst powyżej 60 000 znaków jest odrzucany, bez cichego obcinania.
- OCR wydrukowanego tekstu może nie odczytać odręcznego pisma ani skomplikowanego układu tabel.
- Model może pominąć lub błędnie zinterpretować pole; walidacja strukturalna nie zastępuje weryfikacji merytorycznej przez urzędnika.
- Słowniki, dynamiczne zależności, wizualizator i przetwarzanie wsadowe nie należą do aktualnego zakresu tej implementacji.

## Zasada obsługi niepewności

System nie powinien przedstawiać niekompletnego wyniku jako gotowego formularza. Odrzuca błędny JSON lub wynik bez wymaganych danych, zwracając ścieżki naruszeń. Przed wdrożeniem należy przeprowadzić ocenę jakości na wszystkich materiałach konkursowych oraz na nowych formularzach.
