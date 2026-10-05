package com.shelfy.book;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvUtilTest {

    // --- escape ---

    @Test
    void escape_leavesPlainValuesUntouched() {
        assertThat(CsvUtil.escape("Cien años de soledad")).isEqualTo("Cien años de soledad");
    }

    @Test
    void escape_quotesAndDoublesInternalQuotesWhenValueContainsAComma() {
        assertThat(CsvUtil.escape("García, Gabriel")).isEqualTo("\"García, Gabriel\"");
    }

    @Test
    void escape_doublesInternalQuotes() {
        assertThat(CsvUtil.escape("El libro \"bueno\"")).isEqualTo("\"El libro \"\"bueno\"\"\"");
    }

    @Test
    void escape_quotesValuesContainingNewlines() {
        assertThat(CsvUtil.escape("línea 1\nlínea 2")).isEqualTo("\"línea 1\nlínea 2\"");
    }

    @Test
    void escape_returnsEmptyStringForNull() {
        assertThat(CsvUtil.escape(null)).isEmpty();
    }

    // --- parse ---

    @Test
    void parse_splitsSimpleCommaSeparatedRows() {
        List<List<String>> rows = CsvUtil.parse("title,author\r\nFoo,Bar\r\n");

        assertThat(rows).containsExactly(List.of("title", "author"), List.of("Foo", "Bar"));
    }

    @Test
    void parse_keepsACommaThatIsInsideQuotes() {
        List<List<String>> rows = CsvUtil.parse("title,author\r\nFoo,\"García, Gabriel\"\r\n");

        assertThat(rows.get(1)).containsExactly("Foo", "García, Gabriel");
    }

    @Test
    void parse_unescapesDoubledQuotesInsideAQuotedField() {
        List<List<String>> rows = CsvUtil.parse("title\r\n\"El libro \"\"bueno\"\"\"\r\n");

        assertThat(rows.get(1)).containsExactly("El libro \"bueno\"");
    }

    @Test
    void parse_keepsANewlineThatIsInsideQuotes() {
        List<List<String>> rows = CsvUtil.parse("title\r\n\"línea 1\nlínea 2\"\r\n");

        assertThat(rows).hasSize(2);
        assertThat(rows.get(1)).containsExactly("línea 1\nlínea 2");
    }

    @Test
    void parse_handlesBothCrLfAndBareLfLineEndings() {
        List<List<String>> rows = CsvUtil.parse("a,b\r\nc,d\ne,f");

        assertThat(rows).containsExactly(List.of("a", "b"), List.of("c", "d"), List.of("e", "f"));
    }

    @Test
    void parse_ignoresATrailingBlankLine() {
        List<List<String>> rows = CsvUtil.parse("a,b\r\nc,d\r\n\r\n");

        assertThat(rows).containsExactly(List.of("a", "b"), List.of("c", "d"));
    }

    @Test
    void parse_ofEmptyContentReturnsNoRows() {
        assertThat(CsvUtil.parse("")).isEmpty();
    }

    @Test
    void roundTrip_exportThenParseRecoversTheOriginalValue() {
        String tricky = "Título con \"comillas\", comas, y\nsaltos de línea";

        List<List<String>> rows = CsvUtil.parse(CsvUtil.escape(tricky) + "\r\n");

        assertThat(rows.get(0)).containsExactly(tricky);
    }
}
