package com.shelfy.book;

import com.shelfy.category.Category;
import com.shelfy.user.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "books", indexes = @Index(name = "idx_books_owner_id", columnList = "owner_id"))
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    private String author;

    @Column(name = "cover_url", length = 1000)
    private String coverUrl;

    @Column(length = 20)
    private String isbn;

    @Column(columnDefinition = "TEXT")
    private String synopsis;

    @Column(name = "page_count")
    private Integer pageCount;

    private String series;

    @Column(name = "series_position")
    private Integer seriesPosition;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private BookFormat format;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private BookStatus status = BookStatus.WANT_TO_READ;

    @Column(name = "started_at")
    private LocalDate startedAt;

    @Column(name = "finished_at")
    private LocalDate finishedAt;

    @Column(name = "reminder_sent_at")
    private Instant reminderSentAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "book_categories",
            joinColumns = @JoinColumn(name = "book_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id"),
            indexes = {
                    @Index(name = "idx_book_categories_book_id", columnList = "book_id"),
                    @Index(name = "idx_book_categories_category_id", columnList = "category_id")
            }
    )
    @Builder.Default
    private Set<Category> categories = new LinkedHashSet<>();

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
