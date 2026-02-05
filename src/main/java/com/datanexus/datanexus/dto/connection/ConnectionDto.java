package com.datanexus.datanexus.dto.connection;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConnectionDto {

    private String id;
    private String name;
    private String type;
    private String typeName;
    private String typeIcon;
    private String host;
    private String port;
    private String database;
    private String username;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastUsed;
}
