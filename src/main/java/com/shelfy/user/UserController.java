package com.shelfy.user;

import com.shelfy.security.UserPrincipal;
import com.shelfy.user.dto.UpdateAliasRequest;
import com.shelfy.user.dto.UpdatePreferencesRequest;
import com.shelfy.user.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal UserPrincipal principal) {
        return userService.getById(principal.getId());
    }

    @PatchMapping("/me/preferences")
    public UserResponse updatePreferences(@AuthenticationPrincipal UserPrincipal principal,
                                          @Valid @RequestBody UpdatePreferencesRequest request) {
        return userService.updatePreferences(principal.getId(), request);
    }

    @PatchMapping("/me/alias")
    public UserResponse updateAlias(@AuthenticationPrincipal UserPrincipal principal,
                                    @Valid @RequestBody UpdateAliasRequest request) {
        return userService.updateAlias(principal.getId(), request);
    }
}
