package com.upse.inscripciones.service;

import com.upse.inscripciones.entity.InformeEconomico;
import com.upse.inscripciones.entity.InformeEconomicoLineaEgreso;
import com.upse.inscripciones.entity.InformeEconomicoLineaIngreso;
import com.upse.inscripciones.entity.Planificacion;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Arma el archivo .xlsx del Informe Económico replicando el formato del
 * archivo histórico que maneja el CEC en Excel a mano (una hoja por
 * curso/planificación + una hoja resumen al final).
 * <p>
 * Cada hoja de curso replica la estructura del archivo de ejemplo del CEC:
 * encabezado con datos del curso, tabla de INGRESOS (una fila por tipo de
 * participante), tabla de EGRESOS, y la fila de UTILIDAD resaltada en
 * amarillo. La hoja resumen final referencia con fórmulas las celdas de
 * cada hoja de curso (igual que el archivo de ejemplo), no valores fijos,
 * para que se pueda seguir auditando/recalculando manualmente en Excel.
 */
@Service
public class InformeEconomicoExcelService {

    // Mismo formato de moneda que usa el archivo de ejemplo del CEC.
    private static final String FORMATO_MONEDA =
            "_-[$$-409]* #,##0.00_ ;_-[$$-409]* -#,##0.00 ;_-[$$-409]* \"-\"??_ ;_-@_ ";

    private static final DateTimeFormatter FORMATO_DIA = DateTimeFormatter.ofPattern("dd");
    private static final Locale LOCALE_EC = Locale.forLanguageTag("es-EC");

    /** Dónde quedaron, dentro de la hoja de un curso, las filas con los totales. */
    private record UbicacionHoja(String nombreHoja, int filaTotalIngresos, int filaTotalEgresos) {}

    public byte[] generarExcel(List<InformeEconomico> informes, Integer anio, BigDecimal gastosCec) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Estilos estilos = new Estilos(workbook);

            Map<Long, UbicacionHoja> ubicaciones = new LinkedHashMap<>();
            Set<String> nombresUsados = new HashSet<>();

            for (InformeEconomico informe : informes) {
                String nombreHoja = nombreHojaUnico(informe, nombresUsados);
                Sheet sheet = workbook.createSheet(nombreHoja);
                int[] filasTotal = escribirHojaCurso(sheet, informe, estilos);
                ubicaciones.put(informe.getClaveReporte(),
                        new UbicacionHoja(nombreHoja, filasTotal[0], filasTotal[1]));
            }

