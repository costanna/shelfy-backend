package com.shelfy.user;

import com.shelfy.user.dto.UserResponse;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getAlias(),
                user.getThemePreference(),
                user.getLanguagePreference(),
                user.getAvatarUpdatedAt(),
                user.isRemindersEnabled()
        );
    }
}
