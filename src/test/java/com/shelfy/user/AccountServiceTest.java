package com.shelfy.user;

import com.shelfy.book.Book;
import com.shelfy.book.BookRepository;
import com.shelfy.book.ReadEvent;
import com.shelfy.book.ReadEventRepository;
import com.shelfy.category.Category;
import com.shelfy.category.CategoryRepository;
import com.shelfy.common.exception.ResourceNotFoundException;
import com.shelfy.follow.Follow;
import com.shelfy.follow.FollowRepository;
import com.shelfy.goal.ReadingGoal;
import com.shelfy.goal.ReadingGoalRepository;
import com.shelfy.note.Note;
import com.shelfy.note.NoteRepository;
import com.shelfy.notification.NotificationRepository;
import com.shelfy.readinglog.ReadingLog;
import com.shelfy.readinglog.ReadingLogRepository;
import com.shelfy.review.Review;
import com.shelfy.review.ReviewRepository;
import com.shelfy.user.dto.DeleteAccountRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private BookRepository bookRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private NoteRepository noteRepository;
    @Mock
    private ReadingLogRepository readingLogRepository;
    @Mock
    private ReadingGoalRepository readingGoalRepository;
    @Mock
    private FollowRepository followRepository;
    @Mock
    private ReadEventRepository readEventRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private AvatarRepository avatarRepository;
    @Mock
    private com.shelfy.push.PushSubscriptionRepository pushSubscriptionRepository;
    @Mock
    private com.shelfy.review.ReviewCommentRepository reviewCommentRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @Captor
    private ArgumentCaptor<Set<Follow>> followsCaptor;

    private AccountService service;

    @BeforeEach
    void setUp() {
        service = new AccountService(userRepository, bookRepository, categoryRepository, reviewRepository,
                noteRepository, readingLogRepository, readingGoalRepository, followRepository, reviewCommentRepository,
                readEventRepository, notificationRepository, avatarRepository, pushSubscriptionRepository,
                passwordEncoder);
    }

    private User user() {
        return User.builder().id(USER_ID).email("lectora@shelfy.app").password("hashed").build();
    }

    @Test
    void deleteAccount_throwsWhenTheUserDoesNotExist() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteAccount(USER_ID, new DeleteAccountRequest("whatever")))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(userRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deleteAccount_throwsAndDeletesNothingWhenThePasswordIsWrong() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> service.deleteAccount(USER_ID, new DeleteAccountRequest("wrong")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(bookRepository, never()).deleteByOwnerId(USER_ID);
        verify(userRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deleteAccount_cascadesEverythingAndFinallyDeletesTheUser() {
        User user = user();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct", "hashed")).thenReturn(true);
        lenient().when(avatarRepository.findById(USER_ID)).thenReturn(Optional.empty());

        service.deleteAccount(USER_ID, new DeleteAccountRequest("correct"));

        verify(readingLogRepository).deleteByOwnerId(USER_ID);
        verify(reviewCommentRepository).deleteByUserId(USER_ID);
        verify(reviewRepository).deleteByUserId(USER_ID);
        verify(noteRepository).deleteByUserId(USER_ID);
        verify(readingGoalRepository).deleteByOwnerId(USER_ID);
        verify(readEventRepository).deleteByOwnerId(USER_ID);
        verify(bookRepository).deleteJoinRowsByOwnerId(USER_ID);
        verify(bookRepository).deleteByOwnerId(USER_ID);
        verify(categoryRepository).deleteByOwnerId(USER_ID);
        verify(followRepository).deleteByFollowerIdOrFollowedId(USER_ID, USER_ID);
        verify(notificationRepository).deleteByRecipientIdOrActorId(USER_ID);
        verify(userRepository).delete(user);
    }

    @Test
    void deleteAccount_alsoDeletesTheAvatarWhenThereIsOne() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(passwordEncoder.matches("correct", "hashed")).thenReturn(true);
        stubEmptyCollections();
        Avatar avatar = Avatar.builder().userId(USER_ID).build();
        when(avatarRepository.findById(USER_ID)).thenReturn(Optional.of(avatar));

        service.deleteAccount(USER_ID, new DeleteAccountRequest("correct"));

        verify(avatarRepository).delete(avatar);
    }

    @Test
    void deleteAccount_doesNotTryToDeleteAnAvatarThatDoesNotExist() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(passwordEncoder.matches("correct", "hashed")).thenReturn(true);
        stubEmptyCollections();
        when(avatarRepository.findById(USER_ID)).thenReturn(Optional.empty());

        service.deleteAccount(USER_ID, new DeleteAccountRequest("correct"));

        verify(avatarRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    private void stubEmptyCollections() {
    }
}
