package com.shelfy.book;

import com.shelfy.book.dto.BookResponse;
import com.shelfy.book.dto.ReadEventResponse;
import com.shelfy.category.CategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;

@Component
@RequiredArgsConstructor
public class BookMapper {

    private final CategoryMapper categoryMapper;
    private final ReadEventRepository readEventRepository;

    public BookResponse toResponse(Book book) {
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
                readEventRepository.findByBookIdOrderByFinishedAtDesc(book.getId()).stream()
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
