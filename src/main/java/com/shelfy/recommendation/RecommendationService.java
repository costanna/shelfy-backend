package com.shelfy.recommendation;

import com.shelfy.book.Book;
import com.shelfy.book.BookRepository;
import com.shelfy.book.BookStatus;
import com.shelfy.follow.FollowRepository;
import com.shelfy.recommendation.dto.RecommendationItemResponse;
import com.shelfy.recommendation.dto.RecommendationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Recomanacions socials: llibres que ha acabat la gent que segueixes i que tu
 * encara no tens, ordenats per quants seguids els han llegit. També retorna
 * l'autor més llegit per si el frontend vol completar amb el catàleg obert
 * (Open Library) quan el cercle social no dona prou material.
 */
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private static final int MAX_ITEMS = 10;

    private final BookRepository bookRepository;
    private final FollowRepository followRepository;

    @Transactional(readOnly = true)
    public RecommendationResponse getRecommendations(Long ownerId) {
        List<Book> ownRead = bookRepository.findByOwnerIdInAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
                List.of(ownerId), BookStatus.READ);

        String topAuthor = topAuthor(ownRead);
        Set<String> ownedKeys = new HashSet<>();
        for (Book book : bookRepository.findByOwnerIdAndDeletedAtIsNull(ownerId)) {
            ownedKeys.add(key(book.getTitle(), book.getAuthor()));
        }

        List<Long> followedIds = followRepository.findByFollowerIdOrderByCreatedAtDesc(ownerId).stream()
                .map(follow -> follow.getFollowed().getId())
                .toList();

        List<RecommendationItemResponse> items = List.of();
        if (!followedIds.isEmpty()) {
            items = rankFollowedReads(
                    bookRepository.findByOwnerIdInAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
                            followedIds, BookStatus.READ),
                    ownedKeys);
        }

        return new RecommendationResponse(topAuthor, items);
    }

    private List<RecommendationItemResponse> rankFollowedReads(List<Book> followedReads, Set<String> ownedKeys) {
        Map<String, Accumulator> byKey = new HashMap<>();
        for (Book book : followedReads) {
            if (book.getTitle() == null || book.getTitle().isBlank()) {
                continue;
            }
            String bucket = key(book.getTitle(), book.getAuthor());
            if (ownedKeys.contains(bucket)) {
                continue;
            }
            Accumulator acc = byKey.computeIfAbsent(bucket,
                    k -> new Accumulator(book.getTitle().trim(), trimToNull(book.getAuthor()), book.getCoverUrl()));
            acc.readers++;
            if (acc.coverUrl == null && book.getCoverUrl() != null) {
                acc.coverUrl = book.getCoverUrl();
            }
        }

        List<Accumulator> sorted = new ArrayList<>(byKey.values());
        sorted.sort(Comparator.comparingLong((Accumulator a) -> a.readers).reversed());

        return sorted.stream()
                .limit(MAX_ITEMS)
                .map(acc -> new RecommendationItemResponse(acc.title, acc.author, acc.coverUrl, acc.readers))
                .toList();
    }

    private String topAuthor(List<Book> readBooks) {
        Map<String, Long> counts = new HashMap<>();
        Map<String, String> displayName = new HashMap<>();
        for (Book book : readBooks) {
            String author = trimToNull(book.getAuthor());
            if (author == null) {
                continue;
            }
            String norm = author.toLowerCase(Locale.ROOT);
            counts.merge(norm, 1L, Long::sum);
            displayName.putIfAbsent(norm, author);
        }
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(entry -> displayName.get(entry.getKey()))
                .orElse(null);
    }

    private static String key(String title, String author) {
        return (title == null ? "" : title.trim().toLowerCase(Locale.ROOT))
                + "\u0000"
                + (author == null ? "" : author.trim().toLowerCase(Locale.ROOT));
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static final class Accumulator {
        private final String title;
        private final String author;
        private String coverUrl;
        private long readers;

        private Accumulator(String title, String author, String coverUrl) {
            this.title = title;
            this.author = author;
            this.coverUrl = coverUrl;
        }
    }
}
