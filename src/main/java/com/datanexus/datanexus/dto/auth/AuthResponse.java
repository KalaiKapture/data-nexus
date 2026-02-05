package com.datanexus.datanexus.dto.auth;

import com.datanexus.datanexus.dto.UserDto;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {

    private UserDto user;
    private String accessToken;
    private String refreshToken;
    private boolean isNewUser;
}
