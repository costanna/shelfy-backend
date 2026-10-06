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

    private static final int MAX_IMPORT_ROWS = 2000;
    private static final long MAX_IMPORT_BYTES = 5L * 1024 * 1024;

    private final BookRepository bookRepository;
    private final CategoryService categoryService;
    private final UserService userService;

    @Transactional(readOnly = true)
    public String export(Long ownerId) {
        List<Book> books = bookRepository.findByOwnerIdAndDeletedAtIsNull(ownerId);

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
        if (file == null || file.isEmpty()) {
            return new BookImportResult(0, 0, List.of("El archivo está vacío."));
        }
        if (file.getSize() > MAX_IMPORT_BYTES) {
            return new BookImportResult(0, 0, List.of("El archivo supera los 5 MB."));
        }
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

        int dataRows = rows.size() - 1;
        if (dataRows > MAX_IMPORT_ROWS) {
            return new BookImportResult(0, 0,
                    List.of("El archivo tiene " + dataRows + " filas; el máximo es " + MAX_IMPORT_ROWS + ". Divide el archivo."));
        }

        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            int rowNumber = r + 1;

            String title = value(row, titleIdx);
            if (title == null || title.isBlank()) {
                skipped++;
                messages.add("Fila " + rowNumber + ": omitida, falta el título.");
                continue;
            }
            String trimmedTitle = title.trim();
            if (trimmedTitle.length() > 255) {
                skipped++;
                messages.add("Fila " + rowNumber + ": omitida, el título supera los 255 caracteres.");
                continue;
            }

            Book book = Book.builder()
                    .owner(userService.getEntity(ownerId))
                    .title(trimmedTitle)
                    .author(truncate(blankToNull(value(row, authorIdx)), 255))
                    .isbn(truncate(blankToNull(value(row, isbnIdx)), 20))
                    .synopsis(truncate(blankToNull(value(row, synopsisIdx)), 5000))
                    .build();

            book.setStatus(parseStatus(value(row, statusIdx), rowNumber, messages));
            book.setPageCount(parseInt(value(row, pageCountIdx)));
            book.setSeries(truncate(blankToNull(value(row, seriesIdx)), 255));
            book.setSeriesPosition(parseInt(value(row, seriesPositionIdx)));
            book.setFormat(parseFormat(value(row, formatIdx), rowNumber, messages));

            String rawStarted = value(row, startedAtIdx);
            String rawFinished = value(row, finishedAtIdx);
            LocalDate startedAt = parseDate(rawStarted);
            LocalDate finishedAt = parseDate(rawFinished);
            if ((rawStarted != null && !rawStarted.isBlank() && startedAt == null)
                    || (rawFinished != null && !rawFinished.isBlank() && finishedAt == null)) {
                messages.add("Fila " + rowNumber + ": fecha no válida, se ha ignorado el rango de fechas.");
            } else if (startedAt != null && finishedAt != null && finishedAt.isBefore(startedAt)) {
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

    private BookStatus parseStatus(String raw, int rowNumber, List<String> messages) {
        if (raw == null || raw.isBlank()) {
            return BookStatus.WANT_TO_READ;
        }
        try {
            return BookStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
        } catch (IllegalArgumentException ex) {
            messages.add("Fila " + rowNumber + ": estado '" + raw.trim() + "' no válido, se usa WANT_TO_READ.");
            return BookStatus.WANT_TO_READ;
        }
    }

    private BookFormat parseFormat(String raw, int rowNumber, List<String> messages) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return BookFormat.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
        } catch (IllegalArgumentException ex) {
            messages.add("Fila " + rowNumber + ": formato '" + raw.trim() + "' no válido, se ignora.");
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

    private String truncate(String value, int max) {
        if (value != null && value.length() > max) {
            return value.substring(0, max);
        }
        return value;
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
