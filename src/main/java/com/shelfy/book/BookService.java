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
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Objects;

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

        return PageResponse.from(bookRepository.findAll(spec, pageable), bookMapper::toResponse);
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
        book.setStartedAt(request.startedAt());
        book.setFinishedAt(request.finishedAt());

        if (request.finishedAt() != null) {
            book.setStatus(BookStatus.READ);
        } else if (request.startedAt() != null) {
            if (book.getStatus() == BookStatus.WANT_TO_READ || book.getStatus() == BookStatus.WANT_TO_BUY
                    || book.getStatus() == BookStatus.READ) {
                book.setStatus(BookStatus.READING);
            }
        } else if (book.getStatus() == BookStatus.READ || book.getStatus() == BookStatus.READING) {
            book.setStatus(BookStatus.WANT_TO_READ);
        }

        if (book.getStatus() != BookStatus.READING || !Objects.equals(previousStartedAt, request.startedAt())) {
            book.setReminderSentAt(null);
        }

        return bookMapper.toResponse(book);
    }

    @Transactional
    public BookResponse updateProgress(Long ownerId, Long id, UpdateProgressRequest request) {
        Book book = findOwned(ownerId, id);

        Integer currentPage = request.currentPage();
        if (book.getPageCount() != null && currentPage > book.getPageCount()) {
            currentPage = book.getPageCount();
        }
        book.setCurrentPage(currentPage);

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

        if (book.getFinishedAt() != null) {
            readEventRepository.save(ReadEvent.builder()
                    .book(book)
                    .startedAt(book.getStartedAt())
                    .finishedAt(book.getFinishedAt())
                    .build());
        }

        book.setStatus(BookStatus.READING);
        book.setStartedAt(LocalDate.now());
        book.setFinishedAt(null);
        book.setCurrentPage(null);
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
        book.setTitle(request.title().trim());
        book.setAuthor(request.author());
        book.setCoverUrl(request.coverUrl());
        book.setIsbn(request.isbn());
        book.setSynopsis(request.synopsis());
        book.setPageCount(request.pageCount());
        book.setCurrentPage(request.currentPage());
        book.setSeries(request.series());
        book.setSeriesPosition(request.seriesPosition());
        book.setFormat(request.format());
        book.setStatus(request.status());
        book.setStartedAt(request.startedAt());
        book.setFinishedAt(request.finishedAt());
        book.setCategories(new LinkedHashSet<>(
                categoryService.resolveOwned(ownerId, request.categoryIds())));

        if (book.getStatus() != BookStatus.READING) {
            book.setReminderSentAt(null);
        }
    }
}
