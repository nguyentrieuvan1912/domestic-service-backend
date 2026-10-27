package com.domesticservice.finance.service;

import com.domesticservice.common.web.ServiceHttpClient;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import com.domesticservice.finance.repository.FinanceOperationsSqlRepository;

@Service
public class FinanceOperationsService {

    private final FinanceOperationsSqlRepository repository;

    private final ObjectMapper json;

    private final ServiceHttpClient identity, booking;
    private final PayoutBankDirectory payoutBanks;
    private final com.domesticservice.finance.config.PayoutProperties payoutConfig;

    public FinanceOperationsService(FinanceOperationsSqlRepository repository, ObjectMapper json, @Value("${clients.identity.base-url}") String identityUrl, @Value("${clients.booking.base-url}") String bookingUrl, PayoutBankDirectory payoutBanks, com.domesticservice.finance.config.PayoutProperties payoutConfig) {
        this.repository = repository;
        this.json = json;
        this.payoutBanks=payoutBanks; this.payoutConfig=payoutConfig;
        identity = new ServiceHttpClient(identityUrl);
        booking = new ServiceHttpClient(bookingUrl);
    }

    static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    static String text(Map<String, Object> b, String key, int max, boolean required) {
        String s = Objects.toString(b.get(key), "").strip();
        if (s.length() > max || required && s.isBlank())
            throw bad("Vui lòng kiểm tra " + key + ".");
        return s;
    }

    static long number(Map<String, Object> b, String key, long min, long max) {
        try {
            long value = Long.parseLong(Objects.toString(b.get(key), ""));
            if (value < min || value > max)
                throw bad("Giá trị " + key + " không hợp lệ.");
            return value;
        } catch (NumberFormatException ex) {
            throw bad("Giá trị " + key + " không hợp lệ.");
        }
    }

    private Map<String, Object> balance(long staff, boolean lock) {
        if (lock)
            repository.balanceInsertStaffBalances(staff);
        var rows = repository.balanceFindStaffBalancesForUpdate(lock, staff);
        return rows.isEmpty() ? Map.of("available_balance", 0L, "pending_balance", 0L, "reserved_balance", 0L, "total_earned", 0L, "withdrawn_amount", 0L) : rows.getFirst();
    }

    public Map<String, Object> wallet(String token, Long staffId) {
        var user = identity.authenticate(token, "STAFF", "ADMIN");
        long staff = staffId == null ? ServiceHttpClient.userId(user) : staffId;
        if (!"ADMIN".equals(user.get("role")) && staff != ServiceHttpClient.userId(user))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền xem số dư này.");
        var b = balance(staff, false);
        var ledger = repository.walletFindWalletTransactions(staff);
        return Map.of("balance", Map.of("available", b.get("available_balance"), "pending", b.get("pending_balance"), "reserved", b.get("reserved_balance"), "totalEarned", b.get("total_earned"), "withdrawn", b.get("withdrawn_amount")), "transactions", ledger, "incomes", repository.walletFindStaffIncomes(staff), "withdrawals", repository.walletFindWithdrawals(staff));
    }

    public List<Map<String, Object>> banks(String token) {
        long staff = ServiceHttpClient.userId(identity.authenticate(token, "STAFF"));
        return withBankNames(repository.banksFindStaffBankAccounts(staff));
    }

    private List<Map<String,Object>> withBankNames(List<Map<String,Object>> rows) {
        if(rows.isEmpty()) return rows;
        var names=payoutBanks.displayNames();
        return rows.stream().map(row -> {
            var result=new LinkedHashMap<String,Object>(row);
            result.put("bankName",names.getOrDefault(Objects.toString(row.get("bankCode"),""),"Chưa xác định ngân hàng"));
            return (Map<String,Object>)result;
        }).toList();
    }

