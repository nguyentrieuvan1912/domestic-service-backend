package com.domesticservice.ai.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

final class NaturalBookingAnswers {
    private static final String NUMBER = "([1-9][0-9]{0,5}|mot|hai|ba|bon|tu|nam|sau|bay|tam|chin|muoi)";
    private static final List<String> NUMBER_WORDS = List.of("", "mot", "hai", "ba", "bon", "nam",
        "sau", "bay", "tam", "chin", "muoi");
    private static final Pattern DECIMAL = Pattern.compile("\\d[.,]\\d");
    private static final List<Rule> RULES = List.of(
        count("quantity", "(?:co |ve sinh |rua )?" + NUMBER + " (?:chiec )?(?:may lanh|dieu hoa|may)"),
        count("requiredStaffCount", "(?:(?:cu|cho|minh can|can) )?" + NUMBER
            + " (?:nhan vien(?: (?:cung lam|lam song song))?|nguoi (?:lam|lam het|lam lan luot|lam cung|lam song song))"),
        count("areaM2", "(?:(?:dien tich|rong|tam|khoang) )?" + NUMBER + " (?:m2|met vuong)"),
        count("floors", "(?:co )?" + NUMBER + " (?:tang|lau)"),
        count("childCount", "(?:co )?" + NUMBER + " (?:be|tre|dua|chau nho)"),
        count("careAge", "(?:(?:ba|ong|cu|me|bo|cha) (?:minh |toi )?)?" + NUMBER + " tuoi"),
        count("plantCount", "(?:co )?" + NUMBER + " (?:chau|goc|cay)(?: cay)?"),
        count("animalCount", "(?:co )?" + NUMBER + " (?:con|be) (?:cun|cho|meo|thu cung)"),
        count("weightKg", "(?:be |cun |meo )?(?:nang|can nang) " + NUMBER + " (?:kg|ky|can)"),
        count("diners", "(?:nau cho |co )?" + NUMBER + " (?:nguoi an|khau phan)"),
        count("dishCount", "(?:nau |lam )?" + NUMBER + " mon"),
        count("laundryKg", "(?:giat |do nang |do khoang )?" + NUMBER + " (?:kg|ky|can)(?: do| quan ao)?"),
        count("capacityKg", "(?:may |suc chua |tai trong )?" + NUMBER + " (?:kg|ky|can)"),
        count("capacityLitres", "(?:tu |dung tich )?" + NUMBER + " (?:lit|l)"),
        rule("shoppingBudget", "(?:(?:ngan sach|toi da|khong qua|tam|khoang) )?" + NUMBER
            + " (?:k|nghin|ngan)(?: dong)?", 1000),
        rule("shoppingBudget", "(?:(?:ngan sach|toi da|khong qua|tam|khoang) )?" + NUMBER + " trieu(?: dong)?", 1000000),
        rule("homeType", "(?:o |nha la )?(?:can ho|chung cu)", "Căn hộ"),
        rule("homeType", "(?:o |nha la )?(?:nha pho|nha mat dat)", "Nhà phố"),
        rule("homeType", "(?:o |lam o )?van phong", "Văn phòng"),
        rule("homeCondition", "(?:nha )?(?:vua|moi|sau) (?:sua(?: xong)?|son(?: xong)?|thi cong(?: xong)?)", "Sau sửa chữa"),
        rule("homeCondition", "(?:nha )?(?:vua|moi|sau) chuyen nha", "Sau chuyển nhà"),
        rule("hasSupplies", "(?:nha )?(?:khong|chua) co (?:san )?(?:do|dung cu|hoa chat)(?: ve sinh| don dep)?", false),
        rule("hasSupplies", "(?:nha )?co san (?:do|dung cu|hoa chat|nuoc lau san)(?: ve sinh| don dep)?", true),
        rule("airconType", "(?:may |loai )?(?:gan tuong|treo tuong)", "Treo tường"),
        rule("airconType", "(?:may |loai )?(?:am tran|cassette|am tran cassette)", "Âm trần cassette"),
        rule("airconType", "(?:may |loai )?ap tran", "Áp trần"),
        rule("access", "(?:dan nong )?(?:cao phai dung thang|can thang(?: cao)?|phai (?:leo|dung) thang(?: cao)?)", "Cần thang cao"),
        rule("access", "dan nong (?:thap|de tiep can|de voi toi)", "Dễ tiếp cận"),
        rule("gasInspection", "(?:nho |giup )?(?:kiem tra|do) (?:ga|gas)", true),
        rule("gasInspection", "khong can (?:kiem tra|do) (?:ga|gas)", false),
        rule("washerType", "(?:may |loai )?(?:cua truoc|long ngang)", "Cửa trước"),
        rule("washerType", "(?:may |loai )?(?:cua tren|long dung)", "Cửa trên"),
        rule("waterAccess", "(?:khong|chua) co (?:voi|nguon) nuoc(?: gan may)?", false),
        rule("waterAccess", "co (?:san )?(?:voi|nguon) nuoc(?: gan may)?", true),
        rule("fridgeType", "(?:tu |loai )?(?:mini|tu nho)", "Tủ mini"),
        rule("fridgeType", "(?:tu |loai )?(?:hai|2) cua", "Hai cửa"),
        rule("fridgeType", "(?:tu |loai )?side by side", "Side by side"),
        rule("emptyFridge", "(?:tu )?(?:van|con|van con) do(?: an)?", false),
        rule("emptyFridge", "(?:tu )?(?:da )?(?:trong|lay het do(?: ra roi)?)", true),
        rule("dryerType", "(?:may |loai )?bom nhiet", "Bơm nhiệt"),
        rule("dryerType", "(?:may |loai )?ngung tu", "Ngưng tụ"),
        rule("dryerType", "(?:may |loai )?thong hoi", "Thông hơi"),
        rule("dryerPosition", "(?:may )?(?:de|dat|chong) (?:o )?tren may giat", "Chồng trên máy giặt"),
        rule("furnitureType", "(?:loai |ghe )?(?:sofa|so pha)", "Sofa"),
        rule("furnitureType", "(?:loai )?(?:nem|dem)", "Nệm"),
        rule("furnitureType", "(?:loai )?rem(?: cua)?", "Rèm"),
        rule("material", "(?:chat lieu |boc )?vai", "Vải"),
        rule("material", "(?:chat lieu |boc )?ni", "Nỉ"),
        rule("material", "(?:chat lieu |boc )?da", "Da"),
        rule("animalType", "(?:(?:la|nha nuoi) )?(?:be )?(?:cun|cho)", "Chó"),
        rule("animalType", "(?:(?:la|nha nuoi) )?(?:be )?meo", "Mèo"),
        rule("gardenType", "(?:cay )?(?:o |ngoai )?ban cong", "Ban công"),
        rule("gardenType", "(?:cay )?(?:o |ngoai )?san vuon", "Sân vườn"),
        rule("gardenType", "(?:cay )?(?:o |tren )?san thuong", "Sân thượng"),
        rule("mobility", "(?:(?:ong|ba|cu) )?(?:van )?tu di(?: lai)? duoc", "Tự đi lại"),
        rule("mobility", "(?:(?:ong|ba|cu) )?(?:phai diu|can nguoi diu|di lai can ho tro)", "Cần hỗ trợ"),
        rule("mobility", "(?:(?:ong|ba|cu) )?(?:chi )?nam (?:tren|tai) giuong", "Nằm tại giường"),
        rule("mealStyle", "(?:nau |an |giong vi )?(?:vi |kieu )?(?:mien )?bac", "Miền Bắc"),
        rule("mealStyle", "(?:nau |an |giong vi )?(?:vi |kieu )?(?:mien )?trung", "Miền Trung"),
        rule("mealStyle", "(?:nau |an |giong vi )?(?:vi |kieu )?(?:mien )?nam", "Miền Nam"),
        rule("mealStyle", "(?:nau |an |mon )?chay", "Ăn chay"),
        rule("ingredients", "(?:minh |toi )?(?:da )?(?:mua|chuan bi) (?:san |du )?nguyen lieu(?: roi)?", "Khách đã chuẩn bị"),
        rule("delicates", "(?:do |co )?(?:lua|len|lua va len)", "Lụa / len"),
        rule("delicates", "(?:do |co )?cong so", "Đồ công sở"),
        rule("disinfectionSpace", "(?:khu vuc |lam o )?(?:nha o|van phong|cua hang|kho)", ""),
        rule("substitution", "(?:neu het hang )?(?:goi|hoi) (?:cho )?(?:minh|toi) truoc(?: khi doi)?", "Gọi khách trước khi đổi"),
        rule("substitution", "(?:neu het hang )?(?:bo qua|bo luon)(?: mon do)?", "Bỏ qua món đó"),
        rule("substitution", "(?:neu het hang )?(?:doi|chon|mua) (?:mon )?tuong duong(?: trong ngan sach)?",
            "Chọn món tương đương trong ngân sách")
    );

