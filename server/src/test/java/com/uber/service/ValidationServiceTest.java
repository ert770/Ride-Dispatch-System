package com.uber.service;

import com.uber.exception.BusinessException;
import com.uber.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ValidationService 測試 - 提升分支覆蓋率
 */
@DisplayName("ValidationService 測試")
class ValidationServiceTest {

    private ValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new ValidationService();
    }

    @Nested
    @DisplayName("驗證訂單建立請求")
    class ValidateCreateOrderRequestTests {

        @Test
        @DisplayName("有效請求應通過")
        void testValid() {
            assertDoesNotThrow(() ->
                validationService.validateCreateOrderRequest("p1",
                    new Location(25.0, 45.5), new Location(25.1, 45.6), VehicleType.STANDARD)
            );
        }

        @Test
        void testNullPassengerId() {
            BusinessException ex = assertThrows(BusinessException.class, () ->
                validationService.validateCreateOrderRequest(null,
                    new Location(25, 45), new Location(26, 46), VehicleType.STANDARD)
            );
            assertEquals("INVALID_REQUEST", ex.getCode());
        }

        @Test
        void testEmptyPassengerId() {
            assertThrows(BusinessException.class, () ->
                validationService.validateCreateOrderRequest("  ",
                    new Location(25, 45), new Location(26, 46), VehicleType.STANDARD)
            );
        }

        @Test
        void testNullPickup() {
            assertThrows(BusinessException.class, () ->
                validationService.validateCreateOrderRequest("p1", null,
                    new Location(26, 46), VehicleType.STANDARD)
            );
        }

        @Test
        void testNullDropoff() {
            assertThrows(BusinessException.class, () ->
                validationService.validateCreateOrderRequest("p1",
                    new Location(25, 45), null, VehicleType.STANDARD)
            );
        }

        @Test
        void testInvalidPickupCoordinate() {
            assertThrows(BusinessException.class, () ->
                validationService.validateCreateOrderRequest("p1",
                    new Location(200, 45), new Location(26, 46), VehicleType.STANDARD)
            );
        }

        @Test
        void testInvalidDropoffCoordinate() {
            assertThrows(BusinessException.class, () ->
                validationService.validateCreateOrderRequest("p1",
                    new Location(25, 45), new Location(26, 100), VehicleType.STANDARD)
            );
        }

        @Test
        void testSamePickupDropoff() {
            Location loc = new Location(25, 45);
            assertThrows(BusinessException.class, () ->
                validationService.validateCreateOrderRequest("p1", loc, loc, VehicleType.STANDARD)
            );
        }

        @Test
        void testDistanceTooShort() {
            assertThrows(BusinessException.class, () ->
                validationService.validateCreateOrderRequest("p1",
                    new Location(25.0, 45.5), new Location(25.00001, 45.50001), VehicleType.STANDARD)
            );
        }

        @Test
        void testNullVehicleType() {
            assertThrows(BusinessException.class, () ->
                validationService.validateCreateOrderRequest("p1",
                    new Location(25, 45), new Location(26, 46), null)
            );
        }
    }

    @Nested
    @DisplayName("驗證司機註冊")
    class ValidateDriverRegistrationTests {

        @Test
        void testValid() {
            assertDoesNotThrow(() ->
                validationService.validateDriverRegistration("d1", "John",
                    "0912345678", "ABC-1234", VehicleType.STANDARD)
            );
        }

        @Test
        void testNullDriverId() {
            assertThrows(BusinessException.class, () ->
                validationService.validateDriverRegistration(null, "John",
                    "0912345678", "ABC-1234", VehicleType.STANDARD)
            );
        }

        @Test
        void testEmptyDriverId() {
            assertThrows(BusinessException.class, () ->
                validationService.validateDriverRegistration("  ", "John",
                    "0912345678", "ABC-1234", VehicleType.STANDARD)
            );
        }

        @Test
        void testDriverIdTooLong() {
            assertThrows(BusinessException.class, () ->
                validationService.validateDriverRegistration("a".repeat(51), "John",
                    "0912345678", "ABC-1234", VehicleType.STANDARD)
            );
        }

        @Test
        void testNullName() {
            assertThrows(BusinessException.class, () ->
                validationService.validateDriverRegistration("d1", null,
                    "0912345678", "ABC-1234", VehicleType.STANDARD)
            );
        }

        @Test
        void testEmptyName() {
            assertThrows(BusinessException.class, () ->
                validationService.validateDriverRegistration("d1", "  ",
                    "0912345678", "ABC-1234", VehicleType.STANDARD)
            );
        }

        @Test
        void testNameTooShort() {
            assertThrows(BusinessException.class, () ->
                validationService.validateDriverRegistration("d1", "A",
                    "0912345678", "ABC-1234", VehicleType.STANDARD)
            );
        }

        @Test
        void testNameTooLong() {
            assertThrows(BusinessException.class, () ->
                validationService.validateDriverRegistration("d1", "A".repeat(51),
                    "0912345678", "ABC-1234", VehicleType.STANDARD)
            );
        }

        @Test
        void testNullPhone() {
            assertThrows(BusinessException.class, () ->
                validationService.validateDriverRegistration("d1", "John",
                    null, "ABC-1234", VehicleType.STANDARD)
            );
        }

        @Test
        void testEmptyPhone() {
            assertThrows(BusinessException.class, () ->
                validationService.validateDriverRegistration("d1", "John",
                    "  ", "ABC-1234", VehicleType.STANDARD)
            );
        }

        @Test
        void testInvalidPhone() {
            assertThrows(BusinessException.class, () ->
                validationService.validateDriverRegistration("d1", "John",
                    "invalid", "ABC-1234", VehicleType.STANDARD)
            );
        }

        @ParameterizedTest
        @ValueSource(strings = {"0912345678", "+886912345678", "02-12345678", "0912-345-678"})
        void testValidPhones(String phone) {
            assertDoesNotThrow(() ->
                validationService.validateDriverRegistration("d1", "John",
                    phone, "ABC-1234", VehicleType.STANDARD)
            );
        }

        @Test
        void testNullPlate() {
            assertThrows(BusinessException.class, () ->
                validationService.validateDriverRegistration("d1", "John",
                    "0912345678", null, VehicleType.STANDARD)
            );
        }

        @Test
        void testEmptyPlate() {
            assertThrows(BusinessException.class, () ->
                validationService.validateDriverRegistration("d1", "John",
                    "0912345678", "  ", VehicleType.STANDARD)
            );
        }

        @Test
        void testInvalidPlate() {
            assertThrows(BusinessException.class, () ->
                validationService.validateDriverRegistration("d1", "John",
                    "0912345678", "1234", VehicleType.STANDARD)
            );
        }

        @Test
        void testNullVehicleType() {
            assertThrows(BusinessException.class, () ->
                validationService.validateDriverRegistration("d1", "John",
                    "0912345678", "ABC-1234", null)
            );
        }
    }

    @Nested
    @DisplayName("驗證位置更新")
    class ValidateLocationUpdateTests {

        @Test
        void testValid() {
            assertDoesNotThrow(() ->
                validationService.validateLocationUpdate(new Location(25, 45))
            );
        }

        @Test
        void testNullLocation() {
            assertThrows(BusinessException.class, () ->
                validationService.validateLocationUpdate(null)
            );
        }

        @Test
        void testInvalidCoordinate() {
            assertThrows(BusinessException.class, () ->
                validationService.validateLocationUpdate(new Location(200, 100))
            );
        }
    }

    @Nested
    @DisplayName("驗證訂單狀態轉換")
    class ValidateOrderStateTransitionTests {

        @Test
        void testPendingToAccepted() {
            assertDoesNotThrow(() ->
                validationService.validateOrderStateTransition(OrderStatus.PENDING, OrderStatus.ACCEPTED)
            );
        }

        @Test
        void testPendingToCancelled() {
            assertDoesNotThrow(() ->
                validationService.validateOrderStateTransition(OrderStatus.PENDING, OrderStatus.CANCELLED)
            );
        }

        @Test
        void testAcceptedToOngoing() {
            assertDoesNotThrow(() ->
                validationService.validateOrderStateTransition(OrderStatus.ACCEPTED, OrderStatus.ONGOING)
            );
        }

        @Test
        void testAcceptedToCancelled() {
            assertDoesNotThrow(() ->
                validationService.validateOrderStateTransition(OrderStatus.ACCEPTED, OrderStatus.CANCELLED)
            );
        }

        @Test
        void testOngoingToCompleted() {
            assertDoesNotThrow(() ->
                validationService.validateOrderStateTransition(OrderStatus.ONGOING, OrderStatus.COMPLETED)
            );
        }

        @Test
        void testNullFrom() {
            assertThrows(BusinessException.class, () ->
                validationService.validateOrderStateTransition(null, OrderStatus.ACCEPTED)
            );
        }

        @Test
        void testNullTo() {
            assertThrows(BusinessException.class, () ->
                validationService.validateOrderStateTransition(OrderStatus.PENDING, null)
            );
        }

        @Test
        void testCompletedIsTerminal() {
            BusinessException ex = assertThrows(BusinessException.class, () ->
                validationService.validateOrderStateTransition(OrderStatus.COMPLETED, OrderStatus.PENDING)
            );
            assertEquals("INVALID_STATE", ex.getCode());
        }

        @Test
        void testCancelledIsTerminal() {
            assertThrows(BusinessException.class, () ->
                validationService.validateOrderStateTransition(OrderStatus.CANCELLED, OrderStatus.PENDING)
            );
        }

        @Test
        void testPendingToOngoingNotAllowed() {
            assertThrows(BusinessException.class, () ->
                validationService.validateOrderStateTransition(OrderStatus.PENDING, OrderStatus.ONGOING)
            );
        }

        @Test
        void testAcceptedToPendingNotAllowed() {
            assertThrows(BusinessException.class, () ->
                validationService.validateOrderStateTransition(OrderStatus.ACCEPTED, OrderStatus.PENDING)
            );
        }

        @Test
        void testOngoingToPendingNotAllowed() {
            assertThrows(BusinessException.class, () ->
                validationService.validateOrderStateTransition(OrderStatus.ONGOING, OrderStatus.PENDING)
            );
        }

        @Test
        void testOngoingToCancelledNotAllowed() {
            assertThrows(BusinessException.class, () ->
                validationService.validateOrderStateTransition(OrderStatus.ONGOING, OrderStatus.CANCELLED)
            );
        }
    }

    @Nested
    @DisplayName("驗證司機狀態轉換")
    class ValidateDriverStateTransitionTests {

        @Test
        void testValid() {
            assertDoesNotThrow(() ->
                validationService.validateDriverStateTransition(DriverStatus.OFFLINE, DriverStatus.ONLINE)
            );
        }

        @Test
        void testNullFrom() {
            assertThrows(BusinessException.class, () ->
                validationService.validateDriverStateTransition(null, DriverStatus.ONLINE)
            );
        }

        @Test
        void testNullTo() {
            assertThrows(BusinessException.class, () ->
                validationService.validateDriverStateTransition(DriverStatus.OFFLINE, null)
            );
        }
    }

    @Nested
    @DisplayName("驗證訂單可接單")
    class ValidateOrderAcceptableTests {

        @Test
        void testPending() {
            Order order = new Order();
            order.setStatus(OrderStatus.PENDING);
            order.setCreatedAt(Instant.now());
            assertDoesNotThrow(() -> validationService.validateOrderAcceptable(order));
        }

        @Test
        void testNull() {
            BusinessException ex = assertThrows(BusinessException.class, () ->
                validationService.validateOrderAcceptable(null)
            );
            assertEquals("ORDER_NOT_FOUND", ex.getCode());
        }

        @Test
        void testAccepted() {
            Order order = new Order();
            order.setStatus(OrderStatus.ACCEPTED);
            BusinessException ex = assertThrows(BusinessException.class, () ->
                validationService.validateOrderAcceptable(order)
            );
            assertEquals("ORDER_ALREADY_ACCEPTED", ex.getCode());
            assertEquals(409, ex.getHttpStatus());
        }

        @Test
        void testOngoing() {
            Order order = new Order();
            order.setStatus(OrderStatus.ONGOING);
            assertThrows(BusinessException.class, () ->
                validationService.validateOrderAcceptable(order)
            );
        }

        @Test
        void testCompleted() {
            Order order = new Order();
            order.setStatus(OrderStatus.COMPLETED);
            assertThrows(BusinessException.class, () ->
                validationService.validateOrderAcceptable(order)
            );
        }

        @Test
        void testExpired() {
            Order order = new Order();
            order.setStatus(OrderStatus.PENDING);
            order.setCreatedAt(Instant.now().minus(31, ChronoUnit.MINUTES));
            BusinessException ex = assertThrows(BusinessException.class, () ->
                validationService.validateOrderAcceptable(order)
            );
            assertEquals("ORDER_EXPIRED", ex.getCode());
        }

        @Test
        void testWithin30Minutes() {
            Order order = new Order();
            order.setStatus(OrderStatus.PENDING);
            order.setCreatedAt(Instant.now().minus(29, ChronoUnit.MINUTES));
            assertDoesNotThrow(() -> validationService.validateOrderAcceptable(order));
        }
    }

    @Nested
    @DisplayName("驗證司機可接單")
    class ValidateDriverCanAcceptTests {

        @Test
        void testValid() {
            Driver driver = new Driver();
            driver.setStatus(DriverStatus.ONLINE);
            driver.setBusy(false);
            driver.setLocation(new Location(25, 45));
            assertDoesNotThrow(() -> validationService.validateDriverCanAccept(driver));
        }

        @Test
        void testNull() {
            BusinessException ex = assertThrows(BusinessException.class, () ->
                validationService.validateDriverCanAccept(null)
            );
            assertEquals("DRIVER_NOT_FOUND", ex.getCode());
        }

        @Test
        void testOffline() {
            Driver driver = new Driver();
            driver.setStatus(DriverStatus.OFFLINE);
            driver.setBusy(false);
            driver.setLocation(new Location(25, 45));
            BusinessException ex = assertThrows(BusinessException.class, () ->
                validationService.validateDriverCanAccept(driver)
            );
            assertEquals("DRIVER_OFFLINE", ex.getCode());
        }

        @Test
        void testBusy() {
            Driver driver = new Driver();
            driver.setStatus(DriverStatus.ONLINE);
            driver.setBusy(true);
            driver.setLocation(new Location(25, 45));
            BusinessException ex = assertThrows(BusinessException.class, () ->
                validationService.validateDriverCanAccept(driver)
            );
            assertEquals("DRIVER_BUSY", ex.getCode());
        }

        @Test
        void testNoLocation() {
            Driver driver = new Driver();
            driver.setStatus(DriverStatus.ONLINE);
            driver.setBusy(false);
            driver.setLocation(null);
            assertThrows(BusinessException.class, () ->
                validationService.validateDriverCanAccept(driver)
            );
        }
    }

    @Nested
    @DisplayName("驗證司機訂單匹配")
    class ValidateDriverOrderMatchTests {

        @Test
        void testValid() {
            Driver driver = new Driver();
            driver.setVehicleType(VehicleType.STANDARD);
            driver.setLocation(new Location(25, 45));

            Order order = new Order();
            order.setVehicleType(VehicleType.STANDARD);
            order.setPickupLocation(new Location(25.1, 45.1));

            assertDoesNotThrow(() -> validationService.validateDriverOrderMatch(driver, order));
        }

        @Test
        void testNullDriver() {
            Order order = new Order();
            assertThrows(BusinessException.class, () ->
                validationService.validateDriverOrderMatch(null, order)
            );
        }

        @Test
        void testNullOrder() {
            Driver driver = new Driver();
            assertThrows(BusinessException.class, () ->
                validationService.validateDriverOrderMatch(driver, null)
            );
        }

        @Test
        void testVehicleTypeMismatch() {
            Driver driver = new Driver();
            driver.setVehicleType(VehicleType.STANDARD);
            driver.setLocation(new Location(25, 45));

            Order order = new Order();
            order.setVehicleType(VehicleType.PREMIUM);
            order.setPickupLocation(new Location(25.1, 45.1));

            BusinessException ex = assertThrows(BusinessException.class, () ->
                validationService.validateDriverOrderMatch(driver, order)
            );
            assertEquals("VEHICLE_TYPE_MISMATCH", ex.getCode());
        }

        @Test
        void testTooFar() {
            Driver driver = new Driver();
            driver.setVehicleType(VehicleType.STANDARD);
            driver.setLocation(new Location(0, 0));

            Order order = new Order();
            order.setVehicleType(VehicleType.STANDARD);
            order.setPickupLocation(new Location(40, 40));

            BusinessException ex = assertThrows(BusinessException.class, () ->
                validationService.validateDriverOrderMatch(driver, order)
            );
            assertEquals("TOO_FAR", ex.getCode());
        }
    }

    @Nested
    @DisplayName("驗證取消訂單")
    class ValidateCancelOrderTests {

        @Test
        void testPending() {
            Order order = new Order();
            order.setPassengerId("p1");
            order.setStatus(OrderStatus.PENDING);
            assertDoesNotThrow(() -> validationService.validateCancelOrder(order, "p1"));
        }

        @Test
        void testNull() {
            BusinessException ex = assertThrows(BusinessException.class, () ->
                validationService.validateCancelOrder(null, "p1")
            );
            assertEquals("ORDER_NOT_FOUND", ex.getCode());
        }

        @Test
        void testCompleted() {
            Order order = new Order();
            order.setPassengerId("p1");
            order.setStatus(OrderStatus.COMPLETED);
            BusinessException ex = assertThrows(BusinessException.class, () ->
                validationService.validateCancelOrder(order, "p1")
            );
            assertEquals("INVALID_STATE", ex.getCode());
        }

        @Test
        void testAlreadyCancelled() {
            Order order = new Order();
            order.setPassengerId("p1");
            order.setStatus(OrderStatus.CANCELLED);
            assertDoesNotThrow(() -> validationService.validateCancelOrder(order, "p1"));
        }

        @Test
        void testOngoing() {
            Order order = new Order();
            order.setPassengerId("p1");
            order.setStatus(OrderStatus.ONGOING);
            assertThrows(BusinessException.class, () ->
                validationService.validateCancelOrder(order, "p1")
            );
        }

        @Test
        void testUnauthorized() {
            Order order = new Order();
            order.setPassengerId("p1");
            order.setStatus(OrderStatus.PENDING);
            BusinessException ex = assertThrows(BusinessException.class, () ->
                validationService.validateCancelOrder(order, "p2")
            );
            assertEquals("FORBIDDEN", ex.getCode());
            assertEquals(403, ex.getHttpStatus());
        }
    }

    @Nested
    @DisplayName("驗證費率計畫")
    class ValidateRatePlanTests {

        private RatePlan createValid() {
            RatePlan plan = new RatePlan();
            plan.setVehicleType(VehicleType.STANDARD);
            plan.setBaseFare(100.0);
            plan.setPerKmRate(20.0);
            plan.setPerMinRate(5.0);
            plan.setMinFare(150.0);
            plan.setCancelFee(50.0);
            return plan;
        }

        @Test
        void testValid() {
            assertDoesNotThrow(() -> validationService.validateRatePlan(createValid()));
        }

        @Test
        void testNull() {
            assertThrows(BusinessException.class, () -> validationService.validateRatePlan(null));
        }

        @Test
        void testNullVehicleType() {
            RatePlan plan = createValid();
            plan.setVehicleType(null);
            assertThrows(BusinessException.class, () -> validationService.validateRatePlan(plan));
        }

        @Test
        void testNegativeBaseFare() {
            RatePlan plan = createValid();
            plan.setBaseFare(-10.0);
            assertThrows(BusinessException.class, () -> validationService.validateRatePlan(plan));
        }

        @Test
        void testBaseFareTooHigh() {
            RatePlan plan = createValid();
            plan.setBaseFare(501.0);
            assertThrows(BusinessException.class, () -> validationService.validateRatePlan(plan));
        }

        @Test
        void testNegativePerKmRate() {
            RatePlan plan = createValid();
            plan.setPerKmRate(-5.0);
            assertThrows(BusinessException.class, () -> validationService.validateRatePlan(plan));
        }

        @Test
        void testPerKmRateTooHigh() {
            RatePlan plan = createValid();
            plan.setPerKmRate(101.0);
            assertThrows(BusinessException.class, () -> validationService.validateRatePlan(plan));
        }

        @Test
        void testNegativePerMinRate() {
            RatePlan plan = createValid();
            plan.setPerMinRate(-2.0);
            assertThrows(BusinessException.class, () -> validationService.validateRatePlan(plan));
        }

        @Test
        void testPerMinRateTooHigh() {
            RatePlan plan = createValid();
            plan.setPerMinRate(51.0);
            assertThrows(BusinessException.class, () -> validationService.validateRatePlan(plan));
        }

        @Test
        void testNegativeMinFare() {
            RatePlan plan = createValid();
            plan.setMinFare(-10.0);
            assertThrows(BusinessException.class, () -> validationService.validateRatePlan(plan));
        }

        @Test
        void testNegativeCancelFee() {
            RatePlan plan = createValid();
            plan.setCancelFee(-5.0);
            assertThrows(BusinessException.class, () -> validationService.validateRatePlan(plan));
        }

        @Test
        void testCancelFeeTooHigh() {
            RatePlan plan = createValid();
            plan.setCancelFee(200.0);
            assertThrows(BusinessException.class, () -> validationService.validateRatePlan(plan));
        }
    }

    @Nested
    @DisplayName("訂單完整性")
    class IsOrderCompleteTests {

        @Test
        void testComplete() {
            Order order = new Order();
            order.setOrderId("o1");
            order.setPassengerId("p1");
            order.setStatus(OrderStatus.PENDING);
            order.setVehicleType(VehicleType.STANDARD);
            order.setPickupLocation(new Location(25, 45));
            order.setDropoffLocation(new Location(26, 46));
            assertTrue(validationService.isOrderComplete(order));
        }

        @Test
        void testNull() {
            assertFalse(validationService.isOrderComplete(null));
        }

        @Test
        void testMissingOrderId() {
            Order order = new Order();
            order.setPassengerId("p1");
            order.setStatus(OrderStatus.PENDING);
            order.setVehicleType(VehicleType.STANDARD);
            order.setPickupLocation(new Location(25, 45));
            order.setDropoffLocation(new Location(26, 46));
            assertFalse(validationService.isOrderComplete(order));
        }

        @Test
        void testEmptyOrderId() {
            Order order = new Order();
            order.setOrderId("  ");
            order.setPassengerId("p1");
            order.setStatus(OrderStatus.PENDING);
            order.setVehicleType(VehicleType.STANDARD);
            order.setPickupLocation(new Location(25, 45));
            order.setDropoffLocation(new Location(26, 46));
            assertFalse(validationService.isOrderComplete(order));
        }
    }

    @Nested
    @DisplayName("司機完整性")
    class IsDriverCompleteTests {

        @Test
        void testComplete() {
            Driver driver = new Driver();
            driver.setDriverId("d1");
            driver.setName("John");
            driver.setPhone("0912345678");
            driver.setVehiclePlate("ABC-1234");
            driver.setVehicleType(VehicleType.STANDARD);
            driver.setStatus(DriverStatus.ONLINE);
            assertTrue(validationService.isDriverComplete(driver));
        }

        @Test
        void testNull() {
            assertFalse(validationService.isDriverComplete(null));
        }

        @Test
        void testMissingDriverId() {
            Driver driver = new Driver();
            driver.setName("John");
            driver.setPhone("0912345678");
            driver.setVehiclePlate("ABC-1234");
            driver.setVehicleType(VehicleType.STANDARD);
            driver.setStatus(DriverStatus.ONLINE);
            assertFalse(validationService.isDriverComplete(driver));
        }

        @Test
        void testEmptyDriverId() {
            Driver driver = new Driver();
            driver.setDriverId("  ");
            driver.setName("John");
            driver.setPhone("0912345678");
            driver.setVehiclePlate("ABC-1234");
            driver.setVehicleType(VehicleType.STANDARD);
            driver.setStatus(DriverStatus.ONLINE);
            assertFalse(validationService.isDriverComplete(driver));
        }
    }
}

