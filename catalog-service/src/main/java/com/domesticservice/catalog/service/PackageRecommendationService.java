package com.domesticservice.catalog.service;

import com.domesticservice.catalog.dto.CatalogResponse.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import org.springframework.stereotype.Service;
import static com.domesticservice.catalog.service.PriceQuoteService.bad;

@Service
public class PackageRecommendationService {
    private final CatalogQueryService catalog;
    public PackageRecommendationService(CatalogQueryService catalog) { this.catalog = catalog; }

    public PackageRecommendation recommend(long serviceId, BigDecimal area, int maxStaffCount, int maxDurationMinutes) {
        return plan(catalog.detail(serviceId), area, maxStaffCount, maxDurationMinutes);
    }

    static PackageRecommendation plan(Detail detail, BigDecimal area, int maxStaffCount, int maxDurationMinutes) {
        if (maxStaffCount < 1 || maxStaffCount > 20 || maxDurationMinutes < 0 || maxDurationMinutes > 720)
            throw bad("Giới hạn nhân viên hoặc thời lượng không hợp lệ.");
        var measurement = detail.requirements().stream().filter(r -> "areaM2".equals(r.fieldKey()) && "NUMBER".equals(r.fieldType())).findFirst();
        if (measurement.isEmpty()) return new PackageRecommendation("UNSUPPORTED", area, List.of(), List.of(), "Dịch vụ chưa có định mức diện tích để tự đề xuất gói.");
        if (area == null || area.signum() <= 0) throw bad("Diện tích phải lớn hơn 0.");
        ServiceQuestionnaireValidator.validate(List.of(measurement.get()), Map.of("areaM2", area));
        var unknown = detail.packages().stream().filter(p -> p.maxArea() == null).map(ServicePackage::id).toList();
        var order = Comparator.comparingLong(PackageSuggestion::baseAmount)
            .thenComparingInt(PackageSuggestion::requiredStaffCount).thenComparingInt(PackageSuggestion::durationMinutes)
            .thenComparing(PackageSuggestion::coveredAreaM2).thenComparingLong(PackageSuggestion::packageId).thenComparingInt(PackageSuggestion::quantity);
        List<PackageSuggestion> bestByPackage = new ArrayList<>();
        for (var pack : detail.packages()) {
            if (pack.maxArea() == null || pack.maxArea().signum() <= 0 || pack.defaultStaffCount() < 1 || pack.durationMinutes() < 1) continue;
            PackageSuggestion best = null;
            int maxQuantity = "ITEM".equals(detail.service().priceUnit()) ? Math.min(100, maxDurationMinutes / pack.durationMinutes()) : 1;
            for (int quantity = 1; quantity <= maxQuantity; quantity++) {
                int minutes = ((pack.durationMinutes() * quantity + 29) / 30) * 30;
                if (minutes > maxDurationMinutes) continue;
                BigDecimal coverage = pack.maxArea().multiply(BigDecimal.valueOf(quantity));
                int teams = area.divide(coverage, 0, RoundingMode.CEILING).intValueExact();
                long staff = (long) teams * pack.defaultStaffCount();
                if (staff > maxStaffCount) continue;
                var option = new PackageSuggestion(pack.id(), pack.name(), (int) staff, quantity,
                    Math.multiplyExact(Math.multiplyExact(pack.basePrice(), quantity), teams), minutes,
                    coverage.multiply(BigDecimal.valueOf(teams)));
                if (best == null || order.compare(option, best) < 0) best = option;
            }
            if (best != null) bestByPackage.add(best);
        }
        bestByPackage.sort(order);
        String message = bestByPackage.isEmpty() ? "Chưa có gói đủ diện tích trong giới hạn nhân viên/thời lượng đã chọn. Hãy đổi thời gian hoặc cách đặt."
            : "Chi phí gói thấp nhất trong các gói có định mức đủ diện tích; chưa gồm tiện ích và ưu đãi. Định mức áp dụng cho tổng diện tích cần làm, không nhân thêm số tầng.";
        return new PackageRecommendation(bestByPackage.isEmpty() ? "NO_MATCH" : "RECOMMENDED", area, List.copyOf(bestByPackage), unknown, message);
    }

    static void validateCapacity(Detail detail, ServicePackage pack, Map<?, ?> answers, long quantity, long count) {
        if (pack.maxArea() == null || !answers.containsKey("areaM2")) return;
        BigDecimal area = new BigDecimal(answers.get("areaM2").toString());
        BigDecimal coverage = pack.maxArea().multiply(BigDecimal.valueOf(quantity)).multiply(BigDecimal.valueOf(count / pack.defaultStaffCount()));
        if (area.compareTo(coverage) > 0) throw bad("Gói và số nhân viên chưa đủ cho diện tích đã nhập. Vui lòng áp dụng đề xuất hoặc chọn gói lớn hơn.");
    }
}
