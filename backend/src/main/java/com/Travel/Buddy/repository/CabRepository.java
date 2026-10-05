package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.Cab;
import com.Travel.Buddy.entity.VehicleType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CabRepository extends JpaRepository<Cab, Long> {

    List<Cab> findByIsActiveTrue();

    List<Cab> findByState_StateIdAndIsActiveTrue(Integer stateId);

    List<Cab> findByVehicleTypeAndIsActiveTrue(VehicleType vehicleType);

    List<Cab> findByState_StateIdAndVehicleTypeAndIsActiveTrue(Integer stateId, VehicleType vehicleType);

    List<Cab> findByPartner_UserId(Long partnerId);
}
