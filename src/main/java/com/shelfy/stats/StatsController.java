package com.shelfy.stats;

import com.shelfy.security.UserPrincipal;
import com.shelfy.stats.dto.ReadingStatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping
    public ReadingStatsResponse getStats(@AuthenticationPrincipal UserPrincipal principal) {
        return statsService.getStats(principal.getId());
    }
}
