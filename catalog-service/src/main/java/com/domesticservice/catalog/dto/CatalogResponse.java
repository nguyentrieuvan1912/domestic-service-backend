package com.domesticservice.catalog.dto;

import java.math.BigDecimal;
import java.util.List;
import tools.jackson.databind.JsonNode;

/** Public read models; do not expose database entities or quote internals. */
public final class CatalogResponse {
    private CatalogResponse() {}

    public record Category(long id, String code, String name, String description,
                           String iconUrl, String group) {}
    public record ServiceSummary(long id, long categoryId, String categoryCode, String categoryName,
                                 String code, String name, String description, String shortDescription,
                                 String serviceType, String priceUnit, long basePrice,
                                 int estimatedDurationMinutes, boolean requiresQualification,
                                 String imageUrl, List<String> highlights) {}
    public record ServicePackage(long id, String name, String description, int durationMinutes,
                                 long basePrice, int defaultStaffCount, BigDecimal maxArea) {}
    public record AddOn(long id, String name, String description, long price,
                        int extraDurationMinutes, String imageUrl) {}
    public record Requirement(long id, String fieldKey, String label, String fieldType,
                              boolean required, JsonNode options, JsonNode validationRules,
                              int displayOrder) {}
    public record Detail(ServiceSummary service, List<ServicePackage> packages, List<AddOn> addOns,
                         List<Requirement> requirements, List<String> workflow, List<String> benefits) {}
    public record Page(List<ServiceSummary> items, long total, int page, int size, long totalPages) {}
}
