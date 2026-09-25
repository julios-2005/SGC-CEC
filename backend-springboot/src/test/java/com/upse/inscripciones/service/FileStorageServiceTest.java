package com.upse.inscripciones.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FileStorageServiceTest {
    @TempDir Path root;
    private FileStorageService storage;
    private Path uploads;

    @BeforeEach
    void setup() {
        uploads = root.resolve("uploads");
        storage = new FileStorageService();
        ReflectionTestUtils.setField(storage, "uploadDir", uploads.toString());
    }

    @Test
    void documentosDeLaMismaPersonaNoSeSobrescriben() throws Exception {
        byte[] original = "%PDF-1.4\nprimer documento\n%%EOF".getBytes(StandardCharsets.UTF_8);
        byte[] nuevo = "%PDF-1.4\nsegundo documento\n%%EOF".getBytes(StandardCharsets.UTF_8);
        String uno = storage.guardarArchivo(new MockMultipartFile("file", "pago.pdf", "application/pdf", original),
                "comprobantes-pago", "Comprobante", "1234567890", "Persona de Prueba");
        String dos = storage.guardarArchivo(new MockMultipartFile("file", "pago.pdf", "application/pdf", nuevo),
                "comprobantes-pago", "Comprobante", "1234567890", "Persona de Prueba");
        assertThat(uno).isNotEqualTo(dos);
        assertThat(Files.readAllBytes(uploads.resolve(uno))).isEqualTo(original);
        assertThat(Files.readAllBytes(uploads.resolve(dos))).isEqualTo(nuevo);
    }

    @Test
    void comprobantesGeneradosParaCursosDistintosTienenRutasIndependientes() throws Exception {
        String uno = storage.guardarBytes(new byte[]{1}, "comprobantes-matricula-generados", "1234567890_matricula.pdf");
        String dos = storage.guardarBytes(new byte[]{2}, "comprobantes-matricula-generados", "1234567890_matricula.pdf");
        assertThat(uno).isNotEqualTo(dos);
        assertThat(Files.readAllBytes(uploads.resolve(uno))).containsExactly((byte) 1);
    }

    @Test
    void unRollbackSoloEliminaElArchivoNuevo() throws Exception {
        String previo = storage.guardarBytes(new byte[]{1}, "comprobantes-pago", "documento.pdf");
        TransactionSynchronizationManager.initSynchronization();
        try {
            String nuevo = storage.guardarBytes(new byte[]{2}, "comprobantes-pago", "documento.pdf");
            for (TransactionSynchronization callback : TransactionSynchronizationManager.getSynchronizations()) {
                callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }
            assertThat(Files.exists(uploads.resolve(nuevo))).isFalse();
            assertThat(Files.exists(uploads.resolve(previo))).isTrue();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void limpiezaNoPuedeBorrarFueraDeUploads() throws Exception {
        Path original = root.resolve("original.txt");
        Files.writeString(original, "conservar");
        storage.eliminarSiExiste("../original.txt");
        assertThat(Files.readString(original)).isEqualTo("conservar");
    }
}
