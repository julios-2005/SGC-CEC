package com.upse.inscripciones.controller;

import com.upse.inscripciones.entity.EstadoCurso;
import com.upse.inscripciones.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ParameterErrorsTest {
    private MockMvc mvc;
    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new Controller()).setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @Test void enumInvalidoDevuelve400EnLugarDe500() throws Exception {
        mvc.perform(get("/prueba").param("estado", "TERMINADO"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.exito").value(false));
    }
    @Test void parametroFaltanteDevuelve400EnLugarDe500() throws Exception {
        mvc.perform(get("/prueba")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensaje").value("Falta el campo obligatorio 'estado'."));
    }
    @RestController static class Controller {
        @GetMapping("/prueba") public String test(@RequestParam("estado") EstadoCurso estado) { return estado.name(); }
    }
}