    @Transactional
    public Map<String, Object> saveBank(String token, Map<String, Object> b) {
        long staff = ServiceHttpClient.userId(identity.authenticate(token, "STAFF"));
        String bank = text(b, "bankCode", 30, true), account = text(b, "accountNumber", 50, true), holder = text(b, "accountHolder", 160, true);
        if (!payoutBanks.supports(bank)) throw bad("Vui lòng chọn ngân hàng trong danh sách hỗ trợ đã cấu hình.");
        if (!account.matches("[0-9]{5,30}"))
            throw bad("Số tài khoản phải có 5–30 chữ số.");
        balance(staff, true);
        var existing = repository.saveBankFindStaffBankAccounts(Long.class, staff, bank, account);
        if (!existing.isEmpty())
            return Map.of("id", existing.getFirst(), "status", repository.saveBankFindStaffBankAccounts2(String.class, existing.getFirst()));
        boolean first = !Boolean.TRUE.equals(repository.saveBankExistsStaffBankAccounts(Boolean.class, staff));
        long id = repository.saveBankInsertStaffBankAccounts(staff, bank, account, holder, first);
        return Map.of("id", id, "status", "PENDING");
    }

    @Transactional
    public Map<String, Object> withdraw(String token, Map<String, Object> b) {
        long staff = ServiceHttpClient.userId(identity.authenticate(token, "STAFF"));
        long amount = number(b, "amount", payoutConfig.minimum(), payoutConfig.maximum()), bankId = number(b, "bankAccountId", 1, Long.MAX_VALUE);
        String key = text(b, "requestKey", 160, true);
        var wallet = balance(staff, true);
        var previous = repository.withdrawFindWithdrawals(staff, key);
        if (!previous.isEmpty()) {
            var row = previous.getFirst();
            if (((Number) row.get("amount")).longValue() != amount || ((Number) row.get("bank_account_id")).longValue() != bankId)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã yêu cầu đã dùng cho dữ liệu khác.");
            return Map.of("id", row.get("id"), "status", row.get("status"));
        }
        var banks = repository.withdrawFindStaffBankAccounts(bankId, staff);
        if (banks.isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tài khoản ngân hàng chưa được Admin xác minh hoặc không thuộc bạn.");
        long available = ((Number) wallet.get("available_balance")).longValue(), minimum = ((Number) wallet.getOrDefault("minimum_balance", 0L)).longValue();
        if (available - amount < minimum)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Số dư khả dụng không đủ hoặc thấp hơn mức tối thiểu.");
        long id = repository.withdrawInsertWithdrawals(staff, wallet.get("id"), bankId, json.writeValueAsString(banks.getFirst()), amount, key);
        long balanceId = ((Number) wallet.get("id")).longValue();
        ledger(balanceId, "DEBIT", "AVAILABLE", "HOLD", amount, id, "withdraw:" + id + ":available");
        ledger(balanceId, "CREDIT", "RESERVED", "HOLD", amount, id, "withdraw:" + id + ":reserved");
        repository.withdrawUpdateStaffBalances(amount, amount, balanceId);
        event(staff, "WITHDRAWAL_REQUESTED", id, "Yêu cầu rút tiền đã được lưu, chờ xét duyệt; chưa chuyển tiền ngân hàng.");
        return Map.of("id", id, "status", "PENDING");
    }

    private void event(long staff, String type, long reference, String message) {
        repository.eventInsertOutboxEvents(staff, type, json.writeValueAsString(Map.of("staffId", staff, "referenceId", reference, "message", message)));
    }

    private void ledger(long balance, String direction, String bucket, String type, long amount, long reference, String key) {
        if (amount > 0)
            repository.ledgerInsertWalletTransactions(balance, direction, bucket, type, amount, reference, key);
    }

    public List<Map<String, Object>> adminBanks(String token) {
        identity.authenticate(token, "ADMIN");
        return withBankNames(repository.adminBanksFindStaffBankAccounts());
    }

    public List<Map<String,Object>> bankHistory(String token,long id){
        identity.authenticate(token,"ADMIN");return repository.bankReviewHistory(id);
    }

    @Transactional
    public Map<String, Object> verifyBank(String token, long id, Map<String, Object> b) {
        var admin=identity.authenticate(token,"ADMIN");
        String state = text(b, "status", 20, true);
        if (!List.of("VERIFIED", "INACTIVE").contains(state))
            throw bad("Chỉ được xác minh hoặc ngừng tài khoản.");
        String note=text(b,"verificationNote",2000,"VERIFIED".equals(state));
        if("VERIFIED".equals(state) && note.length()<8) throw bad("Ghi rõ nguồn đối chiếu số tài khoản và tên người nhận.");
        var rows=repository.bankForReview(id);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy tài khoản ngân hàng.");
        var before=rows.getFirst();long adminId=ServiceHttpClient.userId(admin);
        // Repeating the same submitted decision must not create a second audit or change its timestamp.
        if(state.equals(before.get("status")) && Objects.equals(before.get("verified_by"),adminId) && note.equals(Objects.toString(before.get("verification_note"),"")) && before.get("verified_at")!=null)
            return Map.of("id",id,"status",state);
        repository.insertBankReview(before,adminId,Objects.toString(admin.get("fullName"),""),state,note);
        repository.verifyBankUpdateStaffBankAccounts(state,note,adminId,id);
        return Map.of("id",id,"status",state);
    }

    @Transactional
    public Map<String,Object> cancelWithdrawal(String token,long id){
        long staff=ServiceHttpClient.userId(identity.authenticate(token,"STAFF"));
        var initial=repository.reviewWithdrawalFindWithdrawals(id);
        if(initial.isEmpty() || ((Number)initial.getFirst().get("staff_id")).longValue()!=staff)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy yêu cầu rút của bạn.");
        // Approval, cancellation and payout all lock wallet before withdrawal.
        var wallet=balance(staff,true);
        var rows=repository.reviewWithdrawalFindWithdrawalsForUpdate(id);
        if(rows.isEmpty() || ((Number)rows.getFirst().get("staff_id")).longValue()!=staff)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy yêu cầu rút của bạn.");
        var withdrawal=rows.getFirst();long amount=((Number)withdrawal.get("amount")).longValue();
        if("CANCELLED".equals(withdrawal.get("status")))return Map.of("id",id,"status","CANCELLED","amount",amount);
        if(!"PENDING".equals(withdrawal.get("status")) || repository.withdrawalHasPayout(id))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Chỉ được hủy khi đang chờ Admin duyệt. Yêu cầu đã được xử lý; vui lòng tải lại trạng thái.");
        long balanceId=((Number)wallet.get("id")).longValue();
        if(repository.releaseCancelledWithdrawal(balanceId,staff,amount)!=1)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Khoản giữ chưa khớp. Yêu cầu chưa được hủy; cần kiểm tra số dư.");
        ledger(balanceId,"DEBIT","RESERVED","RELEASE",amount,id,"withdraw:"+id+":release-reserved");
        ledger(balanceId,"CREDIT","AVAILABLE","RELEASE",amount,id,"withdraw:"+id+":release-available");
        if(repository.cancelPendingWithdrawal(id,staff)!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"Trạng thái yêu cầu đã thay đổi.");
        event(staff,"WITHDRAWAL_CANCELLED",id,"Đã hủy yêu cầu rút #"+id+" và hoàn "+amount+"đ về số dư khả dụng.");
        return Map.of("id",id,"status","CANCELLED","amount",amount);
    }

    public List<Map<String, Object>> withdrawals(String token) {
        identity.authenticate(token, "ADMIN");
        return repository.withdrawalsFindWithdrawals();
    }

    @Transactional
    public Map<String, Object> reviewWithdrawal(String token, long id, Map<String, Object> b) {
        long admin = ServiceHttpClient.userId(identity.authenticate(token, "ADMIN"));
        // Use the same wallet -> withdrawal lock order as payout preparation/settlement.
        var beforeReview=repository.reviewWithdrawalFindWithdrawals(id);
        if(beforeReview.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy yêu cầu rút.");
        balance(((Number)beforeReview.getFirst().get("staff_id")).longValue(),true);
        var rows = repository.reviewWithdrawalFindWithdrawalsForUpdate(id);
        if (rows.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu rút.");
        var w = rows.getFirst();
        String next = text(b, "status", 20, true);
        if (next.equals(w.get("status")))
            return Map.of("id", id, "status", next);
        if (!List.of("PENDING", "APPROVED").contains(w.get("status")))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Yêu cầu đã kết thúc.");
        if (!List.of("APPROVED", "REJECTED").contains(next))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chỉ được duyệt hoặc từ chối. Chưa có nhà cung cấp xác minh chuyển tiền nên không được đánh dấu đã chi.");
        if ("REJECTED".equals(next)) {
            String reason = text(b, "reason", 2000, true);
            var wallet = balance(((Number) w.get("staff_id")).longValue(), true);
            long amount = ((Number) w.get("amount")).longValue(), balanceId = ((Number) wallet.get("id")).longValue();
            ledger(balanceId, "DEBIT", "RESERVED", "RELEASE", amount, id, "withdraw:" + id + ":release-reserved");
            ledger(balanceId, "CREDIT", "AVAILABLE", "RELEASE", amount, id, "withdraw:" + id + ":release-available");
            repository.reviewWithdrawalUpdateStaffBalances(amount, amount, balanceId);
            repository.reviewWithdrawalUpdateWithdrawals(reason, admin, id);
        } else
            repository.reviewWithdrawalUpdateWithdrawals2(admin, id);
        event(((Number) w.get("staff_id")).longValue(), "WITHDRAWAL_REVIEWED", id, "Yêu cầu rút tiền: " + next + ". Duyệt yêu cầu chưa đồng nghĩa đã chuyển tiền.");
        return Map.of("id", id, "status", next);
    }

    public List<Map<String, Object>> transactions(String token) {
        identity.authenticate(token, "ADMIN");
        var result = new ArrayList<Map<String, Object>>();
        for (var p : repository.transactionsFindPayments()) {
            result.add(transaction(p, "PAYMENT", "customer_id"));
        }
        for (var w : repository.transactionsFindWithdrawals()) {
            result.add(transaction(w, "PAYOUT", "staff_id"));
        }
        for (var r : repository.transactionsFindRefunds()) {
            result.add(transaction(r, "REFUND", "customer_id"));
        }
        return result;
    }

    Map<String, Object> transaction(Map<String, Object> row, String type, String actor) {
        var result = new LinkedHashMap<String, Object>();
        result.put("id", type + "-" + row.get("id"));
        result.put("recordId", row.get("id"));
        result.put("type", type);
        result.put("bookingId", row.get("booking_id"));
        result.put("actorId", row.get(actor));
        result.put("amount", row.get("amount"));
        result.put("status", List.of("PAID", "PROCESSED").contains(row.get("status")) ? "SUCCESS" : List.of("FAILED", "REJECTED", "CANCELLED").contains(row.get("status")) ? "FAILED" : "PENDING");
        result.put("rawStatus", row.get("status"));
        result.put("paymentMethod", Objects.equals(row.get("payment_method"), "CASH") ? "CASH" : "BANK_TRANSFER");
        result.put("platformFee", 0);
        result.put("createdAt", row.get("created_at"));
        result.put("description", Objects.toString(row.get("admin_note"), ""));
        return result;
    }

    @Transactional
    public Map<String, Object> note(String token, String type, long id, Map<String, Object> b) {
        identity.authenticate(token, "ADMIN");
        String table = type.equals("PAYMENT") ? "payments" : type.equals("REFUND") ? "refunds" : null;
        if (table == null)
            throw bad("Giao dịch rút tiền phải xử lý qua yêu cầu rút.");
        var rows = repository.noteFindWHEREForUpdate(table, id);
        if (rows.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy giao dịch.");
        String current = List.of("PAID", "PROCESSED").contains(rows.getFirst().get("status")) ? "SUCCESS" : List.of("FAILED", "REJECTED", "CANCELLED").contains(rows.getFirst().get("status")) ? "FAILED" : "PENDING";
        if (!current.equals(b.get("status")))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Trạng thái tiền phải được xác nhận bởi nghiệp vụ thanh toán. Chỉ được lưu ghi chú đối soát tại đây.");
        repository.noteUpdateSET(table, text(b, "description", 2000, false), id);
        return Map.of("id", id, "status", current);
    }

    public List<Map<String, Object>> invoices(String token) {
        var user = identity.authenticate(token, "ADMIN", "CUSTOMER");
        return repository.invoicesFindInvoices(user, "CUSTOMER".equals(user.get("role")) ? new Object[] { user.get("id") } : new Object[] {});
    }

    public Map<String, Object> paymentProvider(String token, long id) {
        var user = identity.authenticate(token, "CUSTOMER");
        var b = booking.get("/api/v1/booking/bookings/" + id, token);
        if (((Number) b.get("customerId")).longValue() != ServiceHttpClient.userId(user))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn của bạn.");
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Chưa cấu hình nhà cung cấp PayOS/VietQR để tạo QR và xác minh giao dịch thật.");
    }

    public Map<String, Object> policy(String token) {
        identity.authenticate(token, "ADMIN");
        var rows = repository.policyFindFinanceSettings();
        return Map.of("settings", rows);
    }

    @Transactional
    public Map<String, Object> savePolicy(String token, Map<String, Object> b) {
        long admin = ServiceHttpClient.userId(identity.authenticate(token, "ADMIN"));
        long percent = number(b, "platformFeePercent", 0, 100);
        repository.savePolicyInsertFinanceSettings(percent, admin);
        return policy(token);
    }

    @Transactional
    public Map<String, Object> cashReceipt(String token, long id, Map<String, Object> b) {
        identity.authenticate(token, "ADMIN");
        if (!Boolean.TRUE.equals(b.get("cashReceived")))
            throw bad("Cần xác nhận đã thực nhận tiền mặt.");
        var book = booking.get("/api/v1/booking/bookings/" + id, token);
        if (!"CASH".equals(book.get("paymentMethod")) || List.of("CANCELLED", "REFUNDED").contains(book.get("status")))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đơn không thuộc luồng thu tiền mặt còn hiệu lực.");
        repository.cashReceiptLockRecords(id);
        var old = repository.cashReceiptFindPayments(id);
        if (!old.isEmpty())
            return Map.of("id", old.getFirst().get("id"), "status", "PAID");
        long amount = ((Number) book.get("totalAmount")).longValue();
        if (amount <= 0)
            throw bad("Đơn không có số tiền cần thu.");
        long payment = repository.cashReceiptInsertPayments(id, book.get("customerId"), "CASH-" + UUID.randomUUID(), amount, "Admin xác nhận đã thực nhận tiền mặt.");
        repository.cashReceiptInsertOutboxEvents(payment, json.writeValueAsString(Map.of("bookingId", id)));
        return Map.of("id", payment, "status", "PAID");
    }

    public void syncPayment(String token, long id) {
        booking.call("POST", "/api/v1/booking/admin/bookings/" + id + "/verify-payment", token, Map.of());
    }

    public Map<String, Object> paymentStatus(String token, long id) {
        identity.authenticate(token, "ADMIN", "CUSTOMER", "STAFF");
        booking.get("/api/v1/booking/bookings/" + id, token);
        long paid = repository.paymentStatusFindPayments(Long.class, id);
        long refunded = repository.paymentStatusFindRefunds(Long.class, id);
        return Map.of("paidAmount", paid, "refundedAmount", refunded);
    }

    @SuppressWarnings("unchecked")
    @Transactional
    public Map<String, Object> settle(String token, long id) {
        identity.authenticate(token, "ADMIN");
        var book = booking.get("/api/v1/booking/bookings/" + id, token);
        if (!"COMPLETED".equals(book.get("status")))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chỉ lập quyết toán và hóa đơn sau khi đơn hoàn thành.");
        repository.settleLockRecords(id);
        var invoices = repository.settleFindInvoices(Long.class, id);
        if (!invoices.isEmpty())
            return Map.of("invoiceId", invoices.getFirst(), "status", repository.settleFindInvoices2(String.class, invoices.getFirst()));
        long total = ((Number) book.get("totalAmount")).longValue();
        long paid = repository.settleFindPayments(Long.class, id);
        if (paid < total)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chưa xác minh đủ số tiền thanh toán trong Finance.");
        var settings = repository.settleFindFinanceSettings(java.math.BigDecimal.class);
        if (settings.isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Admin cần lưu chính sách phí hệ thống trước khi quyết toán.");
        var people = ((List<Map<String, Object>>) book.get("assignments")).stream().filter(a -> "COMPLETED".equals(a.get("status"))).map(a -> ((Number) a.get("staffId")).longValue()).distinct().sorted().toList();
        if (people.size() != ((Number) book.get("requiredStaffCount")).intValue())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chưa đủ nhân viên hoàn thành để quyết toán.");
        long base = ((Number) book.get("packagePrice")).longValue(), addons = ((Number) book.get("addOnsTotal")).longValue(), discount = ((Number) book.get("discountAmount")).longValue();
        long order = repository.settleInsertOrders(id, "ORDER-" + UUID.randomUUID(), base, addons, discount, total, book.get("paymentMethod"));
        String serviceName = Objects.toString(((Map<String, Object>) book.get("service")).get("name"), "Dịch vụ");
        repository.settleInsertOrderDetails(order, serviceName, base, base);
        if (addons > 0)
            repository.settleInsertOrderDetails2(order, addons, addons);
        long invoice = repository.settleInsertInvoices(order, id, book.get("customerId"), "INV-" + UUID.randomUUID(), json.writeValueAsString(book.get("address")), json.writeValueAsString(book.get("service")), base + addons, discount, total);
        repository.settleInsertInvoiceItems(invoice, serviceName, base + addons, base + addons);
        double hours = java.time.Duration.between(java.time.Instant.parse((String) book.get("startsAt")), java.time.Instant.parse((String) book.get("endsAt"))).toMinutes() / 60.0;
        for (int index = 0; index < people.size(); index++) {
            long staff = people.get(index), share = total / people.size() + (index < total % people.size() ? 1 : 0);
            long fee = java.math.BigDecimal.valueOf(share).multiply(settings.getFirst()).divide(java.math.BigDecimal.valueOf(100), 0, java.math.RoundingMode.DOWN).longValueExact();
            var wallet = balance(staff, true);
            long balanceId = ((Number) wallet.get("id")).longValue();
            long income = repository.settleInsertStaffIncomes(staff, id, balanceId, hours, share, fee, share - fee);
            ledger(balanceId, "CREDIT", "AVAILABLE", "EARNING", share - fee, income, "booking:" + id + ":staff:" + staff + ":earning");
            repository.settleUpdateStaffBalances(share - fee, share - fee, balanceId);
        }
        repository.settleUpdatePayments(order, id);
        for (long person : people) event(person, "EARNING_SETTLED", id, "Thu nhập đơn #" + id + " đã được quyết toán. Xem chi tiết tại ví nhân viên.");
        return Map.of("invoiceId", invoice, "status", "PAID");
    }

    public List<Map<String, Object>> adjustments(String token) {
        identity.authenticate(token, "ADMIN");
        return Map.of("rewards", repository.adjustmentsFindRewards(), "penalties", repository.adjustmentsFindPenalties()).entrySet().stream().flatMap(e -> e.getValue().stream().map(r -> {
            r.put("kind", e.getKey().equals("rewards") ? "REWARD" : "PENALTY");
            return r;
        })).toList();
    }

    @Transactional
    public Map<String, Object> requestReward(String token, long staff, Map<String, Object> b) {
        identity.authenticate(token, "ADMIN");
        String period = Objects.toString(b.get("period"), "CURRENT");
        if (!List.of("CURRENT", "PREVIOUS").contains(period))
            throw bad("Kỳ thưởng không hợp lệ.");
        var operations = booking.get("/api/v1/booking/admin/staff/" + staff + "/operations?period=" + period, token);
        var wallet = balance(staff, true);
        var date = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        var from = date.withDayOfMonth(1).minusMonths(period.equals("PREVIOUS") ? 1 : 0);
        var to = from.plusMonths(1).minusDays(1);
        long monthlyIncome = repository.requestRewardFindStaffIncomes(Long.class, staff, from, to);
        List<Long> ids = new ArrayList<>();
        for (String kind : List.of("tripsBonus", "incomeBonus", "ratingBonus")) {
            if (!Boolean.TRUE.equals(b.get(kind)))
                continue;
            boolean qualified = kind.equals("tripsBonus") ? ((Number) operations.get("completedBookings")).longValue() >= 150 : kind.equals("incomeBonus") ? monthlyIncome >= 20000000 : ((Number) operations.get("averageRating")).doubleValue() >= 4.9 && ((Number) operations.get("reviewCount")).intValue() > 0;
            if (!qualified)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Nhân viên chưa đạt điều kiện thưởng " + kind + " theo dữ liệu thật.");
            var prior = repository.requestRewardFindRewards(Long.class, staff, kind, from, to);
            if (!prior.isEmpty()) {
                ids.add(prior.getFirst());
                continue;
            }
            long amount = kind.equals("tripsBonus") ? 500000 : kind.equals("incomeBonus") ? 1000000 : 300000;
            ids.add(repository.requestRewardInsertRewards(staff, kind, from, to, monthlyIncome, amount, "Đề xuất thưởng theo mốc KPI, chờ phê duyệt."));
        }
        if (ids.isEmpty())
            throw bad("Vui lòng chọn ít nhất một khoản thưởng đủ điều kiện.");
        return Map.of("ids", ids, "status", "PENDING");
    }

    @Transactional
    public Map<String, Object> requestPenalty(String token, long staff, Map<String, Object> b) {
        identity.authenticate(token, "ADMIN");
        booking.get("/api/v1/booking/admin/staff/" + staff + "/operations", token);
        balance(staff, true);
        long amount = number(b, "amount", 1, 1000000000);
        String reason = text(b, "reason", 2000, true), key = text(b, "requestKey", 160, true);
        var prior = repository.requestPenaltyFindPenalties(staff, key);
        if (!prior.isEmpty()) {
            var old = prior.getFirst();
            if (((Number) old.get("amount")).longValue() != amount || !reason.equals(old.get("reason")))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Đề xuất phạt đã tồn tại với nội dung khác.");
            return Map.of("id", old.get("id"), "status", "PENDING");
        }
        return Map.of("id", repository.requestPenaltyInsertPenalties(staff, amount, reason, key), "status", "PENDING");
    }

    @Transactional
    public Map<String, Object> applyAdjustment(String token, String kind, long id) {
        identity.authenticate(token, "ADMIN");
        String table = kind.equals("REWARD") ? "rewards" : kind.equals("PENALTY") ? "penalties" : null;
        if (table == null)
            throw bad("Loại điều chỉnh không hợp lệ.");
        var list = repository.applyAdjustmentFindWHEREForUpdate(table, id);
        if (list.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đề xuất.");
        var entry = list.getFirst();
        if ("APPLIED".equals(entry.get("status")))
            return Map.of("id", id, "status", "APPLIED");
        if (!"PENDING".equals(entry.get("status")))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đề xuất không còn chờ duyệt.");
        var wallet = balance(((Number) entry.get("staff_id")).longValue(), true);
        long amount = ((Number) entry.get("amount")).longValue(), balanceId = ((Number) wallet.get("id")).longValue();
        if (kind.equals("PENALTY") && ((Number) wallet.get("available_balance")).longValue() < amount)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Số dư không đủ; giữ đề xuất phạt ở trạng thái chờ xử lý.");
        ledger(balanceId, kind.equals("REWARD") ? "CREDIT" : "DEBIT", "AVAILABLE", kind, amount, id, kind + ":" + id + ":apply");
        repository.applyAdjustmentUpdateStaffBalances(kind.equals("REWARD") ? amount : -amount, balanceId);
        repository.applyAdjustmentUpdateSET(table, id);
        event(((Number) entry.get("staff_id")).longValue(), kind + "_APPLIED", id, "Khoản điều chỉnh " + kind + " đã được ghi sổ: " + amount + " VNĐ.");
        return Map.of("id", id, "status", "APPLIED");
    }
}
