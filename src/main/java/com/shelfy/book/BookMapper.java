package com.shelfy.book;

import com.shelfy.book.dto.BookResponse;
import com.shelfy.book.dto.ReadEventResponse;
import com.shelfy.category.CategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class BookMapper {

    private final CategoryMapper categoryMapper;
    private final ReadEventRepository readEventRepository;

    /** For a single book: looks up its read history directly. */
    public BookResponse toResponse(Book book) {
        return toResponse(book, readEventRepository.findByBookIdOrderByFinishedAtDesc(book.getId()));
    }

    /**
     * For mapping a whole page of books: pass each book's read history already batch-fetched
     * (see {@link ReadEventRepository#findByBookIdInOrderByFinishedAtDesc}), instead of one query
     * per book.
     */
    public BookResponse toResponse(Book book, List<ReadEvent> readHistory) {
        return new BookResponse(
                book.getId(),
                book.getTitle(),
                book.getAuthor(),
                book.getCoverUrl(),
                book.getIsbn(),
                book.getSynopsis(),
                book.getPageCount(),
                book.getCurrentPage(),
                book.getSeries(),
                book.getSeriesPosition(),
                book.getFormat(),
                book.getStatus(),
                book.getStartedAt(),
                book.getFinishedAt(),
                readHistory.stream()
                        .map(event -> new ReadEventResponse(event.getStartedAt(), event.getFinishedAt()))
                        .toList(),
                book.getCategories().stream()
                        .sorted(Comparator.comparing(c -> c.getName().toLowerCase()))
                        .map(categoryMapper::toResponse)
                        .toList(),
                book.getCreatedAt(),
                book.getUpdatedAt()
        );
    }
}
