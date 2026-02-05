package com.datanexus.datanexus.dto.connection;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TestConnectionRequest {

    @NotBlank(message = "Type is required")
    private String type;

    @NotBlank(message = "Host is required")
    private String host;

    @NotBlank(message = "Port is required")
    private String port;

    @NotBlank(message = "Database is required")
    private String database;

    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;
}
