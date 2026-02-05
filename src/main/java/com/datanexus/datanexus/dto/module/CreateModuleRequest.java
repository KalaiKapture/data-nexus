package com.datanexus.datanexus.dto.module;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateModuleRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String query;
    private Object data;

    @NotBlank(message = "Connection ID is required")
    private String connectionId;
}
