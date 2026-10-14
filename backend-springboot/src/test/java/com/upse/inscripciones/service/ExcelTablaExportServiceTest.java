package com.upse.inscripciones.service;

import com.upse.inscripciones.service.ExcelTablaExportService.ColumnaExcel;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ExcelTablaExportService arma el .xlsx de los listados simples (Cursos,
 * Especialistas, Coordinadores, etc.): una columna por ColumnaExcel, con el
 * tipo de dato de cada celda según el valor (texto, número, fecha, booleano),
 * formato de moneda para las columnas marcadas como tal, y nombres de hoja
 * saneados para los caracteres que Excel no admite.
 */
class ExcelTablaExportServiceTest {

    private final ExcelTablaExportService servicio = new ExcelTablaExportService();

    private record Fila(String nombre, BigDecimal costo, Integer horas, Boolean activo,
                        LocalDate fecha, LocalDateTime fechaHora) {
    }

    private static XSSFSheet leerPrimeraHoja(byte[] xlsx) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(xlsx)).getSheetAt(0);
    }

    @Test
    void elEncabezadoUsaElTextoDeCadaColumnaExcelEnOrden() throws Exception {
        List<ColumnaExcel<Fila>> columnas = List.of(
                ColumnaExcel.de("Nombre", Fila::nombre),
                ColumnaExcel.moneda("Costo", Fila::costo));

        var hoja = leerPrimeraHoja(servicio.generar("Cursos", columnas, List.of()));

        Row header = hoja.getRow(0);
        assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Nombre");
        assertThat(header.getCell(1).getStringCellValue()).isEqualTo("Costo");
    }

    @Test
    void cadaTipoDeValorSeEscribeConElTipoDeCeldaCorrecto() throws Exception {
        List<ColumnaExcel<Fila>> columnas = List.of(
                ColumnaExcel.de("Nombre", Fila::nombre),
                ColumnaExcel.moneda("Costo", Fila::costo),
                ColumnaExcel.de("Horas", Fila::horas),
                ColumnaExcel.de("Activo", Fila::activo),
                ColumnaExcel.de("Fecha", Fila::fecha),
                ColumnaExcel.de("FechaHora", Fila::fechaHora));
        Fila fila = new Fila("Diplomado X", new BigDecimal("125.50"), 40, true,
                LocalDate.of(2026, 3, 15), LocalDateTime.of(2026, 3, 15, 9, 30));

        var hoja = leerPrimeraHoja(servicio.generar("Cursos", columnas, List.of(fila)));
        Row fila1 = hoja.getRow(1);

        assertThat(fila1.getCell(0).getStringCellValue()).isEqualTo("Diplomado X");
        assertThat(fila1.getCell(1).getCellType()).isEqualTo(CellType.NUMERIC);
        assertThat(fila1.getCell(1).getNumericCellValue()).isEqualTo(125.50);
        assertThat(fila1.getCell(2).getNumericCellValue()).isEqualTo(40.0);
        assertThat(fila1.getCell(3).getStringCellValue()).isEqualTo("Sí");
        assertThat(fila1.getCell(4).getStringCellValue()).isEqualTo("15/03/2026");
        assertThat(fila1.getCell(5).getStringCellValue()).isEqualTo("15/03/2026 09:30");
    }

    @Test
    void unBooleanoFalsoSeEscribeComoNoYUnValorNuloDejaLaCeldaEnBlanco() throws Exception {
        List<ColumnaExcel<Fila>> columnas = List.of(
                ColumnaExcel.de("Nombre", Fila::nombre),
                ColumnaExcel.de("Activo", Fila::activo));
        Fila fila = new Fila(null, null, null, false, null, null);

        var hoja = leerPrimeraHoja(servicio.generar("Cursos", columnas, List.of(fila)));
        Row fila1 = hoja.getRow(1);

        assertThat(fila1.getCell(0).getCellType()).isEqualTo(CellType.BLANK);
        assertThat(fila1.getCell(1).getStringCellValue()).isEqualTo("No");
    }

    @Test
    void sinFilasSoloQuedaElEncabezado() throws Exception {
        var hoja = leerPrimeraHoja(servicio.generar("Cursos", List.of(ColumnaExcel.de("Nombre", Fila::nombre)), List.of()));

        assertThat(hoja.getLastRowNum()).isZero();
        assertThat((Object) hoja.getRow(1)).isNull();
    }

    @Test
    void elNombreDeHojaSeSaneaYSeRecortaATreintaYUnCaracteres() throws Exception {
        String nombreLargo = "Reporte/Cursos: 2026 [enero-marzo] *final* ?revisado?" + "X".repeat(20);

        var hoja = leerPrimeraHoja(servicio.generar(nombreLargo, List.of(ColumnaExcel.de("N", Fila::nombre)), List.of()));

        String nombreHoja = hoja.getWorkbook().getSheetName(0);
        assertThat(nombreHoja).hasSizeLessThanOrEqualTo(31);
        assertThat(nombreHoja).doesNotContain("/", "\\", "?", "*", "[", "]", ":");
    }

    @Test
    void unaColumnaMonedaConValorNuloDejaLaCeldaEnBlancoSinLanzarError() throws Exception {
        List<ColumnaExcel<Fila>> columnas = List.of(ColumnaExcel.moneda("Costo", Fila::costo));
        Fila fila = new Fila("X", null, null, null, null, null);

        var hoja = leerPrimeraHoja(servicio.generar("Cursos", columnas, List.of(fila)));

        assertThat(hoja.getRow(1).getCell(0).getCellType()).isEqualTo(CellType.BLANK);
    }
}
