package com.hacknation.cyfryzacja.ai;

import java.util.List;
import java.util.Map;

final class FormGenerationPrompt {
    static final int MAX_INPUT_CHARACTERS = 60000;

    private FormGenerationPrompt() {}

    static List<Map<String, String>> messages(String filename, String extractedText, String catalogContext) {
        return messages(filename, extractedText, catalogContext, List.of());
    }

    static List<Map<String, String>> messages(String filename, String extractedText, String catalogContext,
                                               List<String> validationIssues) {
        if (extractedText.length() > MAX_INPUT_CHARACTERS) {
            throw new IllegalArgumentException("Dokument zawiera ponad 60 000 znaków tekstu; nie został obcięty, bo groziłoby to pominięciem pól. Podziel formularz i prześlij jego części osobno.");
        }
        String correction = validationIssues.isEmpty() ? "" : """

                Poprzednia odpowiedź nie przeszła walidacji COI. Wygeneruj od nowa kompletny formularz na podstawie tekstu źródłowego, poprawiając poniższe naruszenia. Nie dopisuj pustych pól ani danych, których nie ma w źródle. Nie zwracaj opisu naprawy.
                Naruszenia walidacji:
                %s
                """.formatted(String.join("\n", validationIssues));
        String prompt = """
                Przekształć treść formularza urzędowego w kompletny JSON konfiguracji importowanej przez Generator Formularzy COI.
                Zwróć wyłącznie JSON o strukturze {"title":"...","tabs":[...],"flow":{"links":[...]}}.
                Możesz dodać opcjonalne pole dictionaryProposals zawierające propozycje słowników
                w formacie [{"name":"...","fieldTechName":"...","structure":"...","rationale":"..."}].
                Każda strona ma id UUID, title, type MAIN albo FINISH i tablicę questions.
                Każde pytanie ma x, y, rows, cols i field. Każde pole ma id UUID, type, techName, title,
                description, options, validation, readOnly, repeatingSectionGrid, states, questionOrder i tooltipMessage.
                Schemat zagnieżdżenia: na stronie MAIN questions zawiera pola typu SECTION lub REPEATING_SECTION,
                a dopiero ich repeatingSectionGrid zawiera pola wejściowe. Nie umieszczaj pól wejściowych bezpośrednio
                w questions strony MAIN. Każda strona MAIN musi mieć co najmniej jedną sekcję i każde pole musi mieć
                repeatingSectionGrid (pusta tablica jest poprawna dla pól liściowych). Strona FINISH musi mieć co
                najmniej jeden element informacyjny, bez pól wejściowych.
                Każde id ma być osobnym, losowym UUID v4 — nie używaj placeholderów i nie powtarzaj identyfikatorów.
                Schematyczny przykład zagnieżdżenia (nazwy przykładowe nie są danymi do skopiowania):
                {"title":"Tytuł ze źródła","tabs":[{"id":"11111111-1111-4111-8111-111111111111","title":"Dane","type":"MAIN","questions":[{"x":0,"y":0,"rows":1,"cols":3,"field":{"id":"22222222-2222-4222-8222-222222222222","type":"SECTION","techName":"sekcja_przykladowa","title":"Sekcja","description":"","options":[],"validation":[],"readOnly":false,"repeatingSectionGrid":[{"x":0,"y":0,"rows":1,"cols":3,"field":{"id":"33333333-3333-4333-8333-333333333333","type":"TEXT_FIELD","techName":"pole_przykladowe","title":"Pole ze źródła","description":"","options":[],"validation":[],"readOnly":false,"repeatingSectionGrid":[],"states":[],"questionOrder":0,"tooltipMessage":""}}],"states":[],"questionOrder":0,"tooltipMessage":""}}]},{"id":"44444444-4444-4444-8444-444444444444","title":"Podsumowanie","type":"FINISH","questions":[{"x":0,"y":0,"rows":1,"cols":3,"field":{"id":"55555555-5555-4555-8555-555555555555","type":"TEXT_BLOCK","techName":"podsumowanie","title":"Podsumowanie","description":"Treść informacyjna wynikająca ze źródła","options":[],"validation":[],"readOnly":true,"repeatingSectionGrid":[],"states":[],"questionOrder":0,"tooltipMessage":""}}]}],"flow":{"links":[{"id":"66666666-6666-4666-8666-666666666666","source":"11111111-1111-4111-8111-111111111111","target":"44444444-4444-4444-8444-444444444444"}]}}
                Przykład pokazuje wyłącznie strukturę. Użyj treści, nazw, pól i opcji z wejściowego blankietu.
                Używaj wyłącznie typów z reguł technicznych COI: SECTION, REPEATING_SECTION, TEXT_FIELD,
                TEXTAREA, TEXT_BLOCK, NUMBER, DATEPICKER, CHECKBOX, RADIO, SELECT, FILE_UPLOADER,
                CONSENT_SECTION i INFO_SECTION. Sekcje tematyczne reprezentuj jako SECTION, a powtarzalne dane
                jako REPEATING_SECTION. Pola muszą należeć do sekcji; nie zagnieżdżaj sekcji w sekcji.
                Zachowaj wszystkie strony, sekcje, pola, etykiety, instrukcje, zgody i opcje wyboru z dokumentu.
                Nie wymyślaj danych, pól, opcji ani walidacji, których dokument nie uzasadnia. Nie twórz pustych pól zastępczych.
                Jeżeli nie można przygotować kompletnego formularza, zwróć pole generationIssues z listą brakujących informacji;
                taki wynik zostanie odrzucony i skierowany do ręcznej weryfikacji.
                Dla list i radio options ma postać [{"id":"wartość","name":"etykieta","techName":"nazwa_techniczna"}].
                %s
                Słowniki można przypisać tylko do SELECT, RADIO i CHECKBOX. Dla pola słownikowego options ma być puste.
                Jeśli potrzebnego słownika nie ma w katalogu, nie wpisuj go w dictionaryName.
                Dodaj wtedy dictionaryProposals z nazwą, strukturą (klucz, etykieta, typ wartości i źródło danych)
                oraz uzasadnieniem. Propozycje są metadanymi do weryfikacji, odrębnymi od gotowych słowników.
                Walidacje wybieraj wyłącznie z podanej listy w katalogu kontekstu. Użyj REQUIRED gdy pole jest wymagane.
                Identyfikatory, numery telefonów i kody pocztowe zapisuj jako tekst, aby zachować zera wiodące.
                Dane inicjalne zapisuj wyłącznie jako defaultValue="SOURCE.attribute" oraz
                defaultValueType="EXTERNAL_SYSTEM" i readOnly=true. Wybierz wyłącznie źródło i atrybut z katalogu.
                Zachowaj typ pola zgodny z atrybutem. Nie dopisuj ręcznie defaultValue dla dynamicznego źródła.
                Dla zależności używaj field.states z type SHOW albo READ_ONLY. Sekcje i sekcje powtarzające się
                obsługują SHOW; pozostałe pola obsługują SHOW i READ_ONLY. Query zapisuj w składni JSONata/COI,
                odwołując się do techName pola sterującego i jego __userAnswer. Nie używaj HIDE.
                Wartości techName zapisuj małymi literami, bez polskich znaków, w snake_case.
                Stosuj tematyczne sekcje, prosty język, kolejność danych stron, danych sprawy, kontaktu i zgód.
                Używaj jednokolumnowego układu: x=0, cols=3; kolejne elementy układaj w rosnących y.
                Utwórz co najmniej jedną stronę MAIN oraz końcową FINISH; FINISH nie może zawierać pól do wprowadzania danych.
                flow.links musi łączyć strony zgodnie z ich rzeczywistymi ścieżkami.
                flow.links ma odzwierciedlać rzeczywiste przejścia między stronami; gdy brak obsługiwanych warunków
                nawigacji w schemacie COI, zachowaj kolejność liniową. Wszystkie id stron, pól i przejść muszą być UUID.
                Nie pomijaj treści źródłowej.
                Nie dodawaj tekstu poza JSON-em.

                %s
                Nazwa pliku: %s
                Treść formularza:
                %s
                """.formatted(catalogContext, correction, filename, extractedText);
        return List.of(
                Map.of("role", "system", "content", "Jesteś ekspertem od cyfryzacji formularzy administracji publicznej. Wiernie przenosisz strukturę dokumentu. Nie opisuj toku rozumowania; od razu zwróć wymagany JSON."),
                Map.of("role", "user", "content", prompt));
    }
}
