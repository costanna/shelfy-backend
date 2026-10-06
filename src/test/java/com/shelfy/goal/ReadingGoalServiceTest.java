package com.shelfy.goal;

import com.shelfy.book.Book;
import com.shelfy.book.BookRepository;
import com.shelfy.book.BookStatus;
import com.shelfy.book.ReadEventRepository;
import com.shelfy.goal.dto.ReadingGoalRequest;
import com.shelfy.goal.dto.ReadingGoalResponse;
import com.shelfy.user.User;
import com.shelfy.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReadingGoalServiceTest {

    private static final Long OWNER_ID = 1L;
    private static final int THIS_YEAR = Year.now().getValue();

    @Mock
    private ReadingGoalRepository goalRepository;
    @Mock
    private BookRepository bookRepository;
    @Mock
    private ReadEventRepository readEventRepository;
    @Mock
    private UserService userService;

    @Captor
    private ArgumentCaptor<ReadingGoal> goalCaptor;

    private ReadingGoalService service;

    @BeforeEach
    void setUp() {
        service = new ReadingGoalService(goalRepository, bookRepository, readEventRepository, userService);
        lenient().when(readEventRepository.findByOwnerId(OWNER_ID)).thenReturn(List.of());
    }

    private Book readBookIn(int year) {
        return Book.builder().status(BookStatus.READ).finishedAt(LocalDate.of(year, 6, 1)).build();
    }

    @Test
    void getCurrent_returnsNullTargetWhenNoGoalHasBeenSetYet() {
        when(goalRepository.findByOwnerIdAndYear(OWNER_ID, THIS_YEAR)).thenReturn(Optional.empty());
        when(bookRepository.findByOwnerIdAndDeletedAtIsNull(OWNER_ID)).thenReturn(List.of());

        ReadingGoalResponse response = service.getCurrent(OWNER_ID);

        assertThat(response.year()).isEqualTo(THIS_YEAR);
        assertThat(response.targetBooks()).isNull();
        assertThat(response.booksRead()).isZero();
    }

    @Test
    void getCurrent_onlyCountsBooksFinishedInTheCurrentYear() {
        when(goalRepository.findByOwnerIdAndYear(OWNER_ID, THIS_YEAR))
                .thenReturn(Optional.of(ReadingGoal.builder().year(THIS_YEAR).targetBooks(20).build()));
        when(bookRepository.findByOwnerIdAndDeletedAtIsNull(OWNER_ID)).thenReturn(List.of(
                readBookIn(THIS_YEAR),
                readBookIn(THIS_YEAR),
                readBookIn(THIS_YEAR - 1),
                Book.builder().status(BookStatus.READING).build()));

        ReadingGoalResponse response = service.getCurrent(OWNER_ID);

        assertThat(response.targetBooks()).isEqualTo(20);
        assertThat(response.booksRead()).isEqualTo(2);
    }

    @Test
    void getCurrent_countsArchivedRereadsInTheSameYear() {
        when(goalRepository.findByOwnerIdAndYear(OWNER_ID, THIS_YEAR)).thenReturn(Optional.empty());
        when(bookRepository.findByOwnerIdAndDeletedAtIsNull(OWNER_ID)).thenReturn(List.of(readBookIn(THIS_YEAR)));
        com.shelfy.book.ReadEvent archived = com.shelfy.book.ReadEvent.builder()
                .finishedAt(java.time.LocalDate.of(THIS_YEAR, 3, 10))
                .build();
        when(readEventRepository.findByOwnerId(OWNER_ID)).thenReturn(List.of(archived));

        ReadingGoalResponse response = service.getCurrent(OWNER_ID);

        assertThat(response.booksRead()).isEqualTo(2);
    }

    @Test
    void setCurrent_createsANewGoalWhenNoneExistsYet() {
        when(goalRepository.findByOwnerIdAndYear(OWNER_ID, THIS_YEAR)).thenReturn(Optional.empty());
        when(userService.getEntity(OWNER_ID)).thenReturn(User.builder().id(OWNER_ID).build());
        when(bookRepository.findByOwnerIdAndDeletedAtIsNull(OWNER_ID)).thenReturn(List.of());

        ReadingGoalResponse response = service.setCurrent(OWNER_ID, new ReadingGoalRequest(30));

        verify(goalRepository).save(goalCaptor.capture());
        assertThat(goalCaptor.getValue().getTargetBooks()).isEqualTo(30);
        assertThat(goalCaptor.getValue().getYear()).isEqualTo(THIS_YEAR);
        assertThat(response.targetBooks()).isEqualTo(30);
    }

    @Test
    void setCurrent_updatesTheExistingGoalInsteadOfCreatingAnother() {
        ReadingGoal existing = ReadingGoal.builder().id(5L).year(THIS_YEAR).targetBooks(10).build();
        when(goalRepository.findByOwnerIdAndYear(OWNER_ID, THIS_YEAR)).thenReturn(Optional.of(existing));
        when(bookRepository.findByOwnerIdAndDeletedAtIsNull(OWNER_ID)).thenReturn(List.of());

        service.setCurrent(OWNER_ID, new ReadingGoalRequest(50));

        verify(goalRepository).save(existing);
        assertThat(existing.getTargetBooks()).isEqualTo(50);
    }
}
