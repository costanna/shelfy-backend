package com.shelfy.book;

import com.shelfy.book.dto.BookResponse;
import com.shelfy.category.CategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;

@Component
@RequiredArgsConstructor
public class BookMapper {

    private final CategoryMapper categoryMapper;

    public BookResponse toResponse(Book book) {
        return new BookResponse(
                book.getId(),
                book.getTitle(),
                book.getAuthor(),
                book.getCoverUrl(),
                book.getIsbn(),
                book.getSynopsis(),
                book.getPageCount(),
                book.getStatus(),
                book.getCategories().stream()
                        .sorted(Comparator.comparing(c -> c.getName().toLowerCase()))
                        .map(categoryMapper::toResponse)
                        .toList(),
                book.getCreatedAt(),
                book.getUpdatedAt()
        );
    }
}
