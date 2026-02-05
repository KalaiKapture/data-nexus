package com.datanexus.datanexus.repository;

import com.datanexus.datanexus.entity.DatabaseConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DatabaseConnectionRepository extends JpaRepository<DatabaseConnection, String> {

    List<DatabaseConnection> findByUserIdOrderByLastUsedDesc(String userId);

    Optional<DatabaseConnection> findByIdAndUserId(String id, String userId);
}
