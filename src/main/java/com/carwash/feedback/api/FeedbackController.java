package com.carwash.feedback.api;

import com.carwash.access.application.TenantAccessContextProvider;
import com.carwash.feedback.application.*;
import com.carwash.feedback.domain.*;
import com.carwash.shared.api.PageResponse;
import com.carwash.shared.api.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.MultiValueMap;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/feedback")
@Tag(name = "Verified Feedback", description = "Completed-booking reviews, published ratings and moderation.")
@ApiResponses({
    @ApiResponse(responseCode = "400", description = "Validation or eligibility failure", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(responseCode = "403", description = "Access denied", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(responseCode = "404", description = "Resource missing or foreign", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(responseCode = "409", description = "Duplicate review/report or unchanged moderation state", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(responseCode = "405", description = "Method not allowed", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(responseCode = "415", description = "Unsupported media type", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class FeedbackController {
    private final FeedbackService service;
    private final TenantAccessContextProvider access;
    public FeedbackController(FeedbackService service, TenantAccessContextProvider access) {
        this.service = service; this.access = access;
    }

    public record SubmitReviewRequest(@NotBlank @Size(max = 64) String bookingId,
            @NotNull @Min(1) @Max(5) Integer rating, @Size(max = 1000) String comment) {
        @com.fasterxml.jackson.annotation.JsonAnySetter public void rejectUnknown(String name, Object value) {
            throw new IllegalArgumentException("Unknown feedback field");
        }
    }
    public record FeedbackReasonRequest(@NotBlank @Size(max = 500) String reason) {
        @com.fasterxml.jackson.annotation.JsonAnySetter public void rejectUnknown(String name, Object value) {
            throw new IllegalArgumentException("Unknown feedback field");
        }
    }
    public record ReviewView(String id, String bookingId, String branchId, String offeringId,
            int rating, String comment, Instant createdAt, PublicationState publication) {
        static ReviewView of(Review r) { return new ReviewView(r.id(),r.bookingId(),r.branchId(),
                r.offeringId(),r.rating(),r.comment(),r.createdAt(),r.publication()); }
    }
    public record ReportReceipt(String id, String reviewId, ReportState state, Instant createdAt) {
        static ReportReceipt of(ReviewReport r) { return new ReportReceipt(r.id(),r.reviewId(),r.state(),r.createdAt()); }
    }
    public record ModerationView(ReviewView review, String moderationReason, String moderatorId, Instant moderatedAt) {
        static ModerationView of(Review r) { return new ModerationView(ReviewView.of(r), r.moderationReason(),r.moderatorId(),r.moderatedAt()); }
    }
    public record ReportView(String id, String reviewId, String businessId, String actorId, String reason,
            Instant createdAt, ReportState state, String resolutionReason, String resolvedBy, Instant resolvedAt) {
        static ReportView of(ReviewReport r) { return new ReportView(r.id(),r.reviewId(),r.businessId(),
                r.actorId(),r.reason(),r.createdAt(),r.state(),r.resolutionReason(),r.resolvedBy(),r.resolvedAt()); }
    }

    @PostMapping("/reviews")
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit one verified review for an owned completed booking")
    @ApiResponse(responseCode = "201", description = "Review published")
    public ReviewView submit(@Valid @RequestBody SubmitReviewRequest request) {
        return ReviewView.of(service.submit(access.current(),request.bookingId(),request.rating(),request.comment()));
    }

    @GetMapping("/bookings/{bookingId}/review")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Get own booking review")
    @ApiResponse(responseCode = "200", description = "Review returned")
    public ReviewView own(@PathVariable @NotBlank @Size(max = 64) String bookingId) {
        return ReviewView.of(service.ownReview(access.current(),bookingId));
    }

    @GetMapping("/businesses/{businessId}/reviews")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER','PLATFORM_ADMIN')")
    @Operation(summary = "Page through private tenant reviews", description = "Owner scope is canonical; administrators provide an explicit business. Filters: branchId, offeringId, rating, publication. Sort: id, createdAt, rating. Default page 0, size 20, maximum 100.")
    @ApiResponse(responseCode = "200", description = "Authorized review page", useReturnTypeSchema = true)
    public PageResponse<ReviewView> list(@PathVariable @NotBlank @Size(max = 64) String businessId,
            @RequestParam MultiValueMap<String,String> parameters) {
        return PageResponse.from(service.tenantReviews(access.current(),businessId,
                FeedbackListPolicy.REVIEWS.parse(parameters)).map(ReviewView::of));
    }

    @GetMapping("/branches/{branchId}/summary")
    @PreAuthorize("hasAuthority('PERM_MARKETPLACE_READ')")
    @Operation(summary = "Published rating summary for a discoverable branch", description = "Distribution is ordered ratings 1 to 5; average is rounded HALF_UP to two decimals, null when count is zero.")
    @ApiResponse(responseCode = "200", description = "Rating summary")
    public RatingSummary branch(@PathVariable @NotBlank @Size(max = 64) String branchId) {
        return service.branchSummary(branchId);
    }

    @GetMapping("/offerings/{offeringId}/summary")
    @PreAuthorize("hasAuthority('PERM_MARKETPLACE_READ')")
    @Operation(summary = "Published rating summary for a discoverable offering")
    @ApiResponse(responseCode = "200", description = "Rating summary")
    public RatingSummary offering(@PathVariable @NotBlank @Size(max = 64) String offeringId) {
        return service.offeringSummary(offeringId);
    }

    @PostMapping("/reviews/{reviewId}/reports")
    @PreAuthorize("hasAnyRole('CUSTOMER','BUSINESS_OWNER')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Report own review or own tenant review once", description = "Reporting flags a review without changing publication or aggregates.")
    @ApiResponse(responseCode = "201", description = "Report opened")
    public ReportReceipt report(@PathVariable @NotBlank @Size(max = 64) String reviewId,
            @Valid @RequestBody FeedbackReasonRequest request) {
        return ReportReceipt.of(service.report(access.current(),reviewId,request.reason()));
    }

    @GetMapping("/businesses/{businessId}/reports")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Page through administrator moderation reports", description = "Filter: state OPEN or RESOLVED. Sort: id, createdAt. Default page 0, size 20, maximum 100.")
    @ApiResponse(responseCode = "200", description = "Report page", useReturnTypeSchema = true)
    public PageResponse<ReportView> reports(@PathVariable @NotBlank @Size(max = 64) String businessId,
            @RequestParam MultiValueMap<String,String> parameters) {
        return PageResponse.from(service.moderationQueue(access.current(),businessId,
                FeedbackListPolicy.REPORTS.parse(parameters)).map(ReportView::of));
    }

    @PostMapping("/reviews/{reviewId}/hide")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Hide a review with a required reason")
    @ApiResponse(responseCode = "200", description = "Review hidden")
    public ModerationView hide(@PathVariable @NotBlank @Size(max = 64) String reviewId,
            @Valid @RequestBody FeedbackReasonRequest request) {
        return ModerationView.of(service.moderate(access.current(),reviewId,PublicationState.HIDDEN,request.reason()));
    }

    @PostMapping("/reviews/{reviewId}/restore")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Restore a review with a required reason")
    @ApiResponse(responseCode = "200", description = "Review restored")
    public ModerationView restore(@PathVariable @NotBlank @Size(max = 64) String reviewId,
            @Valid @RequestBody FeedbackReasonRequest request) {
        return ModerationView.of(service.moderate(access.current(),reviewId,PublicationState.PUBLISHED,request.reason()));
    }

    @PostMapping("/reports/{reportId}/resolve")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Resolve a moderation report with a required reason")
    @ApiResponse(responseCode = "200", description = "Report resolved")
    public ReportView resolve(@PathVariable @NotBlank @Size(max = 64) String reportId,
            @Valid @RequestBody FeedbackReasonRequest request) {
        return ReportView.of(service.resolve(access.current(),reportId,request.reason()));
    }
}
