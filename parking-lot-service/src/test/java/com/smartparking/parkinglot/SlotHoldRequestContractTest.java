package com.smartparking.parkinglot;
import com.smartparking.parkinglot.application.dto.SlotHoldRequest;
import com.smartparking.common.enums.VehicleTypeEnum;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SlotHoldRequestContractTest {
 @Test void rpcBodyRequiresBookingAndVehicleWhileLotComesFromPath(){
  try(var factory=Validation.buildDefaultValidatorFactory()){
   var body=SlotHoldRequest.builder().bookingId(10L).vehicleType(VehicleTypeEnum.HEAVY_TRUCK).build();
   assertTrue(factory.getValidator().validate(body).isEmpty());
   body.setBookingId(null);
   assertFalse(factory.getValidator().validate(body).isEmpty());
  }
 }
}
