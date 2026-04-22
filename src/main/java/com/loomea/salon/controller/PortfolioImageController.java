package com.loomea.salon.controller;

import com.loomea.salon.entity.PortfolioPost;
import com.loomea.salon.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
public class PortfolioImageController {

    private final PortfolioRepository portfolioRepository;

    @GetMapping("/{postId}/image")
    public ResponseEntity<byte[]> getImage(@PathVariable UUID postId) {
        PortfolioPost post = portfolioRepository.findById(postId).orElse(null);
        if (post == null || post.getImageData() == null || post.getImageData().length == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000")
                .body(post.getImageData());
    }
}
