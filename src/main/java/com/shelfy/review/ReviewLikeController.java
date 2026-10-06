package com.shelfy.review;

import com.shelfy.review.dto.ReviewResponse;
import com.shelfy.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews/{reviewId}/like")
@RequiredArgsConstructor
public class ReviewLikeController {

    private final ReviewService reviewService;

    @PostMapping
    public ReviewResponse like(@AuthenticationPrincipal UserPrincipal principal,
                               @PathVariable Long reviewId) {
        return reviewService.like(principal.getId(), reviewId);
    }

    @DeleteMapping
    public ReviewResponse unlike(@AuthenticationPrincipal UserPrincipal principal,
                                 @PathVariable Long reviewId) {
        return reviewService.unlike(principal.getId(), reviewId);
    }
}