    private NaturalBookingAnswers() {}

    static List<List<Answer>> candidates(String message) {
        if (message.contains("?") || DECIMAL.matcher(message).find()) return List.of();
        var result = new ArrayList<List<Answer>>();
        for (String part : message.split("[;,\\n]+")) {
            String clause = AiBusinessTools.catalogWords(part.replace("²", "2"))
                .replaceFirst("^(?:nha (?:minh|toi|em)|minh|toi(?! da\\b)|em) (?:co )?", "");
            var matches = matchClause(clause);
            if (matches.isEmpty()) matches = matchClause(clause.replaceFirst(" (?:a|nhe|nha|giup minh|giup toi)$", ""));
            if (matches.isEmpty()) return List.of();
            result.add(matches);
        }
        return result;
    }

    private static List<Answer> matchClause(String clause) {
        return RULES.stream().filter(rule -> rule.pattern().matcher(clause).matches())
            .map(rule -> new Answer(rule.field(), rule.value(clause))).distinct().toList();
    }

    static Map<String, Object> validate(List<List<Answer>> candidates, JsonNode service, JsonNode requirements) {
        var changes = new LinkedHashMap<String, Object>();
        var answers = new LinkedHashMap<String, Object>();
        var fields = new LinkedHashMap<String, JsonNode>();
        requirements.forEach(field -> fields.put(field.path("fieldKey").asText(), field));
        boolean airConditioner = "AC_CLEANING".equals(service.path("serviceType").asText());
        for (var clause : candidates) {
            var supported = clause.stream().map(answer -> validatedAnswer(answer, fields, airConditioner))
                .filter(answer -> answer.value() != null).toList();
            if (supported.size() != 1) return Map.of();
            var answer = supported.getFirst();
            var target = isDraftField(answer.field()) ? changes : answers;
            Object previous = target.putIfAbsent(answer.field(), answer.value());
            if (previous != null && !previous.equals(answer.value())) return Map.of();
        }
        if (!answers.isEmpty()) changes.put("answers", answers);
        return changes;
    }

