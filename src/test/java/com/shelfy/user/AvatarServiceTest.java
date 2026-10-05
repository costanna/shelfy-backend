package com.shelfy.user;

import com.shelfy.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvatarServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private AvatarRepository avatarRepository;

    @Captor
    private ArgumentCaptor<Avatar> avatarCaptor;

    private AvatarService service;

    @BeforeEach
    void setUp() {
        service = new AvatarService(userRepository, avatarRepository, new UserMapper());
    }

    private User user() {
        return User.builder().id(USER_ID).email("lectora@shelfy.app").name("Lectora").build();
    }

    private MockMultipartFile realPngFile() {
        try {
            BufferedImage image = new BufferedImage(100, 60, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return new MockMultipartFile("avatar", "avatar.png", "image/png", out.toByteArray());
        } catch (IOException ex) {
            throw new java.io.UncheckedIOException(ex);
        }
    }

    @Test
    void upload_throwsWhenTheFileIsEmpty() {
        MockMultipartFile empty = new MockMultipartFile("avatar", new byte[0]);

        assertThatThrownBy(() -> service.upload(USER_ID, empty)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void upload_throwsWhenTheFileIsNull() {
        assertThatThrownBy(() -> service.upload(USER_ID, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void upload_throwsWhenTheFileExceedsFiveMegabytes() {
        byte[] tooBig = new byte[6 * 1024 * 1024];
        MockMultipartFile big = new MockMultipartFile("avatar", "big.png", "image/png", tooBig);

        assertThatThrownBy(() -> service.upload(USER_ID, big)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void upload_throwsForAnUnsupportedContentType() {
        MockMultipartFile gif = new MockMultipartFile("avatar", "a.gif", "image/gif", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> service.upload(USER_ID, gif)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void upload_throwsWhenTheContentTypeIsMissing() {
        MockMultipartFile noType = new MockMultipartFile("avatar", "a.png", null, new byte[]{1, 2, 3});

        assertThatThrownBy(() -> service.upload(USER_ID, noType)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void upload_throwsWhenTheBytesAreNotARealImage() {
        MockMultipartFile garbage = new MockMultipartFile("avatar", "a.png", "image/png", "esto no es un png".getBytes());

        assertThatThrownBy(() -> service.upload(USER_ID, garbage)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void upload_throwsWhenTheUserDoesNotExist() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upload(USER_ID, realPngFile()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void upload_createsANewAvatarWhenNoneExistedYet() {
        User user = user();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(avatarRepository.findById(USER_ID)).thenReturn(Optional.empty());

        service.upload(USER_ID, realPngFile());

        verify(avatarRepository).save(avatarCaptor.capture());
        assertThat(avatarCaptor.getValue().getImageData()).isNotEmpty();
        assertThat(user.getAvatarUpdatedAt()).isNotNull();
    }

    @Test
    void upload_reusesTheExistingAvatarRowInsteadOfCreatingAnother() {
        User user = user();
        Avatar existing = Avatar.builder().userId(USER_ID).user(user).imageData(new byte[]{9}).build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(avatarRepository.findById(USER_ID)).thenReturn(Optional.of(existing));

        service.upload(USER_ID, realPngFile());

        verify(avatarRepository).save(existing);
        assertThat(existing.getImageData()).isNotEqualTo(new byte[]{9});
    }

    @Test
    void remove_throwsWhenTheUserDoesNotExist() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.remove(USER_ID)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void remove_deletesTheAvatarWhenOneExists() {
        User user = user();
        user.setAvatarUpdatedAt(java.time.Instant.now());
        Avatar existing = Avatar.builder().userId(USER_ID).user(user).build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(avatarRepository.findById(USER_ID)).thenReturn(Optional.of(existing));

        service.remove(USER_ID);

        verify(avatarRepository).delete(any(Avatar.class));
        assertThat(user.getAvatarUpdatedAt()).isNull();
    }

    @Test
    void remove_doesNothingToTheRepositoryWhenThereIsNoAvatarToDelete() {
        User user = user();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(avatarRepository.findById(USER_ID)).thenReturn(Optional.empty());

        service.remove(USER_ID);

        verify(avatarRepository, never()).delete(any(Avatar.class));
        assertThat(user.getAvatarUpdatedAt()).isNull();
    }

    @Test
    void get_returnsTheAvatarWhenItExists() {
        Avatar avatar = Avatar.builder().userId(USER_ID).build();
        when(avatarRepository.findById(USER_ID)).thenReturn(Optional.of(avatar));

        assertThat(service.get(USER_ID)).isSameAs(avatar);
    }

    @Test
    void get_throwsWhenTheAvatarDoesNotExist() {
        when(avatarRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(USER_ID)).isInstanceOf(ResourceNotFoundException.class);
    }
}
