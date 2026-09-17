package com.shelfy.feed;

import com.shelfy.book.Book;
import com.shelfy.book.BookRepository;
import com.shelfy.book.BookStatus;
import com.shelfy.feed.dto.FeedItemResponse;
import com.shelfy.feed.dto.FeedItemType;
import com.shelfy.follow.FollowRepository;
import com.shelfy.review.Review;
import com.shelfy.review.ReviewRepository;
import com.shelfy.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Builds a lightweight "what people you follow have been up to" feed by merging their most
 * recent status changes and reviews in memory, rather than maintaining a separate event log —
 * same trade-off StatsService makes: simple and fast enough for the size of a personal library
 * app. As a consequence, a book's "started/finished reading" timestamp is its updatedAt, so any
 * edit to a book already in that status (fixing a typo, say) can resurface it in the feed.
 */
@Service
@RequiredArgsConstructor
public class FeedService {

    private final FollowRepository followRepository;
    private final BookRepository bookRepository;
    private final ReviewRepository reviewRepository;

    @Transactional(readOnly = true)
    public List<FeedItemResponse> getFeed(Long userId, int limit) {
        List<Long> followedIds = followRepository.findByFollowerIdOrderByCreatedAtDesc(userId).stream()
                .map(follow -> follow.getFollowed().getId())
                .toList();

        if (followedIds.isEmpty()) {
            return List.of();
        }

        Pageable fetchPage = PageRequest.of(0, Math.max(limit, 1));
        List<FeedItemResponse> items = new ArrayList<>();

        bookRepository.findByOwnerIdInAndStatusOrderByUpdatedAtDesc(followedIds, BookStatus.READING, fetchPage)
                .forEach(book -> items.add(toBookItem(book, FeedItemType.STARTED_READING)));

        bookRepository.findByOwnerIdInAndStatusOrderByUpdatedAtDesc(followedIds, BookStatus.READ, fetchPage)
                .forEach(book -> items.add(toBookItem(book, FeedItemType.FINISHED_READING)));

        reviewRepository.findByUserIdInOrderByCreatedAtDesc(followedIds, fetchPage)
                .forEach(review -> items.add(toReviewItem(review)));

        return items.stream()
                .sorted(Comparator.comparing(FeedItemResponse::occurredAt).reversed())
                .limit(limit)
                .toList();
    }

    private FeedItemResponse toBookItem(Book book, FeedItemType type) {
        User owner = book.getOwner();
        return new FeedItemResponse(
                type,
                owner.getId(),
                owner.getAlias(),
                owner.getName(),
                book.getId(),
                book.getTitle(),
                book.getCoverUrl(),
                null,
                book.getUpdatedAt()
        );
    }

    private FeedItemResponse toReviewItem(Review review) {
        User user = review.getUser();
        Book book = review.getBook();
        return new FeedItemResponse(
                FeedItemType.REVIEWED,
                user.getId(),
                user.getAlias(),
                user.getName(),
                book.getId(),
                book.getTitle(),
                book.getCoverUrl(),
                review.getRating(),
                review.getCreatedAt()
        );
    }
}
