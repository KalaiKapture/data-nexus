package com.datanexus.datanexus.controller;

import com.datanexus.datanexus.dto.ApiResponse;
import com.datanexus.datanexus.dto.module.CreateModuleRequest;
import com.datanexus.datanexus.dto.module.ModuleDto;
import com.datanexus.datanexus.entity.User;
import com.datanexus.datanexus.service.ModuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/modules")
@RequiredArgsConstructor
public class ModuleController {

    private final ModuleService moduleService;

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getModules(@AuthenticationPrincipal User user) {
        List<ModuleDto> modules = moduleService.getUserModules(user);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "modules", modules,
                "total", modules.size()
        )));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, ModuleDto>>> createModule(
            @Valid @RequestBody CreateModuleRequest request,
            @AuthenticationPrincipal User user) {
        ModuleDto module = moduleService.createModule(request, user);
        return new ResponseEntity<>(ApiResponse.success(Map.of("module", module)), HttpStatus.CREATED);
    }

    @GetMapping("/shared/{shareId}")
    public ResponseEntity<ApiResponse<Map<String, ModuleDto>>> getModuleByShareId(
            @PathVariable String shareId) {
        ModuleDto module = moduleService.getModuleByShareId(shareId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("module", module)));
    }

    @PostMapping("/shared/{shareId}/view")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> incrementViews(
            @PathVariable String shareId) {
        int views = moduleService.incrementViews(shareId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("views", views)));
    }

    @DeleteMapping("/{moduleId}")
    public ResponseEntity<ApiResponse<Void>> deleteModule(
            @PathVariable String moduleId,
            @AuthenticationPrincipal User user) {
        moduleService.deleteModule(moduleId, user);
        return ResponseEntity.ok(ApiResponse.success("Module deleted successfully"));
    }
}
