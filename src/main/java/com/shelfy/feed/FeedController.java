package com.shelfy.feed;

import com.shelfy.feed.dto.FeedItemResponse;
import com.shelfy.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/feed")
@RequiredArgsConstructor
public class FeedController {

    private static final int MAX_LIMIT = 50;

    private final FeedService feedService;

    @GetMapping
    public List<FeedItemResponse> get(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false, defaultValue = "20") int limit
    ) {
        int boundedLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
        return feedService.getFeed(principal.getId(), boundedLimit);
    }
}
