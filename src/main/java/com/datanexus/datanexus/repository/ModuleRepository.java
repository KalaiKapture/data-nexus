package com.datanexus.datanexus.repository;

import com.datanexus.datanexus.entity.Module;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ModuleRepository extends JpaRepository<Module, String> {

    List<Module> findByUserIdOrderByCreatedAtDesc(String userId);

    Optional<Module> findByIdAndUserId(String id, String userId);

    Optional<Module> findByShareId(String shareId);
}
