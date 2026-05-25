package com.loomea.salon.controller;

import com.loomea.auth.entity.User;
import com.loomea.auth.repository.UserRepository;
import com.loomea.common.dto.ApiResponse;
import com.loomea.common.exception.BadRequestException;
import com.loomea.common.exception.ResourceNotFoundException;
import com.loomea.salon.dto.CreateReviewRequest;
import com.loomea.salon.dto.ReviewDto;
import com.loomea.salon.entity.Review;
import com.loomea.salon.entity.Salon;
import com.loomea.salon.repository.ReviewRepository;
import com.loomea.salon.repository.SalonRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
@Tag(name = "Reviews", description = "Salon reviews and ratings")
public class ReviewController {

    private final ReviewRepository reviewRepository;
    private final SalonRepository salonRepository;
    private final UserRepository userRepository;

    @GetMapping("/salon/{salonId}")
    @Operation(summary = "Get all reviews for a salon")
    public ResponseEntity<ApiResponse<List<ReviewDto>>> getBySalon(@PathVariable UUID salonId) {
        List<ReviewDto> reviews = reviewRepository.findBySalonIdOrderByCreatedAtDesc(salonId)
                .stream().map(this::toDto).toList();
        return ResponseEntity.ok(ApiResponse.success(reviews));
    }

    @PostMapping
    @Operation(summary = "Submit a review for a salon")
    @SecurityRequirement(name = "Bearer JWT")
    public ResponseEntity<ApiResponse<ReviewDto>> create(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateReviewRequest request) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        UUID salonId = UUID.fromString(request.getSalonId());

        // Check if user already reviewed this salon
        if (reviewRepository.findBySalonIdAndUserId(salonId, userId).isPresent()) {
            throw new BadRequestException("You have already reviewed this salon");
        }

        Salon salon = salonRepository.findById(salonId)
                .orElseThrow(() -> new ResourceNotFoundException("Salon", "id", salonId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        Review review = Review.builder()
                .salon(salon)
                .user(user)
                .rating(request.getRating())
                .comment(request.getComment())
                .build();
        review = reviewRepository.save(review);

        // Update salon aggregate rating
        double avg = reviewRepository.averageRatingBySalon(salonId);
        long count = reviewRepository.countBySalonId(salonId);
        salon.setRating(Math.round(avg * 10.0) / 10.0);
        salon.setReviewCount((int) count);
        salonRepository.save(salon);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Review submitted", toDto(review)));
    }

    @DeleteMapping("/{reviewId}")
    @Operation(summary = "Delete your review")
    @SecurityRequirement(name = "Bearer JWT")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID reviewId,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        Review review = reviewRepository.findByIdWithRelations(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review", "id", reviewId));
        if (!review.getUser().getId().equals(userId)) {
            throw new BadRequestException("You can only delete your own reviews");
        }
        UUID salonId = review.getSalon().getId();
        reviewRepository.delete(review);

        // Re-aggregate
        Salon salon = review.getSalon();
        long count = reviewRepository.countBySalonId(salonId);
        if (count == 0) {
            salon.setRating(0);
            salon.setReviewCount(0);
        } else {
            double avg = reviewRepository.averageRatingBySalon(salonId);
            salon.setRating(Math.round(avg * 10.0) / 10.0);
            salon.setReviewCount((int) count);
        }
        salonRepository.save(salon);

        return ResponseEntity.ok(ApiResponse.success("Review deleted", null));
    }

    private ReviewDto toDto(Review r) {
        return ReviewDto.builder()
                .id(r.getId().toString())
                .salonId(r.getSalon().getId().toString())
                .userId(r.getUser().getId().toString())
                .userName(r.getUser().getFullName())
                .userAvatar(r.getUser().getAvatarData() != null && r.getUser().getAvatarData().length > 0
                        ? "/api/auth/users/" + r.getUser().getId() + "/avatar"
                        : null)
                .rating(r.getRating())
                .comment(r.getComment())
                .createdAt(r.getCreatedAt() != null ? r.getCreatedAt().toString() : "")
                .build();
    }
}
