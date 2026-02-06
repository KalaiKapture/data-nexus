package com.datanexus.datanexus.repository;

import com.datanexus.datanexus.entity.RefreshToken;
import com.datanexus.datanexus.utils.PSQLUtil;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
public class RefreshTokenRepository {

    public RefreshToken findByTokenAndNotRevoked(String token) {
        return PSQLUtil.getSingleResult(
                "FROM RefreshToken rt WHERE rt.token = :token AND rt.revoked = false",
                Map.of("token", token),
                RefreshToken.class);
    }

    public void save(RefreshToken refreshToken) {
        PSQLUtil.saveOrUpdate(refreshToken);
    }

    public void revokeAllByUserId(Long userId) {
        PSQLUtil.runQueryForUpdate(
                "UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.user.id = :userId",
                Map.of("userId", userId));
    }
}
