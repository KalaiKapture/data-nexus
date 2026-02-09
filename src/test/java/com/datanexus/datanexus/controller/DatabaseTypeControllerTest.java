package com.datanexus.datanexus.controller;

import com.datanexus.datanexus.dto.DatabaseTypeDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test for DatabaseTypeController
 */
@WebMvcTest(DatabaseTypeController.class)
class DatabaseTypeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testGetAllDatabaseTypes() throws Exception {
        mockMvc.perform(get("/api/database-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].name").exists())
                .andExpect(jsonPath("$[0].icon").exists())
                .andExpect(jsonPath("$[0].sql").exists());
    }

    @Test
    void testDatabaseTypesContainPostgreSQL() throws Exception {
        mockMvc.perform(get("/api/database-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == 'postgresql')].name").value("PostgreSQL"))
                .andExpect(jsonPath("$[?(@.id == 'postgresql')].icon").value("🐘"))
                .andExpect(jsonPath("$[?(@.id == 'postgresql')].defaultPort").value("5432"));
    }

    @Test
    void testDatabaseTypesContainMySQL() throws Exception {
        mockMvc.perform(get("/api/database-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == 'mysql')].name").value("MySQL"))
                .andExpect(jsonPath("$[?(@.id == 'mysql')].icon").value("🐬"));
    }

    @Test
    void testDatabaseTypesContainMongoDB() throws Exception {
        mockMvc.perform(get("/api/database-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == 'mongodb')].name").value("MongoDB"))
                .andExpect(jsonPath("$[?(@.id == 'mongodb')].icon").value("🍃"))
                .andExpect(jsonPath("$[?(@.id == 'mongodb')].noSql").value(true));
    }
}
