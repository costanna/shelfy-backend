package com.shelfy.book;

import com.shelfy.book.dto.BookRequest;
import com.shelfy.book.dto.BookResponse;
import com.shelfy.book.dto.UpdateProgressRequest;
import com.shelfy.book.dto.UpdateReadingDatesRequest;
import com.shelfy.category.CategoryService;
import com.shelfy.common.dto.PageResponse;
import com.shelfy.common.exception.ResourceNotFoundException;
import com.shelfy.note.NoteRepository;
import com.shelfy.review.ReviewRepository;
import com.shelfy.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookService {

    private final BookRepository bookRepository;
    private final ReviewRepository reviewRepository;
    private final NoteRepository noteRepository;
    private final ReadEventRepository readEventRepository;
    private final BookMapper bookMapper;
    private final CategoryService categoryService;
    private final UserService userService;

    @Transactional(readOnly = true)
    public PageResponse<BookResponse> search(Long ownerId, BookFilter filter, Pageable pageable) {
        Specification<Book> spec = BookSpecifications.ownedBy(ownerId)
                .and(BookSpecifications.hasStatus(filter.status()))
                .and(BookSpecifications.hasCategory(filter.categoryId()))
                .and(BookSpecifications.matchesText(filter.query()));

        Page<Book> page = bookRepository.findAll(spec, pageable);

        // Batch-fetch read history for the whole page instead of one query per book.
        List<Long> bookIds = page.getContent().stream().map(Book::getId).toList();
        Map<Long, List<ReadEvent>> readHistoryByBookId = bookIds.isEmpty()
                ? Map.of()
                : readEventRepository.findByBookIdInOrderByFinishedAtDesc(bookIds).stream()
                        .collect(Collectors.groupingBy(event -> event.getBook().getId()));

        return PageResponse.from(page, book -> bookMapper.toResponse(
                book, readHistoryByBookId.getOrDefault(book.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    public BookResponse getById(Long ownerId, Long id) {
        return bookMapper.toResponse(findOwned(ownerId, id));
    }

    @Transactional
    public BookResponse create(Long ownerId, BookRequest request) {
        Book book = Book.builder()
                .owner(userService.getEntity(ownerId))
                .build();

        applyRequest(book, request, ownerId);
        return bookMapper.toResponse(bookRepository.save(book));
    }

    @Transactional
    public BookResponse update(Long ownerId, Long id, BookRequest request) {
        Book book = findOwned(ownerId, id);
        applyRequest(book, request, ownerId);
        return bookMapper.toResponse(book);
    }

    @Transactional
    public BookResponse updateReadingDates(Long ownerId, Long id, UpdateReadingDatesRequest request) {
        Book book = findOwned(ownerId, id);

        if (request.startedAt() != null && request.finishedAt() != null
                && request.finishedAt().isBefore(request.startedAt())) {
            throw new IllegalArgumentException("La fecha de fin no puede ser anterior a la de inicio");
        }

        LocalDate previousStartedAt = book.getStartedAt();
        LocalDate previousFinishedAt = book.getFinishedAt();
        BookStatus previousStatus = book.getStatus();

        book.setStartedAt(request.startedAt());
        book.setFinishedAt(request.finishedAt());

        if (request.finishedAt() != null) {
            book.setStatus(BookStatus.READ);
        } else if (request.startedAt() != null) {
            if (previousStatus == BookStatus.WANT_TO_READ || previousStatus == BookStatus.WANT_TO_BUY
                    || previousStatus == BookStatus.READ) {
                // Re-opening a finished book this way is the same move as reread(): archive its
                // finished read first, so it doesn't vanish from "libros terminados por mes".
                archivePreviousRead(book, previousStatus, previousStartedAt, previousFinishedAt);
                book.setStatus(BookStatus.READING);
            }
        } else if (previousStatus == BookStatus.READ || previousStatus == BookStatus.READING) {
            book.setStatus(BookStatus.WANT_TO_READ);
            book.setCurrentPage(null);
        }

        if (book.getStatus() != BookStatus.READING || !Objects.equals(previousStartedAt, request.startedAt())) {
            book.setReminderSentAt(null);
        }

        return bookMapper.toResponse(book);
    }

    @Transactional
    public BookResponse updateProgress(Long ownerId, Long id, UpdateProgressRequest request) {
        Book book = findOwned(ownerId, id);

        if (book.getStatus() == BookStatus.READ) {
            throw new IllegalArgumentException(
                    "Este libro ya está marcado como leído; usa \"Volver a leer\" si quieres actualizar su progreso");
        }

        book.setCurrentPage(clampToPageCount(request.currentPage(), book.getPageCount()));

        if (book.getStatus() == BookStatus.WANT_TO_READ || book.getStatus() == BookStatus.WANT_TO_BUY) {
            book.setStatus(BookStatus.READING);
        }
        if (book.getStatus() == BookStatus.READING && book.getStartedAt() == null) {
            book.setStartedAt(LocalDate.now());
        }

        return bookMapper.toResponse(book);
    }

    @Transactional
    public BookResponse reread(Long ownerId, Long id) {
        Book book = findOwned(ownerId, id);

        if (book.getStatus() != BookStatus.READ) {
            throw new IllegalArgumentException("Solo puedes volver a leer un libro que ya has terminado");
        }

        archivePreviousRead(book, book.getStatus(), book.getStartedAt(), book.getFinishedAt());

        book.setStatus(BookStatus.READING);
        book.setStartedAt(LocalDate.now());
        book.setFinishedAt(null);
        book.setReminderSentAt(null);

        return bookMapper.toResponse(book);
    }

    @Transactional
    public void delete(Long ownerId, Long id) {
        Book book = findOwned(ownerId, id);
        reviewRepository.deleteByBookId(book.getId());
        noteRepository.deleteByBookId(book.getId());
        readEventRepository.deleteByBookId(book.getId());
        bookRepository.delete(book);
    }

    @Transactional(readOnly = true)
    public Book findOwned(Long ownerId, Long id) {
        return bookRepository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Libro", id));
    }

    private void applyRequest(Book book, BookRequest request, Long ownerId) {
        BookStatus previousStatus = book.getStatus();
        LocalDate previousStartedAt = book.getStartedAt();
        LocalDate previousFinishedAt = book.getFinishedAt();

        book.setTitle(request.title().trim());
        book.setAuthor(request.author());
        book.setCoverUrl(request.coverUrl());
        book.setIsbn(request.isbn());
        book.setSynopsis(request.synopsis());
        book.setPageCount(request.pageCount());
        book.setCurrentPage(clampToPageCount(request.currentPage(), request.pageCount()));
        book.setSeries(request.series());
        book.setSeriesPosition(request.seriesPosition());
        book.setFormat(request.format());

        // Moving a finished book off READ this way (e.g. flipping the status dropdown back to
        // "Reading" on the full edit form) is the same move as reread()/updateReadingDates():
        // archive the read it had before overwriting it, so it isn't lost from the stats. This
        // also resets currentPage — deliberately overriding whatever the request carried for it,
        // since a re-opened book has no progress yet.
        if (previousStatus == BookStatus.READ && request.status() != BookStatus.READ) {
            archivePreviousRead(book, previousStatus, previousStartedAt, previousFinishedAt);
        }

        book.setStatus(request.status());
        book.setStartedAt(request.startedAt());
        book.setFinishedAt(request.finishedAt());
        book.setCategories(new LinkedHashSet<>(
                categoryService.resolveOwned(ownerId, request.categoryIds())));

        if (book.getStatus() != BookStatus.READING) {
            book.setReminderSentAt(null);
        }
    }

    /**
     * If the book's previous state was a finished read (READ with a finishedAt), archives it as a
     * {@link ReadEvent} and clears currentPage — the common first step whenever a book is about to
     * start reading again, shared by {@link #reread}, {@link #updateReadingDates} and
     * {@link #applyRequest} so the three don't drift out of sync with each other.
     */
    private void archivePreviousRead(Book book, BookStatus previousStatus, LocalDate previousStartedAt,
                                      LocalDate previousFinishedAt) {
        if (previousStatus == BookStatus.READ && previousFinishedAt != null) {
            readEventRepository.save(ReadEvent.builder()
                    .book(book)
                    .startedAt(previousStartedAt)
                    .finishedAt(previousFinishedAt)
                    .build());
            book.setCurrentPage(null);
        }
    }

    private Integer clampToPageCount(Integer currentPage, Integer pageCount) {
        if (currentPage != null && pageCount != null && currentPage > pageCount) {
            return pageCount;
        }
        return currentPage;
    }
}
