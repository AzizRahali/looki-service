package com.loomea.search.controller;

import com.loomea.common.dto.ApiResponse;
import com.loomea.salon.dto.SalonDto;
import com.loomea.search.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Tag(name = "Search", description = "Search salons")
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    @Operation(summary = "Search salons by name, address or description")
    public ResponseEntity<ApiResponse<List<SalonDto>>> search(
            @RequestParam(required = false, defaultValue = "") String q) {
        return ResponseEntity.ok(ApiResponse.success(searchService.search(q)));
    }

    @GetMapping("/category/{category}")
    @Operation(summary = "Search salons by service category")
    public ResponseEntity<ApiResponse<List<SalonDto>>> byCategory(@PathVariable String category) {
        return ResponseEntity.ok(ApiResponse.success(searchService.byCategory(category)));
    }
}
