package com.shelfy.book;

import com.shelfy.category.Category;
import jakarta.persistence.criteria.Join;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

final class BookSpecifications {

    private BookSpecifications() {
    }

    static Specification<Book> ownedBy(Long ownerId) {
        return (root, query, cb) -> cb.equal(root.get("owner").get("id"), ownerId);
    }

    static Specification<Book> hasStatus(BookStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    static Specification<Book> hasCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return (root, query, cb) -> {
            if (query != null) {
                query.distinct(true);
            }
            Join<Book, Category> categories = root.join("categories");
            return cb.equal(categories.get("id"), categoryId);
        };
    }

    static Specification<Book> matchesText(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String pattern = "%" + text.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), pattern),
                cb.like(cb.lower(cb.coalesce(root.get("author"), "")), pattern)
        );
    }
}
