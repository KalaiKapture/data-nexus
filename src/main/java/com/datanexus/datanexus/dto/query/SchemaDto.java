package com.datanexus.datanexus.dto.query;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SchemaDto {

    private List<TableInfo> tables;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TableInfo {
        private String name;
        private List<ColumnInfo> columns;
        private long rowCount;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ColumnInfo {
        private String name;
        private String type;
        private boolean nullable;
        private boolean primaryKey;
        private String foreignKey;
    }
}