            escribirHojaResumen(workbook, informes, ubicaciones, estilos, anio, gastosCec);
            workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
            workbook.setForceFormulaRecalculation(true);

            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            workbook.write(salida);
            return salida.toByteArray();
        }
    }

    // ── Hoja individual de un curso ──────────────────────────────────────────
    // Devuelve {filaTotalIngresos, filaTotalEgresos} (0-indexed, como usa POI)
    // para que generarExcel arme las referencias cruzadas de la hoja resumen.
    private int[] escribirHojaCurso(Sheet sheet, InformeEconomico informe, Estilos estilos) {
        Planificacion p = informe.getPlanificacion();
        sheet.setColumnWidth(1, 17 * 256);   // B
        sheet.setColumnWidth(2, 34 * 256);   // C (nombres de tipo de participante / concepto)
        sheet.setColumnWidth(3, 26 * 256);   // Cantidad de participantes
        sheet.setColumnWidth(4, 14 * 256);   // E (valor)
        sheet.setColumnWidth(5, 16 * 256);   // F (total)

        int fila = 1; // fila 2 en Excel (1-indexed), igual que el archivo de ejemplo

        fila = escribirEncabezado(sheet, fila, informe, estilos);
        fila++; // fila en blanco antes de la tabla, igual que el ejemplo

        // ── INGRESOS ──
        int filaHeaderIngresos = fila;
        escribirEncabezadoTabla(sheet, filaHeaderIngresos, "INGRESOS:", estilos);
        int filaPrimerDatoIngreso = filaHeaderIngresos + 1;
        int filaActual = filaPrimerDatoIngreso;
        for (InformeEconomicoLineaIngreso linea : informe.getLineasIngreso()) {
            escribirFilaTabla(sheet, filaActual,
                    linea.getDetalleMostrar().toUpperCase(LOCALE_EC),
                    linea.getCantidad(), linea.getValorUnitario(), linea.getTotal(), estilos);
            filaActual++;
        }
        int filaTotalIngresos = filaActual;
        escribirFilaTotal(sheet, filaTotalIngresos, filaPrimerDatoIngreso, filaTotalIngresos - 1, true, estilos);

        fila = filaTotalIngresos + 2; // una fila en blanco después del total

        // ── EGRESOS ──
        int filaHeaderEgresos = fila;
        escribirEncabezadoTabla(sheet, filaHeaderEgresos, "EGRESOS:", estilos);
        int filaPrimerDatoEgreso = filaHeaderEgresos + 1;
        filaActual = filaPrimerDatoEgreso;
        for (InformeEconomicoLineaEgreso linea : informe.getLineasEgreso()) {
            escribirFilaTabla(sheet, filaActual, linea.getConcepto().trim().toUpperCase(LOCALE_EC),
                    linea.getCantidad(), linea.getValorUnitarioMostrar(), linea.getTotal(), estilos);
            filaActual++;
        }
        int filaTotalEgresos = filaActual;
        escribirFilaTotal(sheet, filaTotalEgresos, filaPrimerDatoEgreso, filaTotalEgresos - 1, false, estilos);

        // ── UTILIDAD ──
        int filaUtilidad = filaTotalEgresos + 2;
        Row filaU = sheet.createRow(filaUtilidad);
        Cell etiquetaU = filaU.createCell(1); // B
        etiquetaU.setCellValue("UTILIDAD:");
        etiquetaU.setCellStyle(estilos.utilidadEtiqueta);
        for (int col = 2; col <= 4; col++) {
            Cell relleno = filaU.createCell(col);
            relleno.setCellStyle(estilos.utilidadRelleno);
        }
        Cell valorU = filaU.createCell(5); // F
        valorU.setCellFormula("F" + (filaTotalIngresos + 1) + "-F" + (filaTotalEgresos + 1));
        valorU.setCellStyle(estilos.utilidadValor);

        if(informe.isCifrasPendientes()){
            sinInformacion(sheet.getRow(filaTotalIngresos).getCell(3));
            sinInformacion(sheet.getRow(filaTotalIngresos).getCell(5));
            sinInformacion(sheet.getRow(filaTotalEgresos).getCell(5));sinInformacion(valorU);
        }
        if(informe.getObservacionReferencia()!=null)crearFilaTexto(sheet,filaUtilidad+2,informe.getObservacionReferencia(),estilos.celdaTexto);
        return new int[]{filaTotalIngresos, filaTotalEgresos};
    }

    private int escribirEncabezado(Sheet sheet, int filaInicio, InformeEconomico informe, Estilos estilos) {
        Planificacion p = informe.getPlanificacion();
        int fila = filaInicio;

        crearFilaTexto(sheet, fila, "CURSO: " + nombreVisible(informe), estilos.tituloCurso);
        sheet.addMergedRegion(new CellRangeAddress(fila, fila, 1, 5));
        fila++;

        String nombreEspecialista = informe.getDocentesReporte()==null?"Sin especificar en Excel":informe.getDocentesReporte();
        crearFilaTexto(sheet, fila, "DOCENTE: " + nombreEspecialista, estilos.tituloCurso);
        sheet.addMergedRegion(new CellRangeAddress(fila, fila, 1, 5));
        fila++;

        String nombreCoordinador = informe.getCoordinadorReporte()==null?"Sin especificar en Excel":informe.getCoordinadorReporte();
        crearFilaTexto(sheet, fila, "COORDINADOR (a): " + nombreCoordinador, estilos.tituloCurso);
        sheet.addMergedRegion(new CellRangeAddress(fila, fila, 1, 5));
        fila++;

        Row filaFechas = sheet.createRow(fila);
        Cell etiquetaFechas = filaFechas.createCell(1);
        etiquetaFechas.setCellValue("INICIO/FIN:");
        etiquetaFechas.setCellStyle(estilos.tituloCurso);
        Cell valorFechas = filaFechas.createCell(2);
        valorFechas.setCellValue(formatearRangoFechas(informe));
        valorFechas.setCellStyle(estilos.tituloCursoCentrado);
        sheet.addMergedRegion(new CellRangeAddress(fila, fila, 2, 5));
        fila++;

        return fila;
    }

    private String formatearRangoFechas(InformeEconomico informe) {
        if(informe.getInicioReporte()==null || informe.getFinReporte()==null)return "Sin fechas especificadas";
        String mesInicio = informe.getInicioReporte().getMonth().getDisplayName(TextStyle.FULL, LOCALE_EC).toUpperCase(LOCALE_EC);
        String mesFin = informe.getFinReporte().getMonth().getDisplayName(TextStyle.FULL, LOCALE_EC).toUpperCase(LOCALE_EC);
        String diaInicio = informe.getInicioReporte().format(FORMATO_DIA);
        String diaFin = informe.getFinReporte().format(FORMATO_DIA);
        if (mesInicio.equals(mesFin)) {
            return mesInicio + " " + diaInicio + " - " + diaFin;
        }
        return mesInicio + " " + diaInicio + " - " + mesFin + " " + diaFin;
    }

    private void escribirEncabezadoTabla(Sheet sheet, int fila, String etiqueta, Estilos estilos) {
        Row row = sheet.createRow(fila);
        String[] encabezados = {etiqueta, "DETALLE", etiqueta.startsWith("INGRESOS") ? "CANTIDAD DE PARTICIPANTES" : "CANTIDAD", "VALOR", "TOTAL"};
        for (int i = 0; i < encabezados.length; i++) {
            Cell c = row.createCell(1 + i); // arranca en columna B
            c.setCellValue(encabezados[i]);
            c.setCellStyle(estilos.encabezadoTabla);
        }
    }

    private void escribirFilaTabla(Sheet sheet, int fila, String detalle, Integer cantidad,
                                   BigDecimal valorUnitario, BigDecimal total, Estilos estilos) {
        Row row = sheet.createRow(fila);

        Cell cDetalle = row.createCell(2); // C
        cDetalle.setCellValue(detalle);
        cDetalle.setCellStyle(estilos.celdaCentrada);

        Cell cCantidad = row.createCell(3); // D
        if (cantidad != null) cCantidad.setCellValue(cantidad);
        cCantidad.setCellStyle(estilos.celdaCentrada);

        Cell cValor = row.createCell(4); // E
        if(valorUnitario!=null)cValor.setCellValue(valorUnitario.doubleValue());
        cValor.setCellStyle(estilos.celdaCentrada);

        Cell cTotal = row.createCell(5); // F
        int filaExcel = fila + 1; // fórmula en notación Excel (1-indexed)
        // Líneas históricas migradas del Excel (p. ej. RETIRADOS, COMISIONES)
        // pueden no tener cantidad de participantes, solo el total global; en
        // ese caso no hay cantidad × valor que calcular vía fórmula.
        BigDecimal producto = (valorUnitario == null || cantidad == null)
                ? null : valorUnitario.multiply(BigDecimal.valueOf(cantidad));
        if (producto!=null && producto.compareTo(total) == 0) {
            cTotal.setCellFormula("D" + filaExcel + "*E" + filaExcel);
        } else {
            // Algunas líneas históricas son valores globales (p. ej. RETIRADOS
            // o COMISIONES) y no cantidad × valor unitario. Se conserva el total
            // oficial del libro en vez de recalcularlo con una fórmula incorrecta.
            cTotal.setCellValue(total.doubleValue());
        }
        cTotal.setCellStyle(estilos.celdaMoneda);
    }

    private void escribirFilaTotal(Sheet sheet, int fila, int filaDatoInicio, int filaDatoFin, boolean incluirSumaCantidad, Estilos estilos) {
        Row row = sheet.createRow(fila);

        Cell cLabel = row.createCell(2); // C
        cLabel.setCellValue("TOTAL");
        cLabel.setCellStyle(estilos.totalEtiqueta);

        int inicioExcel = filaDatoInicio + 1;
        int finExcel = filaDatoFin + 1;

        if (incluirSumaCantidad) {
            Cell cCantidad = row.createCell(3); // D
            cCantidad.setCellFormula(filaDatoFin<filaDatoInicio?"0":"SUM(D" + inicioExcel + ":D" + finExcel + ")");
            cCantidad.setCellStyle(estilos.totalEtiqueta);
        }

        Cell cTotal = row.createCell(5); // F
        cTotal.setCellFormula(filaDatoFin<filaDatoInicio?"0":"SUM(F" + inicioExcel + ":F" + finExcel + ")");
        cTotal.setCellStyle(estilos.totalValor);
    }

    private void crearFilaTexto(Sheet sheet, int fila, String texto, CellStyle estilo) {
        Row row = sheet.createRow(fila);
        Cell c = row.createCell(1); // B
        c.setCellValue(texto);
        c.setCellStyle(estilo);
    }

    // ── Hoja resumen (última hoja del libro) ─────────────────────────────────

    private void escribirHojaResumen(XSSFWorkbook workbook, List<InformeEconomico> informes,
                                      Map<Long, UbicacionHoja> ubicaciones, Estilos estilos,
                                      Integer anio, BigDecimal gastosCec) {
        Sheet sheet = workbook.createSheet("INFORME ECONÓMICO");
        sheet.setColumnWidth(0, 6 * 256);    // A - No.
        sheet.setColumnWidth(1, 45 * 256);   // B - Curso
        sheet.setColumnWidth(2, 20 * 256);   // C - # participantes
        sheet.setColumnWidth(3, 18 * 256);   // D - Ingresos
        sheet.setColumnWidth(4, 18 * 256);   // E - Egresos
        sheet.setColumnWidth(5, 18 * 256);   // F - Utilidad

        int filaHeader = 2; // fila 3, igual que el archivo de ejemplo
        Row header = sheet.createRow(filaHeader);
        String[] encabezados = {"No.", "CURSOS/DIPLOMADOS", "CANTIDAD DE PARTICIPANTES", "INGRESOS", "EGRESOS", "UTILIDAD"};
        for (int i = 0; i < encabezados.length; i++) {
            Cell c = header.createCell(i);
            c.setCellValue(encabezados[i]);
            c.setCellStyle(estilos.encabezadoTabla);
        }

        int fila = filaHeader + 1;
        int numero = 1;
        List<Integer> filasParticipantesCec2026 = new ArrayList<>();
        List<Integer> filasIngresosCec2026 = new ArrayList<>();
        List<Integer> filasEgresosCec2026 = new ArrayList<>();
        List<InformeEconomico> informesCec2026 = new ArrayList<>();

        for (InformeEconomico informe : informes) {
            UbicacionHoja ubicacion = ubicaciones.get(informe.getClaveReporte());
            String hojaRef = "'" + ubicacion.nombreHoja().replace("'", "''") + "'";
            int filaTotalIngresosExcel = ubicacion.filaTotalIngresos() + 1;
            int filaTotalEgresosExcel = ubicacion.filaTotalEgresos() + 1;

            Row row = sheet.createRow(fila);

            Cell cNo = row.createCell(0);
            cNo.setCellValue(numero);
            cNo.setCellStyle(estilos.celdaCentrada);

            Cell cCurso = row.createCell(1);
            cCurso.setCellValue(nombreVisible(informe));
            cCurso.setCellStyle(estilos.celdaTexto);

            Cell cParticipantes = row.createCell(2);
            cParticipantes.setCellFormula(hojaRef + "!D" + filaTotalIngresosExcel);
            cParticipantes.setCellStyle(estilos.celdaCentrada);

            Cell cIngresos = row.createCell(3);
            cIngresos.setCellFormula(hojaRef + "!F" + filaTotalIngresosExcel);
            cIngresos.setCellStyle(estilos.celdaMoneda);

            Cell cEgresos = row.createCell(4);
            cEgresos.setCellStyle(estilos.celdaMoneda);

            Cell cUtilidad = row.createCell(5);
            cUtilidad.setCellStyle(estilos.celdaMoneda);

            cEgresos.setCellFormula(hojaRef + "!F" + filaTotalEgresosExcel);
            int filaExcelActual = fila + 1;
            cUtilidad.setCellFormula("D" + filaExcelActual + "-E" + filaExcelActual);
            if(informe.isCifrasPendientes()){
                sinInformacion(cParticipantes);sinInformacion(cIngresos);sinInformacion(cEgresos);sinInformacion(cUtilidad);
            }

            if (anio != null && informe.getAnioReporte() == anio && !(anio==2026 && informe.isExcluido2026())) {
                filasParticipantesCec2026.add(filaExcelActual);
                filasIngresosCec2026.add(filaExcelActual);
                filasEgresosCec2026.add(filaExcelActual);
                informesCec2026.add(informe);
            }

            fila++;
            numero++;
        }

        // Fila de totales generales al final
        int primeraFilaDatosExcel = filaHeader + 2;
        int ultimaFilaDatosExcel = fila; // "fila" ya quedó apuntando a la próxima libre => es la última fila con datos en notación Excel
        int filaTotales = fila + 1;
        boolean consolidadoCompleto = anio != null;
        Row rowTotales = sheet.createRow(filaTotales);
        Cell cEtiquetaTotales = rowTotales.createCell(1);
        cEtiquetaTotales.setCellValue("TOTAL");
        cEtiquetaTotales.setCellStyle(estilos.totalEtiqueta);

        String[] letrasColumnaTotal = {"C", "D", "E"};
        List<List<Integer>> filasPorColumna = List.of(
                filasParticipantesCec2026,
                filasIngresosCec2026,
                filasEgresosCec2026
        );
        for (int i = 0; i < letrasColumnaTotal.length; i++) {
            Cell c = rowTotales.createCell(2 + i);
            String letra = letrasColumnaTotal[i];
            c.setCellFormula(consolidadoCompleto
                    ? formulaSumaFilas(letra, filasPorColumna.get(i))
                    : "SUM(" + letra + primeraFilaDatosExcel + ":" + letra + ultimaFilaDatosExcel + ")");
            c.setCellStyle(i == 0 ? estilos.totalCantidad : estilos.totalValor);
        }
        Cell cUtilidadTotal = rowTotales.createCell(5);
        int filaTotalesExcel = filaTotales + 1;
        // La utilidad consolidada se obtiene siempre de ingresos menos egresos.
        cUtilidadTotal.setCellFormula("D" + filaTotalesExcel + "-E" + filaTotalesExcel);
        cUtilidadTotal.setCellStyle(estilos.totalValor);

        if (consolidadoCompleto) {
            int filaGastosPersonal = filaTotales + 1;
            Row gastos = sheet.createRow(filaGastosPersonal);
            Cell etiquetaGastos = gastos.createCell(1);
            etiquetaGastos.setCellValue("GASTOS DE PERSONAL CEC");
            etiquetaGastos.setCellStyle(estilos.totalEtiqueta);
            Cell valorGastos = gastos.createCell(4);
            valorGastos.setCellValue(gastosCec.doubleValue());
            valorGastos.setCellStyle(estilos.totalValor);

            int filaSubtotalExcel = filaTotales + 1;
            int filaGastosPersonalExcel = filaGastosPersonal + 1;
            int filaUtilidadCec = filaGastosPersonal + 2;
            Row utilidadCec = sheet.createRow(filaUtilidadCec);

            Cell etiquetaUtilidadCec = utilidadCec.createCell(1);
            etiquetaUtilidadCec.setCellValue("UTILIDAD CEC " + anio + ":");
            etiquetaUtilidadCec.setCellStyle(estilos.utilidadEtiqueta);

            for (int col = 2; col <= 4; col++) {
                Cell relleno = utilidadCec.createCell(col);
                relleno.setCellStyle(estilos.utilidadRelleno);
            }

            Cell valorUtilidadCec = utilidadCec.createCell(5);
            valorUtilidadCec.setCellFormula("F" + filaSubtotalExcel + "-E" + filaGastosPersonalExcel);
            valorUtilidadCec.setCellStyle(estilos.utilidadValor);
        }

        sheet.createFreezePane(0, filaHeader + 1);
    }

    private String formulaSumaFilas(String columna, List<Integer> filasExcel) {
        if (filasExcel.isEmpty()) return "0";
        return filasExcel.stream()
                .map(fila -> columna + fila)
                .collect(Collectors.joining(",", "SUM(", ")"));
    }

    // ── Nombre de hoja válido y único ────────────────────────────────────────
    // Excel prohíbe los caracteres \ / ? * [ ] : en nombres de hoja y limita a
    // 31 caracteres. Si dos cursos truncados quedan con el mismo nombre, se
    // le agrega un sufijo numérico para no pisar una hoja con otra.
    private String nombreHojaUnico(InformeEconomico informe, Set<String> nombresUsados) {
        String base = nombreVisible(informe)
                .replaceAll("[\\\\/?*\\[\\]:]", " ")
                .trim();
        if (base.isEmpty()) {
            base = "Curso";
        }
        String candidato = base.length() > 31 ? base.substring(0, 31).trim() : base;

        int sufijo = 2;
        String original = candidato;
        while (nombresUsados.contains(candidato.toUpperCase(Locale.ROOT))) {
            String sufijoTexto = " (" + sufijo + ")";
            int maxBase = 31 - sufijoTexto.length();
            String baseRecortada = original.length() > maxBase ? original.substring(0, maxBase).trim() : original;
            candidato = baseRecortada + sufijoTexto;
            sufijo++;
        }
        nombresUsados.add(candidato.toUpperCase(Locale.ROOT));
        return candidato;
    }

    private void sinInformacion(Cell cell){cell.setBlank();cell.setCellValue("Sin información");}
    private String nombreVisible(InformeEconomico informe) {
        return informe.getNombreReporte() == null || informe.getNombreReporte().isBlank()
                ? informe.getNombreBaseReporte()
                : informe.getNombreReporte();
    }

    // ── Estilos (se crean una sola vez por libro, no por celda) ──────────────

    private static class Estilos {
        final CellStyle tituloCurso;
        final CellStyle tituloCursoCentrado;
        final CellStyle encabezadoTabla;
        final CellStyle celdaCentrada;
        final CellStyle celdaTexto;
        final CellStyle celdaMoneda;
        final CellStyle totalEtiqueta;
        final CellStyle totalCantidad;
        final CellStyle totalValor;
        final CellStyle utilidadEtiqueta;
        final CellStyle utilidadValor;
        final CellStyle utilidadRelleno;

        Estilos(Workbook wb) {
            Font fuenteTitulo = wb.createFont();
            fuenteTitulo.setFontName("Arial");
            fuenteTitulo.setBold(true);
            fuenteTitulo.setFontHeightInPoints((short) 12);

            Font fuenteNormal = wb.createFont();
            fuenteNormal.setFontName("Arial");
            fuenteNormal.setFontHeightInPoints((short) 11);

            Font fuenteHeaderTabla = wb.createFont();
            fuenteHeaderTabla.setFontName("Arial");
            fuenteHeaderTabla.setBold(true);
            fuenteHeaderTabla.setFontHeightInPoints((short) 11);

            Font fuenteBold = wb.createFont();
            fuenteBold.setFontName("Arial");
            fuenteBold.setBold(true);
            fuenteBold.setFontHeightInPoints((short) 11);

            DataFormat formatoDatos = wb.createDataFormat();
            short idFormatoMoneda = formatoDatos.getFormat(FORMATO_MONEDA);

            tituloCurso = wb.createCellStyle();
            tituloCurso.setFont(fuenteTitulo);

            tituloCursoCentrado = wb.createCellStyle();
            tituloCursoCentrado.setFont(fuenteTitulo);
            tituloCursoCentrado.setAlignment(HorizontalAlignment.CENTER);

            encabezadoTabla = wb.createCellStyle();
            encabezadoTabla.setFont(fuenteHeaderTabla);
            encabezadoTabla.setAlignment(HorizontalAlignment.CENTER);
            encabezadoTabla.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            encabezadoTabla.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            celdaCentrada = wb.createCellStyle();
            celdaCentrada.setFont(fuenteNormal);
            celdaCentrada.setAlignment(HorizontalAlignment.CENTER);

            celdaTexto = wb.createCellStyle();
            celdaTexto.setFont(fuenteNormal);

            celdaMoneda = wb.createCellStyle();
            celdaMoneda.setFont(fuenteNormal);
            celdaMoneda.setAlignment(HorizontalAlignment.CENTER);
            celdaMoneda.setDataFormat(idFormatoMoneda);

            totalEtiqueta = wb.createCellStyle();
            totalEtiqueta.setFont(fuenteNormal);
            totalEtiqueta.setAlignment(HorizontalAlignment.CENTER);

            totalCantidad = wb.createCellStyle();
            totalCantidad.setFont(fuenteBold);
            totalCantidad.setAlignment(HorizontalAlignment.CENTER);
            totalCantidad.setBorderTop(BorderStyle.MEDIUM);
            totalCantidad.setBorderBottom(BorderStyle.MEDIUM);
            totalCantidad.setBorderRight(BorderStyle.MEDIUM);

            totalValor = wb.createCellStyle();
            totalValor.setFont(fuenteBold);
            totalValor.setAlignment(HorizontalAlignment.CENTER);
            totalValor.setDataFormat(idFormatoMoneda);
            totalValor.setBorderTop(BorderStyle.MEDIUM);
            totalValor.setBorderBottom(BorderStyle.MEDIUM);
            totalValor.setBorderRight(BorderStyle.MEDIUM);

            utilidadEtiqueta = wb.createCellStyle();
            utilidadEtiqueta.setFont(fuenteBold);
            utilidadEtiqueta.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
            utilidadEtiqueta.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            utilidadEtiqueta.setBorderTop(BorderStyle.MEDIUM);
            utilidadEtiqueta.setBorderBottom(BorderStyle.MEDIUM);
            utilidadEtiqueta.setBorderLeft(BorderStyle.MEDIUM);

            utilidadRelleno = wb.createCellStyle();
            utilidadRelleno.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
            utilidadRelleno.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            utilidadRelleno.setBorderTop(BorderStyle.MEDIUM);
            utilidadRelleno.setBorderBottom(BorderStyle.MEDIUM);

            utilidadValor = wb.createCellStyle();
            utilidadValor.setFont(fuenteBold);
            utilidadValor.setAlignment(HorizontalAlignment.CENTER);
            utilidadValor.setDataFormat(idFormatoMoneda);
            utilidadValor.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
            utilidadValor.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            utilidadValor.setBorderTop(BorderStyle.MEDIUM);
            utilidadValor.setBorderBottom(BorderStyle.MEDIUM);
            utilidadValor.setBorderRight(BorderStyle.MEDIUM);
        }
    }
}
