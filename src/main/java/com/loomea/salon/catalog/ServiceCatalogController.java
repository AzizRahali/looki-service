package com.loomea.salon.catalog;

import com.loomea.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/services/catalog")
@Tag(name = "Service Catalog", description = "Predefined two-level service catalog")
public class ServiceCatalogController {

    @GetMapping
    @Operation(summary = "Get the full two-level service catalog (public)")
    public ResponseEntity<ApiResponse<List<ServiceCatalogData.Level1>>> getCatalog() {
        return ResponseEntity.ok(ApiResponse.success(ServiceCatalogData.CATALOG));
    }
}
