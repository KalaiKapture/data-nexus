package com.datanexus.datanexus.dto.module;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModuleDto {

    private String id;
    private String shareId;
    private String title;
    private String query;
    private Object data;
    private ConnectionInfo connection;
    private int views;
    private Instant createdAt;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ConnectionInfo {
        private String id;
        private String name;
    }
}
