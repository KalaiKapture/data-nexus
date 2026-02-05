package com.datanexus.datanexus.dto.connection;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestConnectionResponse {

    private boolean connected;
    private String message;
    private String serverVersion;
    private long latency;
}
