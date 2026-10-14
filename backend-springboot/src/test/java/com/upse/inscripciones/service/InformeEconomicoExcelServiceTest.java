package com.upse.inscripciones.service;

import com.upse.inscripciones.entity.*;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InformeEconomicoExcelServiceTest {

    private final InformeEconomicoExcelService service = new InformeEconomicoExcelService();

    @Test
    void exportacionConservaNombreDeProgramacionYTotalesHistoricosNoMultiplicables() throws Exception {
        InformeEconomico informe = crearInforme();
        byte[] contenido = service.generarExcel(List.of(informe), null, BigDecimal.ZERO);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(contenido))) {
            Sheet detalle = workbook.getSheetAt(0);
            assertThat(detalle.getRow(1).getCell(1).getStringCellValue())
                    .isEqualTo("CURSO: Curso de prueba - Segunda programación");

            Row retirados = buscarFila(detalle, "RETIRADOS");
            assertThat(retirados.getCell(5).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(retirados.getCell(5).getNumericCellValue()).isEqualTo(80.0);

            Row especialista = buscarFila(detalle, "ESPECIALISTA");
            assertThat(especialista.getCell(4).getNumericCellValue()).isEqualTo(800.0);
            assertThat(especialista.getCell(5).getCellFormula())
                    .isEqualTo("D" + (especialista.getRowNum() + 1) + "*E" + (especialista.getRowNum() + 1));

            Row comisiones = buscarFila(detalle, "COMISIONES");
            assertThat(comisiones.getCell(5).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(comisiones.getCell(5).getNumericCellValue()).isEqualTo(2700.0);

            Sheet resumen = workbook.getSheet("INFORME ECONÓMICO");
            assertThat(resumen.getRow(3).getCell(1).getStringCellValue())
                    .isEqualTo("Curso de prueba - Segunda programación");
            assertThat(resumen.getRow(3).getCell(4).getCellType()).isEqualTo(CellType.FORMULA);
            assertThat(resumen.getRow(3).getCell(5).getCellFormula()).isEqualTo("D4-E4");
            Row totales = buscarFilaEnColumna(resumen, 1, "TOTAL");
            int filaTotalesExcel = totales.getRowNum() + 1;
            assertThat(totales.getCell(5).getCellFormula())
                    .isEqualTo("D" + filaTotalesExcel + "-E" + filaTotalesExcel);
        }
    }

    @Test
    void exportacionAnualIncluyeNuevosCursosYElGastoEditadoSinCifrasFijas() throws Exception {
        var nuevo = crearInformeHistorico(900L, "NEW", "Curso nuevo", LocalDate.of(2026, 11, 1),
                2, new BigDecimal("150"), new BigDecimal("60"));
        nuevo.setHistorico(false);
        var anterior = crearInformeHistorico(901L, "OLD", "Anterior", LocalDate.of(2025, 11, 1),
                9, new BigDecimal("9999"), new BigDecimal("900"));
        byte[] contenido = service.generarExcel(List.of(nuevo, anterior), 2026, new BigDecimal("25.50"));
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(contenido))) {
            var resumen = workbook.getSheet("INFORME ECONÓMICO");
            var evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            var total = buscarFilaEnColumna(resumen, 1, "TOTAL");
            assertThat(evaluator.evaluate(total.getCell(2)).getNumberValue()).isEqualTo(2);
            assertThat(evaluator.evaluate(total.getCell(3)).getNumberValue()).isEqualTo(150);
            assertThat(evaluator.evaluate(total.getCell(4)).getNumberValue()).isEqualTo(60);
            assertThat(buscarFilaEnColumna(resumen, 1, "GASTOS DE PERSONAL CEC").getCell(4).getNumericCellValue()).isEqualTo(25.5);
            var utilidad = buscarFilaEnColumna(resumen, 1, "UTILIDAD CEC 2026:");
            assertThat(evaluator.evaluate(utilidad.getCell(5)).getNumberValue()).isEqualTo(64.5);
        }
    }

    private InformeEconomico crearInforme() {
        Curso curso = Curso.builder().idCurso(1L).codigo("CEC-01").nombre("Curso de prueba").build();
        Coordinador coordinador = Coordinador.builder().nombres("Ana").apellidos("Coordinadora").build();
        Especialista especialista = Especialista.builder().nombres("Luis").apellidos("Especialista").build();
        Planificacion planificacion = Planificacion.builder()
                .id(10L).curso(curso).coordinador(coordinador).especialista(especialista)
                .fechaInicio(LocalDate.of(2026, 1, 1)).fechaFin(LocalDate.of(2026, 1, 31))
                .modalidad(Modalidad.VIRTUAL).build();
        InformeEconomico informe = InformeEconomico.builder()
                .id(20L).planificacion(planificacion).participantes(0)
                .ingresosTotal(new BigDecimal("80.00"))
                .egresosTotal(new BigDecimal("3500.00"))
                .utilidad(new BigDecimal("-3420.00"))
                .historico(true).nombreReporte("Curso de prueba - Segunda programación")
                .build();

        informe.setLineasIngreso(List.of(InformeEconomicoLineaIngreso.builder()
                .informeEconomico(informe).tipoUsuario(TipoUsuario.EXTERNO).etiqueta("Retirados")
                .cantidad(0).valorUnitario(new BigDecimal("80.00")).total(new BigDecimal("80.00"))
                .build()));
        informe.setLineasEgreso(List.of(
                InformeEconomicoLineaEgreso.builder().informeEconomico(informe).concepto("Especialista")
                        .cantidad(1).valorUnitario(new BigDecimal("8.00")).total(new BigDecimal("800.00")).build(),
                InformeEconomicoLineaEgreso.builder().informeEconomico(informe).concepto("Comisiones")
                        .cantidad(135).valorUnitario(new BigDecimal("2700.00")).total(new BigDecimal("2700.00")).build()
        ));
        return informe;
    }

    private InformeEconomico crearInformeHistorico(long id, String codigo, String nombre,
                                                     LocalDate fechaInicio, int participantes,
                                                     BigDecimal ingresos, BigDecimal egresos) {
        Curso curso = Curso.builder().idCurso(id + 1000).codigo(codigo).nombre(nombre).build();
        Coordinador coordinador = Coordinador.builder().nombres("Ana").apellidos("Coordinadora").build();
        Especialista especialista = Especialista.builder().nombres("Luis").apellidos("Especialista").build();
        Planificacion planificacion = Planificacion.builder()
                .id(id + 2000).curso(curso).coordinador(coordinador).especialista(especialista)
                .fechaInicio(fechaInicio).fechaFin(fechaInicio.plusMonths(1))
                .modalidad(Modalidad.VIRTUAL).build();
        InformeEconomico informe = InformeEconomico.builder()
                .id(id).planificacion(planificacion).participantes(participantes)
                .ingresosTotal(ingresos).egresosTotal(egresos)
                .utilidad(ingresos.subtract(egresos)).historico(true)
                .nombreReporte(nombre).build();
        informe.setLineasIngreso(List.of(InformeEconomicoLineaIngreso.builder()
                .informeEconomico(informe).tipoUsuario(TipoUsuario.EXTERNO).etiqueta("Externos")
                .cantidad(participantes).valorUnitario(BigDecimal.ZERO).total(ingresos).build()));
        informe.setLineasEgreso(List.of(InformeEconomicoLineaEgreso.builder()
                .informeEconomico(informe).concepto("Especialista").cantidad(1)
                .valorUnitario(egresos).total(egresos).build()));
        return informe;
    }

    private Row buscarFila(Sheet sheet, String detalle) {
        return buscarFilaEnColumna(sheet, 2, detalle);
    }

    private Row buscarFilaEnColumna(Sheet sheet, int columna, String detalle) {
        for (Row row : sheet) {
            Cell cell = row.getCell(columna);
            if (cell != null && cell.getCellType() == CellType.STRING
                    && detalle.equals(cell.getStringCellValue())) {
                return row;
            }
        }
        throw new AssertionError("No se encontró la fila " + detalle);
    }
}
