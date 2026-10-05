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
    private PasswordEncoder passwordEncoder;

    @Captor
    private ArgumentCaptor<Set<Follow>> followsCaptor;

    private AccountService service;

    @BeforeEach
    void setUp() {
        service = new AccountService(userRepository, bookRepository, categoryRepository, reviewRepository,
                noteRepository, readingLogRepository, readingGoalRepository, followRepository, readEventRepository,
                notificationRepository, avatarRepository, passwordEncoder);
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

        verify(bookRepository, never()).deleteAll(org.mockito.ArgumentMatchers.<List<Book>>any());
        verify(userRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deleteAccount_cascadesEverythingAndFinallyDeletesTheUser() {
        User user = user();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct", "hashed")).thenReturn(true);

        ReadingLog readingLog = new ReadingLog();
        Review review = Review.builder().id(1L).build();
        Note note = Note.builder().id(1L).build();
        ReadingGoal goal = ReadingGoal.builder().id(1L).build();
        ReadEvent readEvent = ReadEvent.builder().id(1L).build();
        Book book = Book.builder().id(1L).build();
        Category category = Category.builder().id(1L).build();
        Follow following = Follow.builder().id(1L).build();
        Follow follower = Follow.builder().id(2L).build();

        when(readingLogRepository.findByOwnerId(USER_ID)).thenReturn(List.of(readingLog));
        when(reviewRepository.findByUserId(USER_ID)).thenReturn(List.of(review));
        when(noteRepository.findByUserId(USER_ID)).thenReturn(List.of(note));
        when(readingGoalRepository.findByOwnerId(USER_ID)).thenReturn(List.of(goal));
        when(readEventRepository.findByOwnerId(USER_ID)).thenReturn(List.of(readEvent));
        when(bookRepository.findByOwnerId(USER_ID)).thenReturn(List.of(book));
        when(categoryRepository.findByOwnerIdOrderByNameAsc(USER_ID)).thenReturn(List.of(category));
        when(followRepository.findByFollowerIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(following));
        when(followRepository.findByFollowedIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(follower));
        lenient().when(avatarRepository.findById(USER_ID)).thenReturn(Optional.empty());

        service.deleteAccount(USER_ID, new DeleteAccountRequest("correct"));

        verify(readingLogRepository).deleteAll(List.of(readingLog));
        verify(reviewRepository).deleteAll(List.of(review));
        verify(noteRepository).deleteAll(List.of(note));
        verify(readingGoalRepository).deleteAll(List.of(goal));
        verify(readEventRepository).deleteAll(List.of(readEvent));
        verify(bookRepository).deleteAll(List.of(book));
        verify(categoryRepository).deleteAll(List.of(category));
        verify(followRepository).deleteAll(followsCaptor.capture());
        assertThat(followsCaptor.getValue()).containsExactlyInAnyOrder(following, follower);
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
        lenient().when(readingLogRepository.findByOwnerId(USER_ID)).thenReturn(List.of());
        lenient().when(reviewRepository.findByUserId(USER_ID)).thenReturn(List.of());
        lenient().when(noteRepository.findByUserId(USER_ID)).thenReturn(List.of());
        lenient().when(readingGoalRepository.findByOwnerId(USER_ID)).thenReturn(List.of());
        lenient().when(readEventRepository.findByOwnerId(USER_ID)).thenReturn(List.of());
        lenient().when(bookRepository.findByOwnerId(USER_ID)).thenReturn(List.of());
        lenient().when(categoryRepository.findByOwnerIdOrderByNameAsc(USER_ID)).thenReturn(List.of());
        lenient().when(followRepository.findByFollowerIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of());
        lenient().when(followRepository.findByFollowedIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of());
    }
}
