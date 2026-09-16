package com.shelfy.book;

import com.shelfy.book.dto.BookRequest;
import com.shelfy.book.dto.BookResponse;
import com.shelfy.book.dto.UpdateReadingDatesRequest;
import com.shelfy.common.dto.PageResponse;
import com.shelfy.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
public class BookController {

    private final BookService bookService;

    @GetMapping
    public PageResponse<BookResponse> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) BookStatus status,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false, name = "q") String query,
            @PageableDefault(size = 12, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return bookService.search(principal.getId(), new BookFilter(status, categoryId, query), pageable);
    }

    @GetMapping("/{id}")
    public BookResponse get(@AuthenticationPrincipal UserPrincipal principal,
                            @PathVariable Long id) {
        return bookService.getById(principal.getId(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookResponse create(@AuthenticationPrincipal UserPrincipal principal,
                               @Valid @RequestBody BookRequest request) {
        return bookService.create(principal.getId(), request);
    }

    @PutMapping("/{id}")
    public BookResponse update(@AuthenticationPrincipal UserPrincipal principal,
                               @PathVariable Long id,
                               @Valid @RequestBody BookRequest request) {
        return bookService.update(principal.getId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserPrincipal principal,
                       @PathVariable Long id) {
        bookService.delete(principal.getId(), id);
    }

    @PatchMapping("/{id}/reading-dates")
    public BookResponse updateReadingDates(@AuthenticationPrincipal UserPrincipal principal,
                                           @PathVariable Long id,
                                           @Valid @RequestBody UpdateReadingDatesRequest request) {
        return bookService.updateReadingDates(principal.getId(), id, request);
    }
}
