package com.datanexus.datanexus.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "schema_cache")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SchemaCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "connection_id", nullable = false)
    private Long connectionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "data_source_type", nullable = false)
    private String dataSourceType;

    @Column(name = "schema_json", columnDefinition = "TEXT", nullable = false)
    private String schemaJson;

    @Column(name = "sample_data_json", columnDefinition = "TEXT")
    private String sampleDataJson;

    @Column(name = "cached_at", nullable = false)
    private Instant cachedAt;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
