package com.shelfy.book;

import com.shelfy.book.dto.BookImportResult;
import com.shelfy.category.Category;
import com.shelfy.category.CategoryService;
import com.shelfy.user.User;
import com.shelfy.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookCsvServiceTest {

    private static final Long OWNER_ID = 1L;

    @Mock
    private BookRepository bookRepository;
    @Mock
    private CategoryService categoryService;
    @Mock
    private UserService userService;

    @Captor
    private ArgumentCaptor<Book> bookCaptor;

    private BookCsvService service;

    @BeforeEach
    void setUp() {
        service = new BookCsvService(bookRepository, categoryService, userService);
        lenient().when(userService.getEntity(OWNER_ID)).thenReturn(User.builder().id(OWNER_ID).build());
        lenient().when(bookRepository.save(any(Book.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private MockMultipartFile csvFile(String content) {
        return new MockMultipartFile("file", "libros.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    private Book book(String title, String author, BookStatus status) {
        return Book.builder().title(title).author(author).status(status).categories(java.util.Set.of()).build();
    }

    // --- export ---

    @Test
    void export_ofAnEmptyLibraryReturnsOnlyTheHeader() {
        when(bookRepository.findByOwnerIdAndDeletedAtIsNull(OWNER_ID)).thenReturn(List.of());

        String csv = service.export(OWNER_ID);

        assertThat(csv).isEqualTo(
                "title,author,isbn,status,pageCount,series,seriesPosition,format,startedAt,finishedAt,categories,synopsis\r\n");
    }

    @Test
    void export_escapesValuesThatContainCommas() {
        Book b = book("Mi libro, el bueno", "García, Gabriel", BookStatus.READ);
        when(bookRepository.findByOwnerIdAndDeletedAtIsNull(OWNER_ID)).thenReturn(List.of(b));

        String csv = service.export(OWNER_ID);

        assertThat(csv).contains("\"Mi libro, el bueno\",\"García, Gabriel\"");
    }

    @Test
    void export_joinsMultipleCategoriesWithASemicolon() {
        Book b = book("Dune", "Frank Herbert", BookStatus.READ);
        b.setCategories(new java.util.LinkedHashSet<>(List.of(
                Category.builder().name("Ciencia ficción").build(),
                Category.builder().name("Favoritos").build())));
        when(bookRepository.findByOwnerIdAndDeletedAtIsNull(OWNER_ID)).thenReturn(List.of(b));

        String csv = service.export(OWNER_ID);

        assertThat(csv).contains("Ciencia ficción; Favoritos");
    }

    // --- importCsv: validación de estructura ---

    @Test
    void importCsv_ofAnEmptyFileReportsAnError() {
        BookImportResult result = service.importCsv(OWNER_ID, csvFile(""));

        assertThat(result.imported()).isZero();
        assertThat(result.messages()).containsExactly("El archivo está vacío.");
    }

    @Test
    void importCsv_requiresATitleColumn() {
        BookImportResult result = service.importCsv(OWNER_ID, csvFile("author,isbn\r\nGarcía,123\r\n"));

        assertThat(result.imported()).isZero();
        assertThat(result.messages()).containsExactly("El CSV debe tener una columna 'title'.");
    }

    @Test
    void importCsv_isCaseInsensitiveAndOrderIndependentForColumns() {
        BookImportResult result = service.importCsv(OWNER_ID, csvFile("AUTHOR,TITLE\r\nGarcía,Dune\r\n"));

        verify(bookRepository).save(bookCaptor.capture());
        assertThat(bookCaptor.getValue().getTitle()).isEqualTo("Dune");
        assertThat(bookCaptor.getValue().getAuthor()).isEqualTo("García");
        assertThat(result.imported()).isEqualTo(1);
    }

    // --- importCsv: filas ---

    @Test
    void importCsv_skipsRowsWithoutATitle() {
        BookImportResult result = service.importCsv(OWNER_ID, csvFile("title,author\r\n,García\r\n"));

        assertThat(result.imported()).isZero();
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.messages()).containsExactly("Fila 2: omitida, falta el título.");
        verify(bookRepository, never()).save(any());
    }

    @Test
    void importCsv_defaultsToWantToReadWhenStatusIsMissingOrInvalid() {
        service.importCsv(OWNER_ID, csvFile("title,status\r\nDune,WANT_TO_READ\r\nOtro,disparate\r\n"));

        verify(bookRepository, org.mockito.Mockito.times(2)).save(bookCaptor.capture());
        assertThat(bookCaptor.getAllValues())
                .extracting(Book::getStatus)
                .containsExactly(BookStatus.WANT_TO_READ, BookStatus.WANT_TO_READ);
    }

    @Test
    void importCsv_ignoresAnInvalidPageCountInsteadOfFailingTheRow() {
        service.importCsv(OWNER_ID, csvFile("title,pageCount\r\nDune,no-es-un-numero\r\n"));

        verify(bookRepository).save(bookCaptor.capture());
        assertThat(bookCaptor.getValue().getPageCount()).isNull();
    }

    @Test
    void importCsv_ignoresTheDateRangeWhenFinishedAtIsBeforeStartedAtButStillImportsTheRow() {
        BookImportResult result = service.importCsv(OWNER_ID, csvFile(
                "title,startedAt,finishedAt\r\nDune,2026-02-01,2026-01-01\r\n"));

        verify(bookRepository).save(bookCaptor.capture());
        Book saved = bookCaptor.getValue();
        assertThat(saved.getStartedAt()).isNull();
        assertThat(saved.getFinishedAt()).isNull();
        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.messages()).containsExactly(
                "Fila 2: fecha de fin anterior a la de inicio, se ha ignorado el rango de fechas.");
    }

    @Test
    void importCsv_parsesAValidDateRange() {
        service.importCsv(OWNER_ID, csvFile("title,startedAt,finishedAt\r\nDune,2026-01-01,2026-01-20\r\n"));

        verify(bookRepository).save(bookCaptor.capture());
        assertThat(bookCaptor.getValue().getStartedAt()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(bookCaptor.getValue().getFinishedAt()).isEqualTo(LocalDate.of(2026, 1, 20));
    }

    @Test
    void importCsv_resolvesSemicolonSeparatedCategoriesPerRow() {
        when(categoryService.getOrCreate(anyLong(), anyString()))
                .thenAnswer(inv -> Category.builder().name(inv.getArgument(1)).build());

        service.importCsv(OWNER_ID, csvFile("title,categories\r\nDune,Ciencia ficción; Favoritos\r\n"));

        verify(categoryService).getOrCreate(OWNER_ID, "Ciencia ficción");
        verify(categoryService).getOrCreate(OWNER_ID, " Favoritos");
    }

    @Test
    void importCsv_countsImportedAndSkippedAcrossMultipleRows() {
        BookImportResult result = service.importCsv(OWNER_ID, csvFile(
                "title,author\r\nDune,Herbert\r\n,Sin título\r\n1984,Orwell\r\n"));

        assertThat(result.imported()).isEqualTo(2);
        assertThat(result.skipped()).isEqualTo(1);
    }
}
