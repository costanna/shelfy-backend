package com.shelfy.book;

import com.shelfy.book.dto.BookRequest;
import com.shelfy.book.dto.BookResponse;
import com.shelfy.category.CategoryService;
import com.shelfy.common.dto.PageResponse;
import com.shelfy.common.exception.ResourceNotFoundException;
import com.shelfy.review.ReviewRepository;
import com.shelfy.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;

@Service
@RequiredArgsConstructor
public class BookService {

    private final BookRepository bookRepository;
    private final ReviewRepository reviewRepository;
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
    public void delete(Long ownerId, Long id) {
        Book book = findOwned(ownerId, id);
        reviewRepository.deleteByBookId(book.getId());
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
        book.setStatus(request.status());
        book.setCategories(new LinkedHashSet<>(
                categoryService.resolveOwned(ownerId, request.categoryIds())));
    }
}
