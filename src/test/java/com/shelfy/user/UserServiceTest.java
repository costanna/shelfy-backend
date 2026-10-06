package com.shelfy.user;

import com.shelfy.common.dto.PageResponse;
import com.shelfy.common.exception.DuplicateResourceException;
import com.shelfy.common.exception.ResourceNotFoundException;
import com.shelfy.follow.FollowService;
import com.shelfy.follow.dto.UserSummaryResponse;
import com.shelfy.user.dto.UpdateAliasRequest;
import com.shelfy.user.dto.UpdatePreferencesRequest;
import com.shelfy.user.dto.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private FollowService followService;

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository, new UserMapper(), followService);
    }

    private User user() {
        return User.builder().id(USER_ID).email("lectora@shelfy.app").name("Lectora").build();
    }

    @Test
    void getById_throwsWhenTheUserDoesNotExist() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(USER_ID)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updatePreferences_onlyChangesTheFieldsThatWereSent() {
        User user = user();
        user.setThemePreference(ThemePreference.LIGHT);
        user.setRemindersEnabled(true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        UserResponse response = service.updatePreferences(USER_ID,
                new UpdatePreferencesRequest(ThemePreference.DARK, null, null, null));

        assertThat(response.themePreference()).isEqualTo(ThemePreference.DARK);
        // languagePreference no se tocó (va null en la request): se queda con el
        // valor por defecto del builder, no se pone a null.
        assertThat(user.getLanguagePreference()).isEqualTo(LanguagePreference.ES);
        assertThat(user.isRemindersEnabled()).isTrue();
    }

    @Test
    void updateAlias_setsTheNewAliasWhenItIsFree() {
        User user = user();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.findByAliasIgnoreCase("nuevo_alias")).thenReturn(Optional.empty());

        UserResponse response = service.updateAlias(USER_ID, new UpdateAliasRequest("nuevo_alias"));

        assertThat(response.alias()).isEqualTo("nuevo_alias");
    }

    @Test
    void updateAlias_throwsWhenTheAliasIsTakenBySomeoneElse() {
        User user = user();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.findByAliasIgnoreCase("ocupado"))
                .thenReturn(Optional.of(User.builder().id(99L).build()));

        assertThatThrownBy(() -> service.updateAlias(USER_ID, new UpdateAliasRequest("ocupado")))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void updateAlias_allowsKeepingYourOwnCurrentAlias() {
        User user = user();
        user.setAlias("mi_alias");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        // El propio usuario "ya tiene" ese alias: no debería contar como conflicto.
        when(userRepository.findByAliasIgnoreCase("mi_alias")).thenReturn(Optional.of(user));

        UserResponse response = service.updateAlias(USER_ID, new UpdateAliasRequest("mi_alias"));

        assertThat(response.alias()).isEqualTo("mi_alias");
    }

    @Test
    void search_withABlankQueryReturnsAnEmptyPageWithoutQueryingTheRepository() {
        Pageable pageable = PageRequest.of(0, 10);

        PageResponse<UserSummaryResponse> result = service.search(USER_ID, "  ", pageable);

        assertThat(result.content()).isEmpty();
        verify(userRepository, never())
                .findByAliasContainingIgnoreCaseAndIdNot(org.mockito.ArgumentMatchers.anyString(), anyLong(), any());
    }

    @Test
    void search_excludesTheCallerAndIncludesFollowInfoForEachResult() {
        Pageable pageable = PageRequest.of(0, 10);
        User found = User.builder().id(2L).alias("otra").name("Otra").build();
        when(userRepository.findByAliasContainingIgnoreCaseAndIdNot("otra", USER_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(found), pageable, 1));
        when(followService.followersCount(2L)).thenReturn(5L);
        when(followService.isFollowing(USER_ID, 2L)).thenReturn(true);

        PageResponse<UserSummaryResponse> result = service.search(USER_ID, "otra", pageable);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).followersCount()).isEqualTo(5L);
        assertThat(result.content().get(0).followedByMe()).isTrue();
    }

    @Test
    void getEntity_returnsTheUserWhenItExists() {
        User user = user();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        assertThat(service.getEntity(USER_ID)).isSameAs(user);
    }
}