    private static Answer validatedAnswer(Answer answer, Map<String, JsonNode> fields, boolean airConditioner) {
        if ("requiredStaffCount".equals(answer.field()) || (airConditioner && "quantity".equals(answer.field()))) return answer;
        var requirement = fields.get(answer.field());
        return new Answer(answer.field(), requirement == null ? null : catalogValue(answer.value(), requirement));
    }

    private static boolean isDraftField(String field) {
        return "quantity".equals(field) || "requiredStaffCount".equals(field);
    }

    private static Object catalogValue(Object value, JsonNode requirement) {
        return switch (requirement.path("fieldType").asText()) {
            case "NUMBER" -> value instanceof Number ? value : null;
            case "BOOLEAN" -> value instanceof Boolean ? value : null;
            case "SELECT" -> value instanceof String text ? requirement.path("options").valueStream()
                .map(JsonNode::asText).filter(option -> AiBusinessTools.catalogWords(option)
                    .equals(AiBusinessTools.catalogWords(text))).findFirst().orElse(null) : null;
            default -> null;
        };
    }

    private static Rule count(String field, String phrase) {
        return rule(field, phrase, 1);
    }

    private static Rule rule(String field, String phrase, Object value) {
        return new Rule(field, Pattern.compile(phrase), value);
    }

    record Answer(String field, Object value) {}

    private record Rule(String field, Pattern pattern, Object answer) {
        private Object value(String clause) {
            if (answer instanceof Integer scale) {
                var match = pattern.matcher(clause);
                if (!match.matches()) throw new IllegalArgumentException("Invalid numeric answer");
                String count = match.group(1);
                int amount = count.equals("tu") ? 4 : NUMBER_WORDS.indexOf(count);
                return (amount < 0 ? Long.parseLong(count) : amount) * scale;
            }
            if ("".equals(answer)) return clause.replaceFirst("^(?:khu vuc|lam o) ", "");
            return answer;
        }
    }
}
