package com.shelfy.book;

import java.util.ArrayList;
import java.util.List;

final class CsvUtil {

    private CsvUtil() {
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuoting = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        if (!needsQuoting) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    static List<List<String>> parse(String content) {
        List<List<String>> rows = new ArrayList<>();
        List<String> currentRow = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean rowHasContent = false;

        int i = 0;
        int length = content.length();
        while (i < length) {
            char c = content.charAt(i);

            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < length && content.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
                rowHasContent = true;
            } else if (c == ',') {
                currentRow.add(field.toString());
                field.setLength(0);
                rowHasContent = true;
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < length && content.charAt(i + 1) == '\n') {
                    i++;
                }
                currentRow.add(field.toString());
                field.setLength(0);
                // currentRow ya no está vacío nunca aquí (se le acaba de añadir el campo
                // de arriba), así que "|| !currentRow.isEmpty()" sería siempre cierto y
                // anularía por completo el filtro de líneas en blanco: una línea vacía
                // (p. ej. una línea extra al final de un CSV exportado desde Excel/Sheets)
                // generaba una fila fantasma de un solo campo vacío en vez de ignorarse.
                if (rowHasContent) {
                    rows.add(currentRow);
                }
                currentRow = new ArrayList<>();
                rowHasContent = false;
            } else {
                field.append(c);
                rowHasContent = true;
            }
            i++;
        }

        if (rowHasContent || field.length() > 0 || !currentRow.isEmpty()) {
            currentRow.add(field.toString());
            rows.add(currentRow);
        }

        return rows;
    }
}
