package com.upse.inscripciones.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Catálogo centralizado de nacionalidades para el campo
 * {@code Especialista#paisNacionalidad}.
 * <p>
 * Se guarda el código ISO 3166-1 alpha-2 del país (ej. "EC", "CO") en vez
 * de un texto libre como "Colombiano" / "Colombiana" / "Colombiano/a". Con
 * el código, filtrar o agrupar por nacionalidad es una comparación exacta
 * ({@code WHERE pais_nacionalidad = 'CO'}) sin importar cómo alguien haya
 * escrito el gentilicio; el texto para mostrar en pantalla se calcula acá,
 * en un solo lugar, en su forma neutra ("Colombiano/a") para no requerir
 * pedir el sexo del especialista solo para esto.
 * <p>
 * Lista corta y ampliable: cubre Ecuador y los países de origen más
 * comunes para especialistas extranjeros de la región. Agregar un país
 * nuevo es una sola línea en {@link #GENTILICIOS}.
 */
public final class Nacionalidades {

    private Nacionalidades() {
    }

    private static final Map<String, String[]> GENTILICIOS = new LinkedHashMap<>();

    static {
        // código ISO -> { nombre del país, gentilicio neutro }
        GENTILICIOS.put("EC", new String[]{"Ecuador", "Ecuatoriano/a"});
        GENTILICIOS.put("CO", new String[]{"Colombia", "Colombiano/a"});
        GENTILICIOS.put("PE", new String[]{"Perú", "Peruano/a"});
        GENTILICIOS.put("VE", new String[]{"Venezuela", "Venezolano/a"});
        GENTILICIOS.put("BO", new String[]{"Bolivia", "Boliviano/a"});
        GENTILICIOS.put("CL", new String[]{"Chile", "Chileno/a"});
        GENTILICIOS.put("AR", new String[]{"Argentina", "Argentino/a"});
        GENTILICIOS.put("BR", new String[]{"Brasil", "Brasileño/a"});
        GENTILICIOS.put("PY", new String[]{"Paraguay", "Paraguayo/a"});
        GENTILICIOS.put("UY", new String[]{"Uruguay", "Uruguayo/a"});
        GENTILICIOS.put("MX", new String[]{"México", "Mexicano/a"});
        GENTILICIOS.put("CU", new String[]{"Cuba", "Cubano/a"});
        GENTILICIOS.put("PA", new String[]{"Panamá", "Panameño/a"});
        GENTILICIOS.put("CR", new String[]{"Costa Rica", "Costarricense"});
        GENTILICIOS.put("ES", new String[]{"España", "Español/a"});
        GENTILICIOS.put("US", new String[]{"Estados Unidos", "Estadounidense"});
        GENTILICIOS.put("OTRO", new String[]{"Otro país", "Otra nacionalidad"});
    }

    public static boolean esCodigoValido(String codigoIso) {
        return codigoIso != null && GENTILICIOS.containsKey(codigoIso.toUpperCase());
    }

    public static String nombrePais(String codigoIso) {
        String[] datos = GENTILICIOS.get(codigoIso == null ? null : codigoIso.toUpperCase());
        return datos != null ? datos[0] : null;
    }

    /**
     * Texto a mostrar en listados/reportes, ej. "Colombiano/a". Devuelve
     * el propio código si no está en el catálogo, para no ocultar datos
     * antiguos o mal cargados en vez de mostrarlos en blanco.
     */
    public static String gentilicio(String codigoIso) {
        if (codigoIso == null || codigoIso.isBlank()) {
            return null;
        }
        String[] datos = GENTILICIOS.get(codigoIso.toUpperCase());
        return datos != null ? datos[1] : codigoIso;
    }

    public static Map<String, String[]> todas() {
        return GENTILICIOS;
    }
}
