package com.shelfy.book;

import com.shelfy.book.dto.BookRequest;
import com.shelfy.book.dto.BookResponse;
import com.shelfy.book.dto.BookStatusCountsResponse;
import com.shelfy.book.dto.UpdateProgressRequest;
import com.shelfy.book.dto.UpdateReadingDatesRequest;
import com.shelfy.category.CategoryService;
import com.shelfy.common.dto.PageResponse;
import com.shelfy.common.exception.ResourceNotFoundException;
import com.shelfy.note.NoteRepository;
import com.shelfy.review.ReviewRepository;
import com.shelfy.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
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
@Slf4j
public class BookService {

    private final BookRepository bookRepository;
    private final ReviewRepository reviewRepository;
    private final NoteRepository noteRepository;
    private final ReadEventRepository readEventRepository;
    private final BookMapper bookMapper;
    private final CategoryService categoryService;
    private final UserService userService;

    private static final java.util.Set<String> ALLOWED_SORT_FIELDS = java.util.Set.of(
            "title", "author", "createdAt", "updatedAt", "pageCount",
            "startedAt", "finishedAt", "status", "series", "seriesPosition",
            "statusChangedAt", "deletedAt");

    @Transactional(readOnly = true)
    public PageResponse<BookResponse> search(Long ownerId, BookFilter filter, Pageable pageable) {
        validateSort(pageable);
        String query = filter.query() != null && filter.query().length() > 100
                ? filter.query().substring(0, 100)
                : filter.query();
        BookFilter effectiveFilter = new BookFilter(filter.status(), filter.categoryId(), query);
        Specification<Book> spec = BookSpecifications.ownedBy(ownerId)
                .and(BookSpecifications.notDeleted())
                .and(BookSpecifications.hasStatus(effectiveFilter.status()))
                .and(BookSpecifications.hasCategory(effectiveFilter.categoryId()))
                .and(BookSpecifications.matchesText(effectiveFilter.query()));

        Page<Book> page = bookRepository.findAll(spec, pageable);

        List<Long> bookIds = page.getContent().stream().map(Book::getId).toList();
        Map<Long, List<ReadEvent>> readHistoryByBookId = bookIds.isEmpty()
                ? Map.of()
                : readEventRepository.findByBookIdInOrderByFinishedAtDesc(bookIds).stream()
                        .collect(Collectors.groupingBy(event -> event.getBook().getId()));

        return PageResponse.from(page, book -> bookMapper.toResponse(
                book, readHistoryByBookId.getOrDefault(book.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    public BookStatusCountsResponse getStatusCounts(Long ownerId) {
        Map<BookStatus, Long> counts = bookRepository.countByOwnerIdGroupedByStatus(ownerId).stream()
                .collect(Collectors.toMap(BookRepository.BookStatusCount::getStatus,
                        BookRepository.BookStatusCount::getTotal));

        return new BookStatusCountsResponse(
                counts.getOrDefault(BookStatus.WANT_TO_READ, 0L),
                counts.getOrDefault(BookStatus.READING, 0L),
                counts.getOrDefault(BookStatus.READ, 0L),
                counts.getOrDefault(BookStatus.WANT_TO_BUY, 0L));
    }

    @Transactional(readOnly = true)
    public BookResponse getById(Long ownerId, Long id) {
        return bookMapper.toResponse(findOwned(ownerId, id));
    }

    @Transactional
    public BookResponse create(Long ownerId, BookRequest request) {
        Book book = Book.builder()
                .owner(userService.getEntity(ownerId))
                .statusChangedAt(java.time.Instant.now())
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
            touchStatus(book, BookStatus.READ);
        } else if (request.startedAt() != null) {
            if (previousStatus == BookStatus.WANT_TO_READ || previousStatus == BookStatus.WANT_TO_BUY
                    || previousStatus == BookStatus.READ) {
                archivePreviousRead(book, previousStatus, previousStartedAt, previousFinishedAt);
                touchStatus(book, BookStatus.READING);
            }
        } else if (previousStatus == BookStatus.READ || previousStatus == BookStatus.READING) {
            touchStatus(book, BookStatus.WANT_TO_READ);
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
            touchStatus(book, BookStatus.READING);
        }
        if (book.getStatus() == BookStatus.READING && book.getStartedAt() == null) {
            book.setStartedAt(LocalDate.now());
        }
        // Activitat recent: permet que el recordatori torni a avisar si
        // el llibre torna a quedar estancat més endavant.
        book.setReminderSentAt(null);

        return bookMapper.toResponse(book);
    }

    @Transactional
    public BookResponse reread(Long ownerId, Long id) {
        Book book = findOwned(ownerId, id);

        if (book.getStatus() != BookStatus.READ) {
            throw new IllegalArgumentException("Solo puedes volver a leer un libro que ya has terminado");
        }

        archivePreviousRead(book, book.getStatus(), book.getStartedAt(), book.getFinishedAt());

        touchStatus(book, BookStatus.READING);
        book.setStartedAt(LocalDate.now());
        book.setFinishedAt(null);
        book.setReminderSentAt(null);

        return bookMapper.toResponse(book);
    }

    @Transactional
    public void delete(Long ownerId, Long id) {
        Book book = findOwned(ownerId, id);
        book.setDeletedAt(java.time.Instant.now());
    }

    @Transactional(readOnly = true)
    public PageResponse<BookResponse> trash(Long ownerId, Pageable pageable) {
        return PageResponse.from(
                bookRepository.findByOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(ownerId, pageable),
                bookMapper::toResponse);
    }

    @Transactional
    public BookResponse restore(Long ownerId, Long id) {
        Book book = bookRepository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Libro", id));
        if (book.getDeletedAt() == null) {
            throw new IllegalArgumentException("El libro no está en la papelera");
        }
        book.setDeletedAt(null);
        return bookMapper.toResponse(book);
    }

    @Transactional
    public void purge(Long ownerId, Long id) {
        Book book = bookRepository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Libro", id));
        if (book.getDeletedAt() == null) {
            throw new IllegalArgumentException("Primero mueve el libro a la papelera");
        }
        hardDelete(book);
    }

    /**
     * Purga automàtica: cada nit elimina definitivament els llibres que porten
     * més de 30 dies a la paperera, amb les seves ressenyes, notes i historial.
     */
    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public int purgeExpired() {
        java.time.Instant cutoff = java.time.Instant.now().minus(java.time.Duration.ofDays(30));
        java.util.List<Book> expired = bookRepository.findByDeletedAtBefore(cutoff);
        for (Book book : expired) {
            hardDelete(book);
        }
        if (!expired.isEmpty()) {
            log.info("Papelera purgada: {} libros eliminados definitivamente", expired.size());
        }
        return expired.size();
    }

    private void hardDelete(Book book) {
        reviewRepository.deleteByBookId(book.getId());
        noteRepository.deleteByBookId(book.getId());
        readEventRepository.deleteByBookId(book.getId());
        bookRepository.delete(book);
    }

    @Transactional(readOnly = true)
    public java.util.List<com.shelfy.book.dto.BookKeyResponse> keys(Long ownerId) {
        return bookRepository.findKeysByOwnerId(ownerId).stream()
                .map(row -> new com.shelfy.book.dto.BookKeyResponse(row.getId(), row.getTitle(), row.getAuthor()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Book findOwned(Long ownerId, Long id) {
        return bookRepository.findByIdAndOwnerIdAndDeletedAtIsNull(id, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Libro", id));
    }

    private void validateSort(Pageable pageable) {
        for (org.springframework.data.domain.Sort.Order order : pageable.getSort()) {
            if (!ALLOWED_SORT_FIELDS.contains(order.getProperty())) {
                throw new IllegalArgumentException("Orden no permitido: " + order.getProperty());
            }
        }
    }

    private void touchStatus(Book book, BookStatus next) {
        if (book.getStatus() != next) {
            book.setStatus(next);
            book.setStatusChangedAt(java.time.Instant.now());
        }
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

        if (previousStatus == BookStatus.READ && request.status() != BookStatus.READ) {
            archivePreviousRead(book, previousStatus, previousStartedAt, previousFinishedAt);
        }

        touchStatus(book, request.status());
        book.setStartedAt(request.startedAt());
        book.setFinishedAt(request.finishedAt());
        book.setCategories(new LinkedHashSet<>(
                categoryService.resolveOwned(ownerId, request.categoryIds())));

        if (book.getStatus() == BookStatus.WANT_TO_READ || book.getStatus() == BookStatus.WANT_TO_BUY) {
            book.setCurrentPage(null);
        }

        if (book.getStatus() != BookStatus.READING) {
            book.setReminderSentAt(null);
        }
    }

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
