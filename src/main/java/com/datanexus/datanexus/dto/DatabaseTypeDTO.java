package com.datanexus.datanexus.dto;

import com.datanexus.datanexus.enums.DatabaseType;
import lombok.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * DTO for database type information to send to frontend
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DatabaseTypeDTO {
    private String id;
    private String name;
    private String icon;
    private String defaultPort;
    private boolean isSql;
    private boolean isNoSql;

    /**
     * Convert enum to DTO
     */
    public static DatabaseTypeDTO fromEnum(DatabaseType type) {
        return DatabaseTypeDTO.builder()
                .id(type.getId())
                .name(type.getDisplayName())
                .icon(type.getIcon())
                .defaultPort(type.getDefaultPort())
                .isSql(type.isSql())
                .isNoSql(type.isNoSql())
                .build();
    }

    /**
     * Get all database types as DTOs
     */
    public static List<DatabaseTypeDTO> getAllTypes() {
        return DatabaseType.getAllTypes().stream()
                .map(DatabaseTypeDTO::fromEnum)
                .collect(Collectors.toList());
    }
}
