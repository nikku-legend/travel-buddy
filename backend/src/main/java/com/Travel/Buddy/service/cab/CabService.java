package com.Travel.Buddy.service.cab;

import com.Travel.Buddy.dto.cab.*;
import com.Travel.Buddy.entity.*;
import com.Travel.Buddy.repository.CabRepository;
import com.Travel.Buddy.repository.CabRideRepository;
import com.Travel.Buddy.repository.ReviewSummaryRepository;
import com.Travel.Buddy.repository.StateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CabService {

    /**
     * The only status moves a partner may make. (FR-25)
     *
     * <p>This endpoint previously accepted any status at all, so a ride
     * could be walked backwards — COMPLETED to CONFIRMED, or a CANCELLED
     * trip re-opened and picked up again. A cancelled ride that becomes
     * COMPLETED is worse still: it makes a passenger who never travelled
     * look like they did.
     *
     * <p>Terminal states have no entry here at all. Once a ride is
     * finished or cancelled it is a historical record, and editing one
     * means the ledger and the audit trail stop agreeing.
     */
    private static final Map<RideStatus, Set<RideStatus>> ALLOWED_TRANSITIONS =
            Map.of(
                    RideStatus.CONFIRMED,
                    Set.of(RideStatus.DRIVER_ASSIGNED, RideStatus.CANCELLED),
                    RideStatus.DRIVER_ASSIGNED,
                    Set.of(RideStatus.IN_PROGRESS, RideStatus.CANCELLED),
                    RideStatus.IN_PROGRESS,
                    Set.of(RideStatus.COMPLETED)
            );

    private final CabRepository cabRepository;
    private final CabRideRepository cabRideRepository;
    private final StateRepository stateRepository;
    private final ReviewSummaryRepository reviewSummaryRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public CabService(
            CabRepository cabRepository,
            CabRideRepository cabRideRepository,
            StateRepository stateRepository,
            ReviewSummaryRepository reviewSummaryRepository
    ) {
        this.cabRepository = cabRepository;
        this.cabRideRepository = cabRideRepository;
        this.stateRepository = stateRepository;
        this.reviewSummaryRepository = reviewSummaryRepository;
    }

    @Transactional(readOnly = true)
    public List<CabResponse> searchCabs(Integer stateId, VehicleType vehicleType) {
        List<Cab> cabs;

        if (stateId != null && vehicleType != null) {
            cabs = cabRepository.findByState_StateIdAndVehicleTypeAndIsActiveTrue(stateId, vehicleType);
        } else if (stateId != null) {
            cabs = cabRepository.findByState_StateIdAndIsActiveTrue(stateId);
        } else if (vehicleType != null) {
            cabs = cabRepository.findByVehicleTypeAndIsActiveTrue(vehicleType);
        } else {
            cabs = cabRepository.findByIsActiveTrue();
        }

        return cabs.stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CabResponse getCabById(Long cabId) {
        Cab cab = cabRepository.findById(cabId)
                .orElseThrow(() -> new IllegalArgumentException("Cab not found with ID: " + cabId));

        return mapToResponse(cab);
    }

    @Transactional
    public CabResponse registerCab(User partner, CabRegisterRequest request) {
        State state = stateRepository.findById(request.stateId())
                .orElseThrow(() -> new IllegalArgumentException("State not found with ID: " + request.stateId()));

        Cab cab = new Cab();
        cab.setPartner(partner);
        cab.setState(state);
        cab.setVehicleName(request.vehicleName().trim());
        cab.setVehicleType(request.vehicleType());
        cab.setRegistrationNumber(request.registrationNumber().trim().toUpperCase());
        cab.setSeatingCapacity(request.seatingCapacity());
        cab.setDriverName(request.driverName().trim());
        cab.setDriverPhone(request.driverPhone().trim());
        cab.setPricePerKm(request.pricePerKm());
        cab.setBaseFare(request.baseFare());

        Cab saved = cabRepository.save(cab);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<CabResponse> getPartnerCabs(User partner) {
        return cabRepository.findByPartner_UserId(partner.getUserId())
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public RideResponse bookRide(User user, BookRideRequest request) {
        Cab cab = cabRepository.findById(request.cabId())
                .orElseThrow(() -> new IllegalArgumentException("Cab not found with ID: " + request.cabId()));

        if (!Boolean.TRUE.equals(cab.getAvailable()) || !Boolean.TRUE.equals(cab.getActive())) {
            throw new IllegalStateException("Cab is currently not available for booking");
        }

        // Fare calculation: Base Fare + (Distance * PricePerKm)
        BigDecimal variableFare = request.distanceKm().multiply(cab.getPricePerKm());
        BigDecimal totalFare = cab.getBaseFare().add(variableFare).setScale(2, RoundingMode.HALF_UP);

        // Generate 4-digit OTP
        int otp = 1000 + secureRandom.nextInt(9000);

        CabRide ride = new CabRide();
        ride.setUser(user);
        ride.setCab(cab);
        ride.setPickupLocation(request.pickupLocation().trim());
        ride.setDropLocation(request.dropLocation().trim());
        ride.setPickupTime(request.pickupTime());
        ride.setDistanceKm(request.distanceKm());
        ride.setFareAmount(totalFare);
        ride.setStatus(RideStatus.CONFIRMED);
        ride.setOtpCode(String.valueOf(otp));

        CabRide saved = cabRideRepository.save(ride);
        return mapToRideResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<RideResponse> getUserRides(User user) {
        return cabRideRepository.findByUser_UserIdOrderByCreatedAtDesc(user.getUserId())
                .stream()
                .map(this::mapToRideResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RideResponse> getPartnerRides(User partner) {
        return cabRideRepository.findByCab_Partner_UserIdOrderByCreatedAtDesc(partner.getUserId())
                .stream()
                .map(this::mapToRideResponse)
                .toList();
    }

    @Transactional
    public RideResponse updateRideStatus(User partner, Long rideId, RideStatus newStatus) {
        CabRide ride = cabRideRepository.findById(rideId)
                .orElseThrow(() -> new IllegalArgumentException("Ride not found with ID: " + rideId));

        if (!ride.getCab().getPartner().getUserId().equals(partner.getUserId()) &&
                partner.getRole() != Role.ROLE_SUPER_ADMIN) {
            throw new SecurityException("Not authorized to update this ride");
        }

        RideStatus current = ride.getStatus();

        if (current == newStatus) {
            throw new IllegalArgumentException("Ride is already " + newStatus);
        }

        Set<RideStatus> permitted =
                ALLOWED_TRANSITIONS.getOrDefault(current, Set.of());

        if (!permitted.contains(newStatus)) {
            throw new IllegalArgumentException(
                    "A ride that is " + current + " cannot become " + newStatus
            );
        }

        ride.setStatus(newStatus);
        CabRide updated = cabRideRepository.save(ride);
        return mapToRideResponse(updated);
    }

    private CabResponse mapToResponse(Cab cab) {
        return new CabResponse(
                cab.getCabId(),
                cab.getVehicleName(),
                cab.getVehicleType(),
                cab.getRegistrationNumber(),
                cab.getSeatingCapacity(),
                cab.getDriverName(),
                cab.getDriverPhone(),
                cab.getState() != null ? cab.getState().getStateId() : null,
                cab.getState() != null ? cab.getState().getName() : null,
                cab.getPricePerKm(),
                cab.getBaseFare(),
                ratingOf(cab),
                cab.getAvailable(),
                cab.getVerified(),
                reviewCountOf(cab)
        );
    }

    /**
     * The published rating, or null when there are none.
     *
     * <p>Read from the review summary rather than the cab's own
     * {@code rating} column, which is never written by anything
     * and therefore always reports the 5.00 it was declared with.
     * A cab with forty one-star reviews was shown as perfect.
     *
     * <p>Null rather than a default, because a number on a card
     * reads as evidence.
     */
    private BigDecimal ratingOf(Cab cab) {
        ReviewSummary summary = summaryOf(cab.getCabId());

        return summary == null || summary.getReviewCount() == 0
                ? null
                : summary.getAverageRating();
    }

    private Integer reviewCountOf(Cab cab) {
        ReviewSummary summary = summaryOf(cab.getCabId());

        return summary == null || summary.getReviewCount() == null
                ? 0
                : summary.getReviewCount();
    }

    private ReviewSummary summaryOf(Long cabId) {
        return reviewSummaryRepository
                .findByTargetTypeAndTargetId(
                        ReviewTargetType.CAB, cabId)
                .orElse(null);
    }

    private RideResponse mapToRideResponse(CabRide ride) {
        return new RideResponse(
                ride.getRideId(),
                ride.getCab().getCabId(),
                ride.getCab().getVehicleName(),
                ride.getCab().getVehicleType(),
                ride.getCab().getRegistrationNumber(),
                ride.getCab().getDriverName(),
                ride.getCab().getDriverPhone(),
                ride.getPickupLocation(),
                ride.getDropLocation(),
                ride.getPickupTime(),
                ride.getDistanceKm(),
                ride.getFareAmount(),
                ride.getStatus(),
                ride.getOtpCode(),
                ride.getCreatedAt()
        );
    }
}
