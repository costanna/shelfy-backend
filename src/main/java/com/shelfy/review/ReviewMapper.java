package com.shelfy.review;

import com.shelfy.review.dto.ReviewResponse;
import org.springframework.stereotype.Component;

@Component
public class ReviewMapper {

    public ReviewResponse toResponse(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getRating(),
                review.getText(),
                review.getCreatedAt(),
                review.getBook().getId(),
                review.getUser().getName()
        );
    }
}
