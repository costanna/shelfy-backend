package com.shelfy.user;

import com.shelfy.common.exception.DuplicateResourceException;
import com.shelfy.common.exception.ResourceNotFoundException;
import com.shelfy.user.dto.UpdateAliasRequest;
import com.shelfy.user.dto.UpdatePreferencesRequest;
import com.shelfy.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        return userMapper.toResponse(findOrThrow(id));
    }

    @Transactional
    public UserResponse updatePreferences(Long id, UpdatePreferencesRequest request) {
        User user = findOrThrow(id);

        if (request.themePreference() != null) {
            user.setThemePreference(request.themePreference());
        }
        if (request.languagePreference() != null) {
            user.setLanguagePreference(request.languagePreference());
        }

        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse updateAlias(Long id, UpdateAliasRequest request) {
        User user = findOrThrow(id);

        userRepository.findByAliasIgnoreCase(request.alias())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new DuplicateResourceException("Ese alias ya está en uso");
                });

        user.setAlias(request.alias());
        return userMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public User getEntity(Long id) {
        return findOrThrow(id);
    }

    private User findOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));
    }
}
