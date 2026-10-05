package com.Travel.Buddy.controller;

import com.Travel.Buddy.dto.bucketlist.AddBucketListRequest;
import com.Travel.Buddy.dto.bucketlist.BucketListCheckResponse;
import com.Travel.Buddy.dto.bucketlist.BucketListResponse;
import com.Travel.Buddy.service.bucketlist.BucketListService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bucket-list")
public class BucketListController {

    private final BucketListService bucketListService;

    public BucketListController(
            BucketListService bucketListService
    ) {
        this.bucketListService = bucketListService;
    }

    /**
     * ADD TOURIST PLACE TO BUCKET LIST
     *
     * POST /api/v1/bucket-list
     */
    @PostMapping
    public ResponseEntity<BucketListResponse> addToBucketList(
            Authentication authentication,
            @Valid @RequestBody AddBucketListRequest request
    ) {

        BucketListResponse response =
                bucketListService.addToBucketList(
                        authentication.getName(),
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    /**
     * GET CURRENT USER'S BUCKET LIST
     *
     * GET /api/v1/bucket-list
     */
    @GetMapping
    public ResponseEntity<List<BucketListResponse>> getMyBucketList(
            Authentication authentication
    ) {

        List<BucketListResponse> response =
                bucketListService.getMyBucketList(
                        authentication.getName()
                );

        return ResponseEntity.ok(response);
    }

    /**
     * CHECK WHETHER A PLACE IS SAVED
     *
     * GET /api/v1/bucket-list/check/{placeId}
     */
    @GetMapping("/check/{placeId}")
    public ResponseEntity<BucketListCheckResponse> checkBucketList(
            Authentication authentication,
            @PathVariable Long placeId
    ) {

        BucketListCheckResponse response =
                bucketListService.checkBucketList(
                        authentication.getName(),
                        placeId
                );

        return ResponseEntity.ok(response);
    }

    /**
     * REMOVE ITEM FROM BUCKET LIST
     *
     * DELETE /api/v1/bucket-list/{bucketId}
     */
    @DeleteMapping("/{bucketId}")
    public ResponseEntity<Void> removeFromBucketList(
            Authentication authentication,
            @PathVariable Long bucketId
    ) {

        bucketListService.removeFromBucketList(
                authentication.getName(),
                bucketId
        );

        return ResponseEntity.noContent().build();
    }
}