package com.hacknation.cyfryzacja.services;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

import com.hacknation.cyfryzacja.JSONRes.FormDefinition;
import com.hacknation.cyfryzacja.JSONRes.FormDefinition.FlowLink;
import com.hacknation.cyfryzacja.JSONRes.FormDefinition.FormField;
import com.hacknation.cyfryzacja.JSONRes.FormDefinition.FormFlow;
import com.hacknation.cyfryzacja.JSONRes.FormDefinition.FormQuestion;
import com.hacknation.cyfryzacja.JSONRes.FormDefinition.FormTab;
import com.hacknation.cyfryzacja.walidacja.FileValidationService.ValidatedFile;

//FTJ to znaczy file to json generator
@Service 
public class FTJGenerator {
    public FormDefinition generate(ValidatedFile file){
        String mainTabId = UUID.randomUUID().toString();
        String finishTabId = UUID.randomUUID().toString();

        String rawTitle = "Wniosek na podstawie " + file.filename();
        String title = rawTitle.length() > 255 ? rawTitle.substring(0, 255) : rawTitle;

        FormQuestion applicantNameField = new FormQuestion(
                0, 0, 1, 1,
                new FormField(
                        UUID.randomUUID().toString(),
                        "TEXT_FIELD",
                        "applicant_name",
                        "Imię",
                        "Dane pobierane z rejestru",
                        null,
                        List.of("REQUIRED"),
                        null,
                        true,
                        null,
                        "MLPZ.name",
                        "EXTERNAL_SYSTEM",
                        null,
                        null,
                        1,
                        null
                )
        );

        FormTab mainTab = new FormTab(
                mainTabId,
                "Dane wnioskodawcy",
                "MAIN",
                List.of(applicantNameField)
        );

        FormQuestion finishInfo = new FormQuestion(
                0, 0, 1, 1,
                new FormField(
                        UUID.randomUUID().toString(),
                        "INFO_SECTION",
                        "summary_info",
                        "Podsumowanie",
                        "Przed wysłaniem sprawdź poprawność wprowadzonych danych.",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        1,
                        null
                )
        );

        FormTab finishTab = new FormTab(
                finishTabId,
                "Podsumowanie",
                "FINISH",
                List.of(finishInfo)
        );

        FormFlow flow = new FormFlow(List.of(
                new FlowLink(UUID.randomUUID().toString(), mainTabId, finishTabId)
        ));

        return new FormDefinition(title, List.of(mainTab, finishTab), flow);
    }
}
