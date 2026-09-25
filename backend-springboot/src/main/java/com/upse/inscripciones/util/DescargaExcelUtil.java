package com.upse.inscripciones.util;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

/** Arma la respuesta HTTP de descarga de un .xlsx, igual en todos los controllers que exportan a Excel. */
public final class DescargaExcelUtil {

    private DescargaExcelUtil() {}

    public static ResponseEntity<byte[]> responder(byte[] contenidoExcel, String nombreArchivo) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(
                ContentDisposition.attachment()
                        .filename(nombreArchivo, StandardCharsets.UTF_8)
                        .build());
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        return ResponseEntity.ok().headers(headers).body(contenidoExcel);
    }
}
