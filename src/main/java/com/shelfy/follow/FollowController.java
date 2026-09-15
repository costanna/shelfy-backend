package com.shelfy.follow;

import com.shelfy.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/{id}/follow")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void follow(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        followService.follow(principal.getId(), id);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfollow(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        followService.unfollow(principal.getId(), id);
    }
}
