package com.datanexus.datanexus.service;

import com.datanexus.datanexus.dto.module.CreateModuleRequest;
import com.datanexus.datanexus.dto.module.ModuleDto;
import com.datanexus.datanexus.entity.DatabaseConnection;
import com.datanexus.datanexus.entity.Module;
import com.datanexus.datanexus.entity.User;
import com.datanexus.datanexus.exception.ApiException;
import com.datanexus.datanexus.repository.DatabaseConnectionRepository;
import com.datanexus.datanexus.repository.ModuleRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ModuleService {

    private final ObjectMapper objectMapper;
    private final ModuleRepository moduleRepository;
    private final DatabaseConnectionRepository connectionRepository;

    public List<ModuleDto> getUserModules(User user) {
        List<Module> modules = moduleRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        return modules.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public ModuleDto createModule(CreateModuleRequest request, User user) {
        DatabaseConnection connection = connectionRepository.findByIdAndUserId(request.getConnectionId(), user.getId());
        if (connection == null) {
            throw ApiException.notFound("CONNECTION_NOT_FOUND", "Database connection not found");
        }

        String dataJson;
        try {
            dataJson = request.getData() != null ? objectMapper.writeValueAsString(request.getData()) : null;
        } catch (JsonProcessingException e) {
            throw ApiException.badRequest("BAD_REQUEST", "Invalid data format");
        }

        String shareId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        Module module = Module.builder()
                .shareId(shareId)
                .title(request.getTitle())
                .query(request.getQuery())
                .data(dataJson)
                .connection(connection)
                .user(user)
                .views(0)
                .build();

        module = moduleRepository.save(module);
        return toDto(module);
    }

    public ModuleDto getModuleByShareId(String shareId) {
        Module module = moduleRepository.findByShareId(shareId);
        if (module == null) {
            throw ApiException.notFound("MODULE_NOT_FOUND", "Module not found");
        }
        return toDto(module);
    }

    public int incrementViews(String shareId) {
        Module module = moduleRepository.findByShareId(shareId);
        if (module == null) {
            throw ApiException.notFound("MODULE_NOT_FOUND", "Module not found");
        }
        module.setViews(module.getViews() + 1);
        moduleRepository.save(module);
        return module.getViews();
    }

    public void deleteModule(String moduleId, User user) {
        Module module = moduleRepository.findByIdAndUserId(moduleId, user.getId());
        if (module == null) {
            throw ApiException.notFound("MODULE_NOT_FOUND", "Module not found");
        }
        moduleRepository.delete(module);
    }

    private ModuleDto toDto(Module module) {
        Object data = null;
        if (module.getData() != null) {
            try {
                data = objectMapper.readValue(module.getData(), Object.class);
            } catch (JsonProcessingException e) {
                data = module.getData();
            }
        }

        ModuleDto.ConnectionInfo connectionInfo = null;
        if (module.getConnection() != null) {
            connectionInfo = ModuleDto.ConnectionInfo.builder()
                    .id(module.getConnection().getId())
                    .name(module.getConnection().getName())
                    .build();
        }

        return ModuleDto.builder()
                .id(module.getId())
                .shareId(module.getShareId())
                .title(module.getTitle())
                .query(module.getQuery())
                .data(data)
                .connection(connectionInfo)
                .views(module.getViews())
                .createdAt(module.getCreatedAt())
                .build();
    }
}
