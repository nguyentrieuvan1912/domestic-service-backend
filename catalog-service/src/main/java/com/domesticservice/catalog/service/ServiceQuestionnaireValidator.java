package com.domesticservice.catalog.service;

import com.domesticservice.catalog.dto.CatalogResponse.Requirement;
import java.math.BigDecimal;
import java.util.*;
import static com.domesticservice.catalog.service.PriceQuoteService.bad;

/** Validates the persisted catalog questionnaire before any quote or booking is stored. */
final class ServiceQuestionnaireValidator {
    private ServiceQuestionnaireValidator() {}
    static Map<?, ?> validate(List<Requirement> fields, Object rawAnswers) {
        if (rawAnswers != null && !(rawAnswers instanceof Map<?, ?>))
            throw bad("Câu trả lời dịch vụ phải là một đối tượng.");
        var answers = rawAnswers instanceof Map<?, ?> map ? map : Map.of();
        Set<String> fieldKeys = new HashSet<>();
        for (var requirement : fields) fieldKeys.add(requirement.fieldKey());
        if (answers.size() > 50 || answers.keySet().stream().anyMatch(k -> !fieldKeys.contains(k.toString())))
            throw bad("Câu trả lời chứa trường không thuộc yêu cầu dịch vụ.");
        for (var r : fields) {
            Object value = answers.get(r.fieldKey());
            if (r.required() && (value == null || value.toString().isBlank() || value instanceof Collection<?> items && items.isEmpty()))
                throw bad("Thiếu thông tin: " + r.label());
            if (value == null)
                continue;
            if (!r.required() && value instanceof String text && text.isBlank())
                continue;
            if ("NUMBER".equals(r.fieldType())) {
                try {
                    BigDecimal n = new BigDecimal(value.toString());
                    if (r.validationRules().path("integer").asBoolean(false) && n.stripTrailingZeros().scale() > 0)
                        throw bad("Cần nhập số nguyên: " + r.label());
                    if (r.validationRules().has("min") && n.compareTo(new BigDecimal(r.validationRules().get("min").asText())) < 0 || r.validationRules().has("max") && n.compareTo(new BigDecimal(r.validationRules().get("max").asText())) > 0)
                        throw bad("Giá trị không hợp lệ: " + r.label());
                } catch (NumberFormatException ex) {
                    throw bad("Cần nhập số: " + r.label());
                }
            }
            if ("BOOLEAN".equals(r.fieldType()) && !(value instanceof Boolean))
                throw bad("Giá trị không hợp lệ: " + r.label());
            if ("SELECT".equals(r.fieldType()) && !r.options().isEmpty()) {
                boolean found = false;
                for (var option : r.options()) if (option.asText().equals(value.toString()))
                    found = true;
                if (!found)
                    throw bad("Lựa chọn không hợp lệ: " + r.label());
            }
            if ("MULTI_SELECT".equals(r.fieldType())) {
                var selections = PriceQuoteService.list(value);
                if (selections.stream().distinct().count() != selections.size())
                    throw bad("Trùng lựa chọn: " + r.label());
                for (var selection : selections) {
                    boolean found = false;
                    for (var option : r.options()) if (option.asText().equals(Objects.toString(selection)))
                        found = true;
                    if (!found)
                        throw bad("Lựa chọn không hợp lệ: " + r.label());
                }
            }
            if ("DATE".equals(r.fieldType())) {
                try {
                    java.time.LocalDate.parse(value.toString());
                } catch (java.time.DateTimeException ex) {
                    throw bad("Ngày không hợp lệ: " + r.label());
                }
            }
            if ("TEXT".equals(r.fieldType()) && !(value instanceof String))
                throw bad("Cần nhập văn bản: " + r.label());
            if (value.toString().length() > 2000)
                throw bad("Câu trả lời quá dài: " + r.label());
        }
        return answers;
    }
}
