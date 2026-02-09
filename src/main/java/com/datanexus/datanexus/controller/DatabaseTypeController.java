package com.datanexus.datanexus.controller;

import com.datanexus.datanexus.dto.DatabaseTypeDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for database types
 */
@RestController
@RequestMapping("/api/database-types")
@RequiredArgsConstructor
@Slf4j
public class DatabaseTypeController {

    /**
     * Get all supported database types
     * Frontend will use this to populate the database selection dropdown
     */
    @GetMapping
    public ResponseEntity<List<DatabaseTypeDTO>> getAllDatabaseTypes() {
        log.info("Fetching all supported database types");
        return ResponseEntity.ok(DatabaseTypeDTO.getAllTypes());
    }
}
