package com.datanexus.datanexus.repository;

import com.datanexus.datanexus.entity.User;
import com.datanexus.datanexus.utils.PSQLUtil;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
public class UserRepository {

    public User findById(Long id) {
        return PSQLUtil.getSingleResult(
                "FROM User u WHERE u.id = :id",
                Map.of("id", id),
                User.class);
    }

    public User findByUsername(String username) {
        return PSQLUtil.getSingleResult(
                "FROM User u WHERE u.username = :username",
                Map.of("username", username),
                User.class);
    }

    public User findByEmail(String email) {
        return PSQLUtil.getSingleResult(
                "FROM User u WHERE u.email = :email",
                Map.of("email", email),
                User.class);
    }

    public User save(User user) {
        return PSQLUtil.saveOrUpdateWithReturn(user);
    }
}
