package com.shelfy.goal;

import com.shelfy.goal.dto.ReadingGoalRequest;
import com.shelfy.goal.dto.ReadingGoalResponse;
import com.shelfy.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reading-goals")
@RequiredArgsConstructor
public class ReadingGoalController {

    private final ReadingGoalService goalService;

    @GetMapping("/current")
    public ReadingGoalResponse current(@AuthenticationPrincipal UserPrincipal principal) {
        return goalService.getCurrent(principal.getId());
    }

    @PutMapping("/current")
    public ReadingGoalResponse setCurrent(@AuthenticationPrincipal UserPrincipal principal,
                                          @Valid @RequestBody ReadingGoalRequest request) {
        return goalService.setCurrent(principal.getId(), request);
    }
}
