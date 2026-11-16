package com.domesticservice.ai.service;

import java.text.Normalizer;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class AiCustomerKnowledge {
    private static final Set<String> COMMON_WORDS = Set.of("cleanmaster", "toi", "ban", "minh", "nao",
        "gi", "khong", "nhu", "the", "co", "cho", "cua", "duoc", "nen", "can", "nhieu", "lam",
        "ung", "dung", "mot", "khi", "trong", "qua", "nhung", "cac", "voi", "bao", "xem", "va", "hay", "vay", "nhi");
    private final JdbcTemplate db;
    private final OllamaEmbeddings embeddings;
    private final ObjectMapper json;

    @Autowired
    AiCustomerKnowledge(JdbcTemplate db, OllamaEmbeddings embeddings, ObjectMapper json) {
        this.db = db;
        this.embeddings = embeddings;
        this.json = json;
    }

    List<Article> find(String question) {
        return search(question).articles();
    }

    public Search search(String question) {
        var terms = new HashSet<>(Arrays.asList(normalize(question).split(" ")));
        terms.removeIf(term -> term.length() < 2 || COMMON_WORDS.contains(term));
        if (terms.isEmpty()) return new Search("KEYWORD", embeddings.model, List.of());
        var rows = db.query("""
            SELECT d.id,d.version,d.title,d.source_uri,c.content,c.embedding::text,c.embedding_model,d.index_status
            FROM knowledge_documents d
            JOIN knowledge_chunks c ON c.document_id=d.id
            WHERE d.status='ACTIVE' AND d.approval_status='APPROVED' AND c.document_version=d.version
            ORDER BY d.id,c.chunk_index
            """, (row, index) -> new Candidate(row.getLong("id"), row.getLong("version"), row.getString("title"),
                row.getString("source_uri"), row.getString("content"), row.getString("embedding"),
                embeddings.model.equals(row.getString("embedding_model")) && "READY".equals(row.getString("index_status"))));
        List<Double> query = List.of();
        String mode = "KEYWORD";
        if (rows.stream().anyMatch(Candidate::indexed)) {
            try { query = embeddings.query(question); mode = "HYBRID"; }
            catch (RuntimeException unavailable) { mode = "KEYWORD_FALLBACK"; }
        }
        var matches = new ArrayList<Article>();
        for (var row : rows) {
            int lexical = score(terms, row.title(), row.content());
            double similarity = row.indexed() && !query.isEmpty() ? cosine(query, row.embedding()) : 0;
            if (query.isEmpty() ? lexical < 2 : similarity < 0.5 && lexical < 4) continue;
            double rank = query.isEmpty() ? lexical : 0.8 * similarity + 0.2 * Math.min(1, lexical / 12.0);
            matches.add(new Article(row.id(), row.version(), row.title(), row.content(), row.source(), rank, similarity));
        }
        matches.sort(Comparator.comparingDouble(Article::score).reversed().thenComparingLong(Article::documentId));
        var documents = new LinkedHashMap<Long, Article>();
        for (var match : matches) documents.putIfAbsent(match.documentId(), match);
        return new Search(mode, embeddings.model, documents.values().stream().limit(3).toList());
    }

    boolean areCurrent(List<Article> articles) {
        return articles.stream().allMatch(article -> Boolean.TRUE.equals(db.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM knowledge_documents
            WHERE id=? AND version=? AND status='ACTIVE' AND approval_status='APPROVED')
            """, Boolean.class, article.documentId(), article.version())));
    }

    private double cosine(List<Double> query, String raw) {
        if (raw == null) return 0;
        try {
            List<Double> vector = json.readValue(raw, new TypeReference<>() {});
            if (vector.size() != query.size()) return 0;
            double dot = 0, norm = 0;
            for (int index = 0; index < vector.size(); index++) {
                double value = vector.get(index);
                if (!Double.isFinite(value)) return 0;
                dot += query.get(index) * value;
                norm += value * value;
            }
            return norm > 0 ? dot / Math.sqrt(norm) : 0;
        } catch (RuntimeException invalid) { return 0; }
    }

    private static int score(Set<String> terms, String articleTitle, String articleContent) {
        var title = new HashSet<>(Arrays.asList(normalize(articleTitle).split(" ")));
        var content = new HashSet<>(Arrays.asList(normalize(articleContent).split(" ")));
        return terms.stream().mapToInt(term -> title.contains(term) ? 3 : content.contains(term) ? 1 : 0).sum();
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text.toLowerCase(Locale.ROOT).replace('đ', 'd'), Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "").replaceAll("[^a-z0-9]+", " ").strip();
    }

    public record Article(long documentId, long version, String title, String content, String sourceUri,
                          double score, double similarity) {}
    public record Search(String mode, String embeddingModel, List<Article> articles) {}
    private record Candidate(long id, long version, String title, String source, String content, String embedding, boolean indexed) {}
}
