package com.shelfy.user;

import com.shelfy.book.Book;
import com.shelfy.book.BookRepository;
import com.shelfy.category.CategoryMapper;
import com.shelfy.common.exception.ResourceNotFoundException;
import com.shelfy.follow.FollowService;
import com.shelfy.review.Review;
import com.shelfy.review.ReviewRepository;
import com.shelfy.user.dto.PublicBookResponse;
import com.shelfy.user.dto.PublicReviewResponse;
import com.shelfy.user.dto.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final ReviewRepository reviewRepository;
    private final CategoryMapper categoryMapper;
    private final FollowService followService;

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long viewerId, Long targetUserId) {
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", targetUserId));

        boolean own = viewerId.equals(targetUserId);
        boolean followedByMe = own || followService.isFollowing(viewerId, targetUserId);
        boolean visible = own || followedByMe;

        return new UserProfileResponse(
                target.getId(),
                target.getAlias(),
                target.getName(),
                followService.followersCount(targetUserId),
                followService.followingCount(targetUserId),
                followedByMe,
                own,
                visible,
                visible ? books(targetUserId) : List.of()
        );
    }

    private List<PublicBookResponse> books(Long userId) {
        List<Book> books = bookRepository.findByOwnerId(userId);

        Map<Long, List<PublicReviewResponse>> reviewsByBookId = reviewRepository.findByUserId(userId).stream()
                .collect(Collectors.groupingBy(
                        review -> review.getBook().getId(),
                        Collectors.mapping(this::toReviewResponse, Collectors.toList())
                ));

        return books.stream()
                .sorted(Comparator.comparing(Book::getCreatedAt).reversed())
                .map(book -> toBookResponse(book, reviewsByBookId.getOrDefault(book.getId(), List.of())))
                .toList();
    }

    private PublicBookResponse toBookResponse(Book book, List<PublicReviewResponse> reviews) {
        return new PublicBookResponse(
                book.getId(),
                book.getTitle(),
                book.getAuthor(),
                book.getCoverUrl(),
                book.getSynopsis(),
                book.getPageCount(),
                book.getStatus(),
                book.getCategories().stream()
                        .sorted(Comparator.comparing(c -> c.getName().toLowerCase()))
                        .map(categoryMapper::toResponse)
                        .toList(),
                reviews
        );
    }

    private PublicReviewResponse toReviewResponse(Review review) {
        return new PublicReviewResponse(
                review.getId(),
                review.getRating(),
                review.getText(),
                review.getCreatedAt()
        );
    }
}
