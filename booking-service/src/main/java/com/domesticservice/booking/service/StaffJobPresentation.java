package com.domesticservice.booking.service;

import java.util.*;

/**
 * Presentation comes only from the booking snapshots confirmed by the customer.
 */
final class StaffJobPresentation {

    private StaffJobPresentation() {
    }

    static String address(Map<String, Object> address) {
        String full = Objects.toString(address.get("fullAddress"), "").trim();
        if (!full.isEmpty())
            return full;
        var parts = new LinkedHashSet<String>();
        for (var keys : List.of(List.of("streetAddress", "detailAddress", "detail_address"), List.of("ward"), List.of("district"), List.of("city", "province"))) {
            for (String key : keys) {
                String value = Objects.toString(address.get(key), "").trim();
                if (!value.isEmpty()) {
                    parts.add(value);
                    break;
                }
            }
        }
        return String.join(", ", parts);
    }

    static void add(Map<String, Object> target, Map<String, Object> booking, Map<String, Object> service, Map<String, Object> address) {
        summary(target, booking, service, address);
        var selections = new ArrayList<Map<String, String>>();
        appendSelections(selections, service.get("configuration"), service.get("configurationLabels"));
        appendSelections(selections, service.get("answers"), service.get("answerLabels"));
        target.put("workSelections", selections);
    }

    static void summary(Map<String, Object> target, Map<String, Object> booking, Map<String, Object> service, Map<String, Object> address) {
        target.put("paymentMethod", booking.get("payment_method"));
        target.put("paymentStatus", booking.get("payment_status"));
        target.put("totalCustomerPaid", booking.get("total_amount"));
        target.put("city", Objects.toString(address.getOrDefault("city", address.get("province")), ""));
        target.put("addOnsText", names(service.get("addOns")));
        target.put("relatedServicesText", names(service.get("relatedServices")));
    }

    private static String names(Object value) {
        if (!(value instanceof List<?> list))
            return "";
        return list.stream().filter(Map.class::isInstance).map(Map.class::cast).map(item -> Objects.toString(item.get("name"), "").trim()).filter(name -> !name.isEmpty()).reduce((a, b) -> a + ", " + b).orElse("");
    }

    private static void appendSelections(List<Map<String, String>> result, Object raw, Object rawLabels) {
        if (!(raw instanceof Map<?, ?> values))
            return;
        Map<?, ?> labels = rawLabels instanceof Map<?, ?> map ? map : Map.of();
        for (var item : values.entrySet()) {
            if (item.getValue() == null)
                continue;
            String value = item.getValue() instanceof Boolean flag ? (flag ? "Có" : "Không") : item.getValue() instanceof List<?> list ? String.join(", ", list.stream().map(Objects::toString).toList()) : item.getValue().toString();
            if (!value.isBlank())
                result.add(Map.of("label", Objects.toString(labels.get(item.getKey()), item.getKey().toString()), "value", value));
        }
    }
}
