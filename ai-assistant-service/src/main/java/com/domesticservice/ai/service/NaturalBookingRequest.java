package com.domesticservice.ai.service;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

final class NaturalBookingRequest {
    private static final Pattern REQUEST = Pattern.compile(
        "^(?:(?:toi|minh|em|anh|chi) )?(?:(?:muon|can|nho|thue|lam on|hay|tim(?! hieu\\b)) "
            + "(?:(?:co|tim|thue|nho) )?(?:nguoi )?|(?:giup|cho) (?:toi|minh) )(.+)$");
    private static final Pattern COMMAND = Pattern.compile(
        "^(?:mua|di cho|don|don dep|ve sinh|giat|ui|la|nau|trong|giu|cham|tuoi|tam|nap|bom|khu|phun|hut|giup)\\b.*\\b(?:giup|ho)\\b");
    private static final Pattern INQUIRY = Pattern.compile(
        "^(?:biet|hoi|xem|tim hieu|tu van|so sanh|kiem tra|khong)\\b"
            + "|\\b(?:bao lau|co nen|nhu the nao|chinh sach|khuyen mai|duoc khong|hay khong)\\b"
            + "|\\b(?:nao|khong|sao) (?:a|nhi|vay)$|\\b(?:nao|khong|sao)$");

    private static final List<Rule> RULES = List.of(
        rule("GROCERY_SHOPPING", "di (?:sieu thi|cho ho)|di cho giup|chon thuc pham"
            + "|mua(?: [a-z0-9]+){0,9} (?:thuc pham|do an|do tuoi|rau cu|rau sach|thit heo|thit lon|thit bo|hai san|trai cay|hoa qua)"),
        rule("AC_MAINTENANCE", "(?:nap|bom|them) (?:ga|gas)|(?:bao duong|bao tri) (?:may lanh|dieu hoa)"),
        rule("AC_CLEANING", "(?:ve sinh|rua|lam sach|xit rua)(?: (?:giup|ho|toi|minh|may|mot|hai|ba|bon|nam|sau|bay|tam|chin|muoi|[0-9]+)){0,4} (?:may lanh|dieu hoa)"),
        rule("CHILD_CARE", "bao mau|(?:trong|giu|cham|cham soc) (?:tre(?: em)?|be|con(?: nho)?)"),
        rule("ELDERLY_CARE", "(?:cham|cham soc|trong) (?:nguoi gia|nguoi cao tuoi|ong ba|cu gia)"),
        rule("PET_CARE", "(?:cham|cham soc|trong|giu|tam|dat di dao) (?:thu cung|cho meo|cho|meo)"),
        rule("PLANT_CARE", "(?:cham|cham soc|tuoi|cat tia) (?:cay(?: canh)?|vuon|san vuon)"),
        rule("LAUNDRY", "giat (?:ui|la|do|quan ao|chan ga)|(?:ui|la) (?:do|quan ao)"),
        rule("COOKING", "nau (?:an|com|bua (?:an|sang|trua|toi)|mon an)"),
        rule("OTHER_SERVICES", "(?:phun )?(?:khu khuan|khu trung|khu mui am moc)|xu ly am moc"),
        rule("REFRIGERATOR_CLEANING", "(?:ve sinh|lau don|lam sach|khu mui) (?:tu lanh|tu dong)"),
        rule("WASHING_MACHINE", "(?:ve sinh|lam sach|rua) (?:may giat|long giat)"),
        rule("DRYER_CLEANING", "(?:ve sinh|lam sach|rua) may say(?: quan ao)?"),
        rule("CLEANING_UPHOLSTERY", "(?:ve sinh|lam sach|giat|hut bui) (?:ghe )?(?:sofa|so pha|nem|dem|rem(?: cua)?)"),
        rule("CLEANING_DEEP", "tong ve sinh toan dien", "tong ve sinh toan dien"),
        rule("CLEANING_DEEP", "tong ve sinh|(?:don|ve sinh) (?:ky|sau|toan bo) (?:can ho|nha)"
            + "|(?:don|ve sinh) (?:nha|can ho) sau (?:xay|sua|thi cong)", "tong ve sinh"),
        rule("CLEANING_HOURLY", "(?:don(?: dep)?|ve sinh) (?:nha )?(?:dinh ky|hang tuan|moi tuan|hang thang)", "don dep dinh ky"),
        rule("CLEANING_HOURLY", "(?:don(?: dep)?|ve sinh) (?:nha )?theo ca", "don dep theo ca"),
        rule("CLEANING_HOURLY", "don (?:dep )?nha(?: cua)?(?: theo gio)?(?! sau (?:xay|sua|thi cong))|giup viec theo gio", "don dep nha theo gio"),
        rule("CLEANING_HOURLY", "ve sinh nha(?: cua)?(?: theo gio)?(?! sau (?:xay|sua|thi cong))", "ve sinh nha theo gio")
    );

    private NaturalBookingRequest() {}

    static String task(String normalized) {
        var request = REQUEST.matcher(normalized);
        if (request.matches()) return request.group(1);
        return COMMAND.matcher(normalized).find() ? normalized : null;
    }

    static boolean inquiry(String task) {
        return INQUIRY.matcher(task).find();
    }

    static List<Map<String, Object>> match(String task, List<Map<String, Object>> services) {
        var matches = RULES.stream().filter(rule -> rule.phrase().matcher(task).find()).toList();
        var types = matches.stream().map(Rule::type).distinct().toList();
        var candidates = services.stream().filter(service -> types.contains(service.get("serviceType"))).toList();
        if (types.size() != 1 || candidates.size() <= 1) return candidates;

        // Specific hourly variants precede the generic request. Choose only an actual Catalog name.
        String preferredName = matches.getFirst().preferredName();
        var preferred = candidates.stream().filter(service ->
            AiBusinessTools.catalogWords(service.get("name").toString()).equals(preferredName)).toList();
        return preferred.isEmpty() ? candidates : preferred;
    }

    private static Rule rule(String type, String phrase) {
        return rule(type, phrase, "");
    }

    private static Rule rule(String type, String phrase, String preferredName) {
        return new Rule(type, Pattern.compile("\\b(?:" + phrase + ")\\b"), preferredName);
    }

    private record Rule(String type, Pattern phrase, String preferredName) {}
}
