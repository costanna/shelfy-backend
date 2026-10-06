package com.shelfy.book;

import com.shelfy.book.dto.BookRequest;
import com.shelfy.book.dto.BookResponse;
import com.shelfy.book.dto.BookStatusCountsResponse;
import com.shelfy.book.dto.UpdateProgressRequest;
import com.shelfy.book.dto.UpdateReadingDatesRequest;
import com.shelfy.category.CategoryService;
import com.shelfy.common.exception.ResourceNotFoundException;
import com.shelfy.note.NoteRepository;
import com.shelfy.review.ReviewRepository;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    private static final Long OWNER_ID = 1L;
    private static final Long BOOK_ID = 10L;

    @Mock
    private BookRepository bookRepository;
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private NoteRepository noteRepository;
    @Mock
    private ReadEventRepository readEventRepository;
    @Mock
    private BookMapper bookMapper;
    @Mock
    private CategoryService categoryService;
    @Mock
    private UserService userService;

    @Captor
    private ArgumentCaptor<Book> bookCaptor;
    @Captor
    private ArgumentCaptor<ReadEvent> readEventCaptor;

    private BookService service;

    @BeforeEach
    void setUp() {
        service = new BookService(
                bookRepository, reviewRepository, noteRepository, readEventRepository,
                bookMapper, categoryService, userService);
        // El mapper no es lo que estamos probando aquí: basta con que no reviente. lenient()
        // porque varios tests lanzan su excepción antes de llegar a invocar el mapper.
        lenient().when(bookMapper.toResponse(any(Book.class))).thenReturn(dummyResponse());
    }

    private BookResponse dummyResponse() {
        return new BookResponse(null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
    }

    private BookRequest requestWith(BookStatus status, LocalDate startedAt, LocalDate finishedAt) {
        return new BookRequest(
                "Cien años de soledad", "García Márquez", null, null, null,
                null, null, null, null, null, status, startedAt, finishedAt, Set.of());
    }

    private Book bookOwnedBy(User owner) {
        return Book.builder().id(BOOK_ID).owner(owner).status(BookStatus.WANT_TO_READ).build();
    }

    private User owner() {
        return User.builder().id(OWNER_ID).build();
    }

    // --- create / update ---

    @Test
    void create_savesBookOwnedByUserWithResolvedCategories() {
        User owner = owner();
        when(userService.getEntity(OWNER_ID)).thenReturn(owner);
        when(categoryService.resolveOwned(anyLong(), any())).thenReturn(Set.of());
        when(bookRepository.save(any(Book.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(OWNER_ID, requestWith(BookStatus.WANT_TO_READ, null, null));

        verify(bookRepository).save(bookCaptor.capture());
        Book saved = bookCaptor.getValue();
        assertThat(saved.getOwner()).isSameAs(owner);
        assertThat(saved.getTitle()).isEqualTo("Cien años de soledad");
        assertThat(saved.getStatus()).isEqualTo(BookStatus.WANT_TO_READ);
    }

    @Test
    void update_throwsWhenBookNotOwnedByCaller() {
        when(bookRepository.findByIdAndOwnerIdAndDeletedAtIsNull(BOOK_ID, OWNER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(OWNER_ID, BOOK_ID, requestWith(BookStatus.READ, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_archivesPreviousReadWhenStatusChangesAwayFromRead() {
        Book book = bookOwnedBy(owner());
        book.setStatus(BookStatus.READ);
        book.setStartedAt(LocalDate.of(2026, 1, 1));
        book.setFinishedAt(LocalDate.of(2026, 1, 15));
        when(bookRepository.findByIdAndOwnerIdAndDeletedAtIsNull(BOOK_ID, OWNER_ID)).thenReturn(Optional.of(book));
        when(categoryService.resolveOwned(anyLong(), any())).thenReturn(Set.of());

        service.update(OWNER_ID, BOOK_ID, requestWith(BookStatus.WANT_TO_READ, null, null));

        verify(readEventRepository).save(readEventCaptor.capture());
        ReadEvent archived = readEventCaptor.getValue();
        assertThat(archived.getStartedAt()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(archived.getFinishedAt()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(book.getStatus()).isEqualTo(BookStatus.WANT_TO_READ);
    }

    // --- updateProgress ---

    @Test
    void updateProgress_movesWantToReadBookIntoReadingAndStampsStartDate() {
        Book book = bookOwnedBy(owner());
        book.setStatus(BookStatus.WANT_TO_READ);
        book.setPageCount(300);
        when(bookRepository.findByIdAndOwnerIdAndDeletedAtIsNull(BOOK_ID, OWNER_ID)).thenReturn(Optional.of(book));

        service.updateProgress(OWNER_ID, BOOK_ID, new UpdateProgressRequest(50));

        assertThat(book.getStatus()).isEqualTo(BookStatus.READING);
        assertThat(book.getCurrentPage()).isEqualTo(50);
        assertThat(book.getStartedAt()).isEqualTo(LocalDate.now());
    }

    @Test
    void updateProgress_clampsCurrentPageToPageCount() {
        Book book = bookOwnedBy(owner());
        book.setStatus(BookStatus.READING);
        book.setPageCount(100);
        when(bookRepository.findByIdAndOwnerIdAndDeletedAtIsNull(BOOK_ID, OWNER_ID)).thenReturn(Optional.of(book));

        service.updateProgress(OWNER_ID, BOOK_ID, new UpdateProgressRequest(500));

        assertThat(book.getCurrentPage()).isEqualTo(100);
    }

    @Test
    void updateProgress_throwsWhenBookAlreadyFinished() {
        Book book = bookOwnedBy(owner());
        book.setStatus(BookStatus.READ);
        when(bookRepository.findByIdAndOwnerIdAndDeletedAtIsNull(BOOK_ID, OWNER_ID)).thenReturn(Optional.of(book));

        assertThatThrownBy(() -> service.updateProgress(OWNER_ID, BOOK_ID, new UpdateProgressRequest(10)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- updateReadingDates ---

    @Test
    void updateReadingDates_throwsWhenFinishedBeforeStarted() {
        Book book = bookOwnedBy(owner());
        when(bookRepository.findByIdAndOwnerIdAndDeletedAtIsNull(BOOK_ID, OWNER_ID)).thenReturn(Optional.of(book));
        UpdateReadingDatesRequest request =
                new UpdateReadingDatesRequest(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1));

        assertThatThrownBy(() -> service.updateReadingDates(OWNER_ID, BOOK_ID, request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateReadingDates_settingFinishedAtMarksBookAsRead() {
        Book book = bookOwnedBy(owner());
        book.setStatus(BookStatus.READING);
        book.setStartedAt(LocalDate.of(2026, 1, 1));
        when(bookRepository.findByIdAndOwnerIdAndDeletedAtIsNull(BOOK_ID, OWNER_ID)).thenReturn(Optional.of(book));

        service.updateReadingDates(OWNER_ID, BOOK_ID,
                new UpdateReadingDatesRequest(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 20)));

        assertThat(book.getStatus()).isEqualTo(BookStatus.READ);
        assertThat(book.getFinishedAt()).isEqualTo(LocalDate.of(2026, 1, 20));
    }

    @Test
    void updateReadingDates_restartingAFinishedBookArchivesThePreviousRead() {
        Book book = bookOwnedBy(owner());
        book.setStatus(BookStatus.READ);
        book.setStartedAt(LocalDate.of(2025, 1, 1));
        book.setFinishedAt(LocalDate.of(2025, 1, 15));
        when(bookRepository.findByIdAndOwnerIdAndDeletedAtIsNull(BOOK_ID, OWNER_ID)).thenReturn(Optional.of(book));

        service.updateReadingDates(OWNER_ID, BOOK_ID,
                new UpdateReadingDatesRequest(LocalDate.of(2026, 1, 1), null));

        verify(readEventRepository).save(readEventCaptor.capture());
        assertThat(readEventCaptor.getValue().getFinishedAt()).isEqualTo(LocalDate.of(2025, 1, 15));
        assertThat(book.getStatus()).isEqualTo(BookStatus.READING);
    }

    @Test
    void updateReadingDates_clearingBothDatesOnAReadBookResetsToWantToRead() {
        Book book = bookOwnedBy(owner());
        book.setStatus(BookStatus.READ);
        book.setStartedAt(LocalDate.of(2025, 1, 1));
        book.setFinishedAt(LocalDate.of(2025, 1, 15));
        book.setCurrentPage(200);
        when(bookRepository.findByIdAndOwnerIdAndDeletedAtIsNull(BOOK_ID, OWNER_ID)).thenReturn(Optional.of(book));

        service.updateReadingDates(OWNER_ID, BOOK_ID, new UpdateReadingDatesRequest(null, null));

        assertThat(book.getStatus()).isEqualTo(BookStatus.WANT_TO_READ);
        assertThat(book.getCurrentPage()).isNull();
    }

    // --- reread ---

    @Test
    void reread_throwsWhenBookIsNotFinished() {
        Book book = bookOwnedBy(owner());
        book.setStatus(BookStatus.READING);
        when(bookRepository.findByIdAndOwnerIdAndDeletedAtIsNull(BOOK_ID, OWNER_ID)).thenReturn(Optional.of(book));

        assertThatThrownBy(() -> service.reread(OWNER_ID, BOOK_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reread_archivesPreviousReadAndRestartsToday() {
        Book book = bookOwnedBy(owner());
        book.setStatus(BookStatus.READ);
        book.setStartedAt(LocalDate.of(2025, 1, 1));
        book.setFinishedAt(LocalDate.of(2025, 1, 15));

        when(bookRepository.findByIdAndOwnerIdAndDeletedAtIsNull(BOOK_ID, OWNER_ID)).thenReturn(Optional.of(book));

        service.reread(OWNER_ID, BOOK_ID);

        verify(readEventRepository).save(any(ReadEvent.class));
        assertThat(book.getStatus()).isEqualTo(BookStatus.READING);
        assertThat(book.getStartedAt()).isEqualTo(LocalDate.now());
        assertThat(book.getFinishedAt()).isNull();
    }

    // --- delete ---

    @Test
    void delete_movesBookToTrashInsteadOfHardDeleting() {
        Book book = bookOwnedBy(owner());
        when(bookRepository.findByIdAndOwnerIdAndDeletedAtIsNull(BOOK_ID, OWNER_ID)).thenReturn(Optional.of(book));

        service.delete(OWNER_ID, BOOK_ID);

        assertThat(book.getDeletedAt()).isNotNull();
        verify(reviewRepository, never()).deleteByBookId(BOOK_ID);
        verify(bookRepository, never()).delete(any(Book.class));
    }

    @Test
    void delete_throwsWhenBookNotOwnedByCaller() {
        when(bookRepository.findByIdAndOwnerIdAndDeletedAtIsNull(BOOK_ID, OWNER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(OWNER_ID, BOOK_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(bookRepository, never()).delete(any(Book.class));
    }

    @Test
    void restore_clearsDeletedAtWhenBookIsInTrash() {
        Book book = bookOwnedBy(owner());
        book.setDeletedAt(java.time.Instant.now());
        when(bookRepository.findByIdAndOwnerId(BOOK_ID, OWNER_ID)).thenReturn(Optional.of(book));

        service.restore(OWNER_ID, BOOK_ID);

        assertThat(book.getDeletedAt()).isNull();
    }

    @Test
    void purge_hardDeletesOnlyBooksAlreadyInTrash() {
        Book book = bookOwnedBy(owner());
        book.setDeletedAt(java.time.Instant.now());
        when(bookRepository.findByIdAndOwnerId(BOOK_ID, OWNER_ID)).thenReturn(Optional.of(book));

        service.purge(OWNER_ID, BOOK_ID);

        verify(reviewRepository).deleteByBookId(BOOK_ID);
        verify(noteRepository).deleteByBookId(BOOK_ID);
        verify(readEventRepository).deleteByBookId(BOOK_ID);
        verify(bookRepository).delete(book);
    }

    @Test
    void purgeExpired_hardDeletesOnlyBooksDeletedOverThirtyDaysAgo() {
        Book oldTrash = bookOwnedBy(owner());
        oldTrash.setDeletedAt(java.time.Instant.now().minus(java.time.Duration.ofDays(31)));
        when(bookRepository.findByDeletedAtBefore(any(java.time.Instant.class)))
                .thenReturn(List.of(oldTrash));

        int purged = service.purgeExpired();

        assertThat(purged).isEqualTo(1);
        verify(reviewRepository).deleteByBookId(oldTrash.getId());
        verify(bookRepository).delete(oldTrash);
    }

    // --- findOwned ---

    @Test
    void findOwned_throwsWhenBookDoesNotExistForThatOwner() {
        when(bookRepository.findByIdAndOwnerIdAndDeletedAtIsNull(BOOK_ID, OWNER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findOwned(OWNER_ID, BOOK_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getStatusCounts_mapsEachStatusAndDefaultsMissingOnesToZero() {
        when(bookRepository.countByOwnerIdGroupedByStatus(OWNER_ID)).thenReturn(List.of(
                statusCount(BookStatus.READING, 3),
                statusCount(BookStatus.READ, 7)));

        BookStatusCountsResponse counts = service.getStatusCounts(OWNER_ID);

        assertThat(counts.reading()).isEqualTo(3);
        assertThat(counts.read()).isEqualTo(7);
        assertThat(counts.wantToRead()).isZero();
        assertThat(counts.wantToBuy()).isZero();
        assertThat(counts.total()).isEqualTo(10);
    }

    private BookRepository.BookStatusCount statusCount(BookStatus status, long total) {
        return new BookRepository.BookStatusCount() {
            @Override
            public BookStatus getStatus() {
                return status;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }

    @Test
    void getById_returnsMappedResponseForOwnedBook() {
        Book book = bookOwnedBy(owner());
        when(bookRepository.findByIdAndOwnerIdAndDeletedAtIsNull(BOOK_ID, OWNER_ID)).thenReturn(Optional.of(book));

        BookResponse response = service.getById(OWNER_ID, BOOK_ID);

        assertThat(response).isNotNull();
        verify(bookMapper, times(1)).toResponse(book);
    }
}
