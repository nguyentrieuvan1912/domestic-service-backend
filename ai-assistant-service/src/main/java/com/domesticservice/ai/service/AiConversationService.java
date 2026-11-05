package com.domesticservice.ai.service;

import com.domesticservice.common.web.ServiceHttpClient;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.domesticservice.ai.repository.AiConversationSqlRepository;

@Service
public class AiConversationService {

    private final AiConversationSqlRepository repository;

    private final ServiceHttpClient identity;

    private final AiProvider provider;
    private final TransactionTemplate transactions;
    private final AiBookingDraftService drafts;

    public AiConversationService(AiConversationSqlRepository repository, AiProvider provider,
                                 PlatformTransactionManager manager, AiBookingDraftService drafts,
                                 @Value("${clients.identity.base-url}") String identityUrl) {
        this.repository = repository;
        this.provider = provider;
        this.drafts = drafts;
        this.transactions = new TransactionTemplate(manager);
        identity = new ServiceHttpClient(identityUrl);
    }

    private long actor(String token) {
        return ServiceHttpClient.userId(identity.authenticate(token, "CUSTOMER"));
    }

    private void owned(long user, long id, boolean lock) {
        if (repository.ownedFindAiConversationsForUpdate(lock, id, user).isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hội thoại của tài khoản này.");
    }

    public List<Map<String, Object>> conversations(String token) {
        return repository.conversationsFindAiConversations(actor(token));
    }

    @Transactional
    public Map<String, Object> create(String token) {
        long id = repository.createFindAiConversations(Long.class, actor(token));
        return Map.of("id", id);
    }

    public List<Map<String, Object>> messages(String token, long id) {
        owned(actor(token), id, false);
        return repository.messagesFindAiMessages(id);
    }

    private record Turn(Long responseId, List<Map<String, Object>> context, AiBookingDraftService.Patch draft) {}

    public Map<String, Object> send(String token, long id, Map<String, Object> body) {
        long user = actor(token);
        String text = Objects.toString(body.get("content"), "").strip();
        String key = Objects.toString(body.get("clientKey"), "");
        if (text.isEmpty() || text.length() > 4000 || key.isBlank() || key.length() > 160) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nội dung hoặc mã yêu cầu không hợp lệ.");
        }
        String attempt = UUID.randomUUID().toString();
        Turn turn = transactions.execute(status -> prepare(user, id, key, text, attempt));
        if (turn.responseId() != null) return sentTurn(id, key, turn.responseId());

        try {
            var reply = provider.reply(turn.context(), token);
            var draftReply = prepareDraftReply(token, user, id, turn.draft(), reply);
            return transactions.execute(status -> {
                owned(user, id, true);
                requireActive(id);
                if (!repository.ownsTurn(id, key, attempt)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Lượt gửi đã hết hạn. Vui lòng thử lại.");
                }
                long userMessage = repository.sendFindAiMessages3(Long.class, id, text, key);
                var savedDraft = draftReply.prepared() == null ? null : drafts.save(id, user, draftReply.prepared());
                long replyId = repository.sendFindAiMessages4(Long.class, id, draftReply.text());
                if (draftReply.receipt() != null) {
                    var receipt = draftReply.receipt();
                    repository.saveToolReceipt(userMessage, receipt.name(), receipt.status(),
                        receipt.httpStatus(), receipt.elapsedMillis());
                }
                for (var receipt : reply.tools()) {
                    repository.saveToolReceipt(userMessage, receipt.name(), receipt.status(),
                        receipt.httpStatus(), receipt.elapsedMillis());
                }
                repository.completeTurn(id, key, attempt, replyId);
                var result = sentTurn(id, key, replyId);
                if (savedDraft != null) result.put("bookingDraft", savedDraft);
                return result;
            });
        } catch (RuntimeException failure) {
            try {
                transactions.executeWithoutResult(status -> repository.releaseTurn(id, key, attempt));
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    private record DraftReply(String text, AiBookingDraftService.Prepared prepared, AiProvider.ToolReceipt receipt) {}

    private DraftReply prepareDraftReply(String token, long user, long id, AiBookingDraftService.Patch snapshot,
                                         AiProvider.Reply reply) {
        if (reply.draftChanges().isEmpty()) return new DraftReply(reply.text(), null, null);
        long started = System.nanoTime();
        var patch = new AiBookingDraftService.Patch(snapshot.draftId(), snapshot.version(), reply.draftChanges());
        try {
            var prepared = drafts.prepare(token, id, user, patch, true);
            return new DraftReply(drafts.progress(prepared), prepared, draftReceipt("SUCCEEDED", 200, started));
        } catch (ResponseStatusException failure) {
            int code = failure.getStatusCode().value();
            if (code != 400 && code != 404) throw failure;
            return new DraftReply("Mình chưa lưu thay đổi. " + failure.getReason()
                + " Bạn kiểm tra và gửi lại thông tin cụ thể nhé.", null, draftReceipt("FAILED", code, started));
        }
    }

    private static AiProvider.ToolReceipt draftReceipt(String status, int code, long started) {
        return new AiProvider.ToolReceipt("propose_booking_draft", status, code,
            java.time.Duration.ofNanos(System.nanoTime() - started).toMillis());
    }

    private Map<String, Object> sentTurn(long conversation, String key, long reply) {
        var result = new LinkedHashMap<String, Object>();
        result.put("id", reply);
        result.put("messages", repository.turnMessages(conversation, key, reply));
        return result;
    }

    private Turn prepare(long user, long id, String key, String text, String attempt) {
        owned(user, id, true);
        requireActive(id);
        String hash = digest(text);
        var previous = repository.findTurn(id, key);
        if (!previous.isEmpty()) {
            var turn = previous.getFirst();
            if (!hash.equals(turn.get("request_hash"))) throw reusedKey();
            if (turn.get("response_id") instanceof Number reply) return new Turn(reply.longValue(), List.of(), null);
        }
        var legacy = repository.sendFindAiMessages(id, key);
        if (!legacy.isEmpty()) {
            if (!text.equals(legacy.getFirst().get("content"))) throw reusedKey();
            return new Turn(((Number) legacy.getFirst().get("id")).longValue(), List.of(), null);
        }
        if (repository.hasRunningTurn(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đang xử lý một tin nhắn. Vui lòng thử lại.");
        }
        repository.startTurn(id, key, hash, attempt);
        var context = new ArrayList<>(repository.sendFindAiMessages2(id));
        Collections.reverse(context);
        var draft = drafts.snapshot(id, user);
        if (!draft.changes().isEmpty()) {
            context.addFirst(Map.of("role", "SYSTEM", "content", draft.changes()));
        }
        context.add(Map.of("role", "USER", "content", text));
        return new Turn(null, context, draft);
    }

    private void requireActive(long id) {
        if (!Boolean.TRUE.equals(repository.sendExistsAiConversations(Boolean.class, id))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hội thoại đã kết thúc.");
        }
    }

    private static ResponseStatusException reusedKey() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Mã yêu cầu đã dùng với nội dung khác.");
    }

    private static String digest(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Transactional
    public void clear(String token, long id) {
        long user = actor(token);
        owned(user, id, true);
        repository.clearConversationData(id, user);
    }
}
