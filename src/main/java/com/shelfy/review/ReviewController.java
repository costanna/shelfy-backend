package com.shelfy.review;

import com.shelfy.review.dto.ReviewRequest;
import com.shelfy.review.dto.ReviewResponse;
import com.shelfy.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/books/{bookId}/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping
    public List<ReviewResponse> list(@AuthenticationPrincipal UserPrincipal principal,
                                     @PathVariable Long bookId) {
        return reviewService.listByBook(principal.getId(), bookId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse create(@AuthenticationPrincipal UserPrincipal principal,
                                 @PathVariable Long bookId,
                                 @Valid @RequestBody ReviewRequest request) {
        return reviewService.create(principal.getId(), bookId, request);
    }

    @PutMapping("/{reviewId}")
    public ReviewResponse update(@AuthenticationPrincipal UserPrincipal principal,
                                 @PathVariable Long bookId,
                                 @PathVariable Long reviewId,
                                 @Valid @RequestBody ReviewRequest request) {
        return reviewService.update(principal.getId(), bookId, reviewId, request);
    }

    @DeleteMapping("/{reviewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserPrincipal principal,
                       @PathVariable Long bookId,
                       @PathVariable Long reviewId) {
        reviewService.delete(principal.getId(), bookId, reviewId);
    }
}
