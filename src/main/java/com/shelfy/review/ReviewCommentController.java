package com.shelfy.review;

import com.shelfy.review.dto.ReviewCommentRequest;
import com.shelfy.review.dto.ReviewCommentResponse;
import com.shelfy.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reviews/{reviewId}/comments")
@RequiredArgsConstructor
public class ReviewCommentController {

    private final ReviewService reviewService;

    @GetMapping
    public List<ReviewCommentResponse> list(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable Long reviewId) {
        return reviewService.comments(principal.getId(), reviewId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewCommentResponse create(@AuthenticationPrincipal UserPrincipal principal,
                                        @PathVariable Long reviewId,
                                        @Valid @RequestBody ReviewCommentRequest request) {
        return reviewService.comment(principal.getId(), reviewId, request);
    }

    @DeleteMapping("/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserPrincipal principal,
                       @PathVariable Long reviewId,
                       @PathVariable Long commentId) {
        reviewService.deleteComment(principal.getId(), reviewId, commentId);
    }
}
