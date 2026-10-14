package com.upse.inscripciones.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Genera archivos .xlsx de una sola hoja con encabezado + filas de datos,
 * para los listados simples (Cursos, Especialistas, Coordinadores,
 * Planificaciones, Inscripciones). No confundir con
 * {@link InformeEconomicoExcelService}, que arma un libro con una hoja por
 * curso + hoja resumen — esto es más simple: una tabla, una hoja.
 * <p>
 * Cada módulo arma su propia lista de {@link ColumnaExcel} (encabezado +
 * cómo sacar el valor de cada fila) y llama a {@link #generar}; este
 * servicio no sabe nada de negocio, solo sabe pintar una tabla en Excel.
 */
@Service
public class ExcelTablaExportService {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FORMATO_FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String FORMATO_MONEDA =
            "_-[$$-409]* #,##0.00_ ;_-[$$-409]* -#,##0.00 ;_-[$$-409]* \"-\"??_ ;_-@_ ";

    /** Una columna del Excel: su encabezado, un extractor del valor por fila, y si es moneda. */
    public record ColumnaExcel<T>(String encabezado, java.util.function.Function<T, Object> extractor, boolean esMoneda) {
        public static <T> ColumnaExcel<T> de(String encabezado, java.util.function.Function<T, Object> extractor) {
            return new ColumnaExcel<>(encabezado, extractor, false);
        }
        public static <T> ColumnaExcel<T> moneda(String encabezado, java.util.function.Function<T, Object> extractor) {
            return new ColumnaExcel<>(encabezado, extractor, true);
        }
    }

    public <T> byte[] generar(String nombreHoja, List<ColumnaExcel<T>> columnas, List<T> filas) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(sanearNombreHoja(nombreHoja));

            CellStyle estiloHeader = crearEstiloHeader(workbook);
            CellStyle estiloTexto = crearEstiloTexto(workbook);
            CellStyle estiloMoneda = crearEstiloMoneda(workbook);

            Row header = sheet.createRow(0);
            for (int col = 0; col < columnas.size(); col++) {
                Cell c = header.createCell(col);
                c.setCellValue(columnas.get(col).encabezado());
                c.setCellStyle(estiloHeader);
            }

            int filaExcel = 1;
            for (T fila : filas) {
                Row row = sheet.createRow(filaExcel);
                for (int col = 0; col < columnas.size(); col++) {
                    ColumnaExcel<T> columna = columnas.get(col);
                    Cell cell = row.createCell(col);
                    escribirValor(cell, columna.extractor().apply(fila));
                    cell.setCellStyle(columna.esMoneda() ? estiloMoneda : estiloTexto);
                }
                filaExcel++;
            }

            for (int col = 0; col < columnas.size(); col++) {
                sheet.autoSizeColumn(col);
                // autoSizeColumn puede dejar columnas muy angostas con encabezados
                // cortos ("ID", "#"); se fuerza un mínimo legible.
                int anchoMinimo = 12 * 256;
                if (sheet.getColumnWidth(col) < anchoMinimo) {
                    sheet.setColumnWidth(col, anchoMinimo);
                }
            }

            sheet.createFreezePane(0, 1);
            if (!columnas.isEmpty()) {
                sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
                        0, 0, 0, columnas.size() - 1));
            }

            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            workbook.write(salida);
            return salida.toByteArray();
        }
    }

    private void escribirValor(Cell cell, Object valor) {
        if (valor == null) {
            cell.setBlank();
        } else if (valor instanceof BigDecimal bd) {
            cell.setCellValue(bd.doubleValue());
        } else if (valor instanceof Number n) {
            cell.setCellValue(n.doubleValue());
        } else if (valor instanceof Boolean b) {
            cell.setCellValue(b ? "Sí" : "No");
        } else if (valor instanceof LocalDate fecha) {
            cell.setCellValue(fecha.format(FORMATO_FECHA));
        } else if (valor instanceof LocalDateTime fechaHora) {
            cell.setCellValue(fechaHora.format(FORMATO_FECHA_HORA));
        } else {
            cell.setCellValue(valor.toString());
        }
    }

    private String sanearNombreHoja(String nombre) {
        String limpio = nombre.replaceAll("[\\\\/?*\\[\\]:]", " ").trim();
        return limpio.length() > 31 ? limpio.substring(0, 31).trim() : limpio;
    }

    private CellStyle crearEstiloHeader(Workbook wb) {
        Font fuente = wb.createFont();
        fuente.setFontName("Arial");
        fuente.setBold(true);
        fuente.setFontHeightInPoints((short) 11);
        fuente.setColor(IndexedColors.WHITE.getIndex());

        CellStyle estilo = wb.createCellStyle();
        estilo.setFont(fuente);
        estilo.setAlignment(HorizontalAlignment.CENTER);
        estilo.setVerticalAlignment(VerticalAlignment.CENTER);
        estilo.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        estilo.setBorderBottom(BorderStyle.THIN);
        return estilo;
    }

    private CellStyle crearEstiloTexto(Workbook wb) {
        Font fuente = wb.createFont();
        fuente.setFontName("Arial");
        fuente.setFontHeightInPoints((short) 10.5f);

        CellStyle estilo = wb.createCellStyle();
        estilo.setFont(fuente);
        estilo.setVerticalAlignment(VerticalAlignment.CENTER);
        estilo.setBorderBottom(BorderStyle.THIN);
        estilo.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        return estilo;
    }

    private CellStyle crearEstiloMoneda(Workbook wb) {
        CellStyle estilo = crearEstiloTexto(wb);
        DataFormat formato = wb.createDataFormat();
        // No se puede clonar solo el dataFormat sin clonar el estilo entero
        // (mismo font/borde), así que se crea un estilo nuevo con las mismas
        // propiedades en vez de mutar el estilo de texto ya devuelto.
        Font fuente = wb.createFont();
        fuente.setFontName("Arial");
        fuente.setFontHeightInPoints((short) 10.5f);

        CellStyle estiloMoneda = wb.createCellStyle();
        estiloMoneda.cloneStyleFrom(estilo);
        estiloMoneda.setDataFormat(formato.getFormat(FORMATO_MONEDA));
        estiloMoneda.setAlignment(HorizontalAlignment.RIGHT);
        return estiloMoneda;
    }
}
