package com.shelfy.book;

import com.shelfy.book.dto.BookImportResult;
import com.shelfy.category.Category;
import com.shelfy.category.CategoryService;
import com.shelfy.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BookCsvService {

    private static final List<String> HEADER = List.of(
            "title", "author", "isbn", "status", "pageCount", "series", "seriesPosition", "format",
            "startedAt", "finishedAt", "categories", "synopsis"
    );

    private final BookRepository bookRepository;
    private final CategoryService categoryService;
    private final UserService userService;

    @Transactional(readOnly = true)
    public String export(Long ownerId) {
        List<Book> books = bookRepository.findByOwnerId(ownerId);

        StringBuilder csv = new StringBuilder();
        csv.append(String.join(",", HEADER)).append("\r\n");

        for (Book book : books) {
            String categories = book.getCategories().stream()
                    .map(Category::getName)
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("");

            List<String> row = List.of(
                    nullToEmpty(book.getTitle()),
                    nullToEmpty(book.getAuthor()),
                    nullToEmpty(book.getIsbn()),
                    book.getStatus().name(),
                    book.getPageCount() != null ? book.getPageCount().toString() : "",
                    nullToEmpty(book.getSeries()),
                    book.getSeriesPosition() != null ? book.getSeriesPosition().toString() : "",
                    book.getFormat() != null ? book.getFormat().name() : "",
                    book.getStartedAt() != null ? book.getStartedAt().toString() : "",
                    book.getFinishedAt() != null ? book.getFinishedAt().toString() : "",
                    categories,
                    nullToEmpty(book.getSynopsis())
            );

            csv.append(row.stream().map(CsvUtil::escape).reduce((a, b) -> a + "," + b).orElse(""))
                    .append("\r\n");
        }

        return csv.toString();
    }

    @Transactional
    public BookImportResult importCsv(Long ownerId, MultipartFile file) {
        String content = readAsUtf8(file);
        List<List<String>> rows = CsvUtil.parse(content);

        if (rows.isEmpty()) {
            return new BookImportResult(0, 0, List.of("El archivo está vacío."));
        }

        List<String> header = rows.get(0).stream().map(h -> h.trim().toLowerCase(Locale.ROOT)).toList();
        int titleIdx = header.indexOf("title");
        if (titleIdx < 0) {
            return new BookImportResult(0, 0, List.of("El CSV debe tener una columna 'title'."));
        }

        int authorIdx = header.indexOf("author");
        int isbnIdx = header.indexOf("isbn");
        int statusIdx = header.indexOf("status");
        int pageCountIdx = header.indexOf("pagecount");
        int seriesIdx = header.indexOf("series");
        int seriesPositionIdx = header.indexOf("seriesposition");
        int formatIdx = header.indexOf("format");
        int startedAtIdx = header.indexOf("startedat");
        int finishedAtIdx = header.indexOf("finishedat");
        int categoriesIdx = header.indexOf("categories");
        int synopsisIdx = header.indexOf("synopsis");

        int imported = 0;
        int skipped = 0;
        List<String> messages = new ArrayList<>();

        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            int rowNumber = r + 1;

            String title = value(row, titleIdx);
            if (title == null || title.isBlank()) {
                skipped++;
                messages.add("Fila " + rowNumber + ": omitida, falta el título.");
                continue;
            }

            Book book = Book.builder()
                    .owner(userService.getEntity(ownerId))
                    .title(title.trim())
                    .author(blankToNull(value(row, authorIdx)))
                    .isbn(blankToNull(value(row, isbnIdx)))
                    .synopsis(blankToNull(value(row, synopsisIdx)))
                    .build();

            book.setStatus(parseStatus(value(row, statusIdx)));
            book.setPageCount(parseInt(value(row, pageCountIdx)));
            book.setSeries(blankToNull(value(row, seriesIdx)));
            book.setSeriesPosition(parseInt(value(row, seriesPositionIdx)));
            book.setFormat(parseFormat(value(row, formatIdx)));

            LocalDate startedAt = parseDate(value(row, startedAtIdx));
            LocalDate finishedAt = parseDate(value(row, finishedAtIdx));
            if (startedAt != null && finishedAt != null && finishedAt.isBefore(startedAt)) {
                messages.add("Fila " + rowNumber + ": fecha de fin anterior a la de inicio, se ha ignorado el rango de fechas.");
            } else {
                book.setStartedAt(startedAt);
                book.setFinishedAt(finishedAt);
            }

            book.setCategories(resolveCategories(ownerId, value(row, categoriesIdx)));

            bookRepository.save(book);
            imported++;
        }

        return new BookImportResult(imported, skipped, messages);
    }

    private Set<Category> resolveCategories(Long ownerId, String raw) {
        if (raw == null || raw.isBlank()) {
            return new LinkedHashSet<>();
        }
        Set<Category> categories = new LinkedHashSet<>();
        for (String name : raw.split(";")) {
            if (!name.isBlank()) {
                categories.add(categoryService.getOrCreate(ownerId, name));
            }
        }
        return categories;
    }

    private BookStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return BookStatus.WANT_TO_READ;
        }
        try {
            return BookStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
        } catch (IllegalArgumentException ex) {
            return BookStatus.WANT_TO_READ;
        }
    }

    private BookFormat parseFormat(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return BookFormat.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private Integer parseInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String value(List<String> row, int index) {
        return index >= 0 && index < row.size() ? row.get(index) : null;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String readAsUtf8(MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
