package com.domesticservice.catalog.service;

import com.domesticservice.catalog.dto.CatalogResponse.Requirement;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class ServiceQuestionnaireValidatorTest {
    final ObjectMapper json=new ObjectMapper();
    Requirement field(String key,String type,boolean required,String options,String rules) {
        return new Requirement(1,key,key,type,required,json.readTree(options),json.readTree(rules),10);
    }
    @Test void requiresServiceSpecificFieldsAndRejectsUnknownFields() {
        var fields=List.of(field("menu","TEXT",true,"[]","{}"));
        assertThrows(ResponseStatusException.class,()->ServiceQuestionnaireValidator.validate(fields,Map.of()));
        assertThrows(ResponseStatusException.class,()->ServiceQuestionnaireValidator.validate(fields,Map.of("menu","Dinner","airconType","Wall")));
        assertThrows(ResponseStatusException.class,()->ServiceQuestionnaireValidator.validate(fields,List.of("Dinner")));
        assertEquals(Map.of("menu","Dinner"),ServiceQuestionnaireValidator.validate(fields,Map.of("menu","Dinner")));
    }
    @Test void numericAnswersRespectBoundsAndIntegerCounts() {
        var fields=List.of(field("diners","NUMBER",true,"[]","{\"min\":1,\"max\":30,\"integer\":true}"));
        for(String value:List.of("0","31","1.5","NaN","true"))
            assertThrows(ResponseStatusException.class,()->ServiceQuestionnaireValidator.validate(fields,Map.of("diners",value)));
        assertEquals("4",ServiceQuestionnaireValidator.validate(fields,Map.of("diners","4")).get("diners"));
    }
    @Test void booleanFalseIsAnAnswerAndSelectionsMustBelongToTheService() {
        var fields=List.of(field("waterAccess","BOOLEAN",true,"[]","{}"),field("type","SELECT",true,"[\"Front\",\"Top\"]","{}"));
        assertDoesNotThrow(()->ServiceQuestionnaireValidator.validate(fields,Map.of("waterAccess",false,"type","Front")));
        assertThrows(ResponseStatusException.class,()->ServiceQuestionnaireValidator.validate(fields,Map.of("waterAccess","false","type","Front")));
        assertThrows(ResponseStatusException.class,()->ServiceQuestionnaireValidator.validate(fields,Map.of("waterAccess",true,"type","Other")));
    }
    @Test void multiSelectionRejectsEmptyDuplicateAndForeignChoices() {
        var fields=List.of(field("tasks","MULTI_SELECT",true,"[\"Feed\",\"Play\"]","{}"));
        for(var value:List.of(List.of(),List.of("Feed","Feed"),List.of("Wash")))
            assertThrows(ResponseStatusException.class,()->ServiceQuestionnaireValidator.validate(fields,Map.of("tasks",value)));
        assertDoesNotThrow(()->ServiceQuestionnaireValidator.validate(fields,Map.of("tasks",List.of("Feed","Play"))));
    }
    @Test void optionalBlankNumbersDoNotBlockBookingButLongTextDoes() {
        var fields=List.of(field("age","NUMBER",false,"[]","{\"min\":1}"),field("notes","TEXT",false,"[]","{}"));
        assertDoesNotThrow(()->ServiceQuestionnaireValidator.validate(fields,Map.of("age","")));
        assertThrows(ResponseStatusException.class,()->ServiceQuestionnaireValidator.validate(fields,Map.of("notes","x".repeat(2001))));
    }
}
