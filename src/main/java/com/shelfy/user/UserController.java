package com.shelfy.user;

import com.shelfy.follow.dto.UserSummaryResponse;
import com.shelfy.security.UserPrincipal;
import com.shelfy.user.dto.UpdateAliasRequest;
import com.shelfy.user.dto.UpdatePreferencesRequest;
import com.shelfy.user.dto.UserProfileResponse;
import com.shelfy.user.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserProfileService userProfileService;
    private final AvatarService avatarService;

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

    @GetMapping("/search")
    public List<UserSummaryResponse> search(@AuthenticationPrincipal UserPrincipal principal,
                                             @RequestParam(required = false) String q) {
        return userService.search(principal.getId(), q);
    }

    @GetMapping("/{id}/profile")
    public UserProfileResponse profile(@AuthenticationPrincipal UserPrincipal principal,
                                       @PathVariable Long id) {
        return userProfileService.getProfile(principal.getId(), id);
    }

    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserResponse uploadAvatar(@AuthenticationPrincipal UserPrincipal principal,
                                     @RequestParam("file") MultipartFile file) {
        return avatarService.upload(principal.getId(), file);
    }

    @DeleteMapping("/me/avatar")
    public UserResponse removeAvatar(@AuthenticationPrincipal UserPrincipal principal) {
        return avatarService.remove(principal.getId());
    }

    @GetMapping("/{id}/avatar")
    public ResponseEntity<byte[]> avatar(@PathVariable Long id) {
        Avatar avatar = avatarService.get(id);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic())
                .body(avatar.getImageData());
    }
}
