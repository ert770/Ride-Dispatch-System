package com.uber.service;

import com.uber.model.RatePlan;
import com.uber.model.VehicleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FareService 單元測試
 * 
 * 測試覆蓋:
 * - UT-F01: 公式計算正確
 * - UT-F02: 低於最低車資則收最低
 * - UT-F03: 取消費計算正確
 */
class FareServiceTest {

    private FareService fareService;

    @BeforeEach
    void setUp() {
        fareService = new FareService();
        fareService.initRatePlans();
    }

    @Nested
    @DisplayName("預估車資計算測試")
    class EstimatedFareTests {

        @Test
        @DisplayName("UT-F01: 正常計算預估車資 - STANDARD")
        void testCalculateEstimatedFare_Standard() {
            // 公式: Base (50) + 10km * 15/km = 50 + 150 = 200
            double fare = fareService.calculateEstimatedFare(VehicleType.STANDARD, 10.0);
            assertEquals(200.0, fare, 0.01);
        }

        @Test
        @DisplayName("UT-F01: 正常計算預估車資 - PREMIUM")
        void testCalculateEstimatedFare_Premium() {
            // 公式: Base (80) + 10km * 25/km = 80 + 250 = 330
            double fare = fareService.calculateEstimatedFare(VehicleType.PREMIUM, 10.0);
            assertEquals(330.0, fare, 0.01);
        }

        @Test
        @DisplayName("UT-F01: 正常計算預估車資 - XL")
        void testCalculateEstimatedFare_XL() {
            // 公式: Base (100) + 10km * 30/km = 100 + 300 = 400
            double fare = fareService.calculateEstimatedFare(VehicleType.XL, 10.0);
            assertEquals(400.0, fare, 0.01);
        }

        @Test
        @DisplayName("UT-F02: 低於最低車資則收最低 - STANDARD")
        void testCalculateEstimatedFare_MinFare_Standard() {
            // 距離 0.5km: Base (50) + 0.5 * 15 = 57.5 < Min (70)
            double fare = fareService.calculateEstimatedFare(VehicleType.STANDARD, 0.5);
            assertEquals(70.0, fare, 0.01);
        }

        @Test
        @DisplayName("UT-F02: 低於最低車資則收最低 - PREMIUM")
        void testCalculateEstimatedFare_MinFare_Premium() {
            // 距離 0.5km: Base (80) + 0.5 * 25 = 92.5 < Min (120)
            double fare = fareService.calculateEstimatedFare(VehicleType.PREMIUM, 0.5);
            assertEquals(120.0, fare, 0.01);
        }

        @Test
        @DisplayName("UT-F02: 低於最低車資則收最低 - XL")
        void testCalculateEstimatedFare_MinFare_XL() {
            // 距離 0.5km: Base (100) + 0.5 * 30 = 115 < Min (150)
            double fare = fareService.calculateEstimatedFare(VehicleType.XL, 0.5);
            assertEquals(150.0, fare, 0.01);
        }

        @Test
        @DisplayName("零距離取得最低車資")
        void testCalculateEstimatedFare_ZeroDistance() {
            // 距離 0km: Base (50) < Min (70)
            double fare = fareService.calculateEstimatedFare(VehicleType.STANDARD, 0);
            assertEquals(70.0, fare, 0.01);
        }
    }

    @Nested
    @DisplayName("實際車資計算測試")
    class ActualFareTests {

        @Test
        @DisplayName("UT-F01: 正常計算實際車資 - 含時間")
        void testCalculateFare_WithDuration() {
            // STANDARD: Base (50) + 5km * 15/km + 10min * 3/min = 50 + 75 + 30 = 155
            double fare = fareService.calculateFare(VehicleType.STANDARD, 5.0, 10);
            assertEquals(155.0, fare, 0.01);
        }

        @Test
        @DisplayName("UT-F01: PREMIUM 車資計算正確")
        void testCalculateFare_Premium() {
            // PREMIUM: Base (80) + 5km * 25/km + 10min * 5/min = 80 + 125 + 50 = 255
            double fare = fareService.calculateFare(VehicleType.PREMIUM, 5.0, 10);
            assertEquals(255.0, fare, 0.01);
        }

        @Test
        @DisplayName("UT-F01: XL 車資計算正確")
        void testCalculateFare_XL() {
            // XL: Base (100) + 5km * 30/km + 10min * 6/min = 100 + 150 + 60 = 310
            double fare = fareService.calculateFare(VehicleType.XL, 5.0, 10);
            assertEquals(310.0, fare, 0.01);
        }

        @Test
        @DisplayName("UT-F02: 短程短時間取最低車資")
        void testCalculateFare_MinFare() {
            // STANDARD: Base (50) + 0.5km * 15 + 1min * 3 = 50 + 7.5 + 3 = 60.5 < Min (70)
            double fare = fareService.calculateFare(VehicleType.STANDARD, 0.5, 1);
            assertEquals(70.0, fare, 0.01);
        }

        @Test
        @DisplayName("邊界測試: 剛好等於最低車資")
        void testCalculateFare_ExactlyMinFare() {
            // 計算剛好等於最低車資的距離和時間
            // STANDARD Min = 70, Base = 50
            // 需要: 20 = 15*d + 3*t
            // 設 d=1, t=(20-15)/3 ≈ 1.67min
            double fare = fareService.calculateFare(VehicleType.STANDARD, 1.0, 2);
            // Base (50) + 1km * 15 + 2min * 3 = 50 + 15 + 6 = 71
            assertEquals(71.0, fare, 0.01);
        }

        @Test
        @DisplayName("長途行程車資計算")
        void testCalculateFare_LongTrip() {
            // STANDARD: Base (50) + 50km * 15 + 60min * 3 = 50 + 750 + 180 = 980
            double fare = fareService.calculateFare(VehicleType.STANDARD, 50.0, 60);
            assertEquals(980.0, fare, 0.01);
        }
    }

    @Nested
    @DisplayName("取消費測試")
    class CancelFeeTests {

        @Test
        @DisplayName("UT-F03: STANDARD 取消費正確")
        void testCancelFee_Standard() {
            assertEquals(30.0, fareService.getCancelFee(VehicleType.STANDARD), 0.01);
        }

        @Test
        @DisplayName("UT-F03: PREMIUM 取消費正確")
        void testCancelFee_Premium() {
            assertEquals(50.0, fareService.getCancelFee(VehicleType.PREMIUM), 0.01);
        }

        @Test
        @DisplayName("UT-F03: XL 取消費正確")
        void testCancelFee_XL() {
            assertEquals(60.0, fareService.getCancelFee(VehicleType.XL), 0.01);
        }
    }

    @Nested
    @DisplayName("費率管理測試")
    class RatePlanManagementTests {

        @Test
        @DisplayName("取得所有費率")
        void testGetAllRatePlans() {
            List<RatePlan> plans = fareService.getAllRatePlans();
            assertEquals(3, plans.size());
        }

        @Test
        @DisplayName("取得指定車種費率")
        void testGetRatePlan() {
            RatePlan standardPlan = fareService.getRatePlan(VehicleType.STANDARD);
            
            assertNotNull(standardPlan);
            assertEquals(50.0, standardPlan.getBaseFare(), 0.01);
            assertEquals(15.0, standardPlan.getPerKmRate(), 0.01);
            assertEquals(3.0, standardPlan.getPerMinRate(), 0.01);
            assertEquals(70.0, standardPlan.getMinFare(), 0.01);
            assertEquals(30.0, standardPlan.getCancelFee(), 0.01);
        }

        @Test
        @DisplayName("更新費率")
        void testUpdateRatePlan() {
            RatePlan newPlan = RatePlan.builder()
                    .baseFare(100.0)
                    .perKmRate(20.0)
                    .perMinRate(5.0)
                    .minFare(150.0)
                    .cancelFee(50.0)
                    .build();
            
            fareService.updateRatePlan(VehicleType.STANDARD, newPlan);
            
            // 驗證更新後的計算
            double fare = fareService.calculateEstimatedFare(VehicleType.STANDARD, 10.0);
            // 新: Base (100) + 10km * 20 = 100 + 200 = 300
            assertEquals(300.0, fare, 0.01);
        }

        @Test
        @DisplayName("更新費率後取消費也更新")
        void testUpdateRatePlan_CancelFee() {
            RatePlan newPlan = RatePlan.builder()
                    .baseFare(60.0)
                    .perKmRate(18.0)
                    .perMinRate(4.0)
                    .minFare(80.0)
                    .cancelFee(40.0)
                    .build();
            
            fareService.updateRatePlan(VehicleType.STANDARD, newPlan);
            
            assertEquals(40.0, fareService.getCancelFee(VehicleType.STANDARD), 0.01);
        }
    }

    @Nested
    @DisplayName("費率表符合規格書")
    class RatePlanComplianceTests {

        @Test
        @DisplayName("STANDARD 費率符合規格書定義")
        void testStandardRatePlan_Compliance() {
            // 規格書: Base=50, PerKm=15, PerMin=3, Min=70
            RatePlan plan = fareService.getRatePlan(VehicleType.STANDARD);
            
            assertEquals(50.0, plan.getBaseFare(), 0.01);
            assertEquals(15.0, plan.getPerKmRate(), 0.01);
            assertEquals(3.0, plan.getPerMinRate(), 0.01);
            assertEquals(70.0, plan.getMinFare(), 0.01);
        }

        @Test
        @DisplayName("PREMIUM 費率符合規格書定義")
        void testPremiumRatePlan_Compliance() {
            // 規格書: Base=80, PerKm=25, PerMin=5, Min=120
            RatePlan plan = fareService.getRatePlan(VehicleType.PREMIUM);
            
            assertEquals(80.0, plan.getBaseFare(), 0.01);
            assertEquals(25.0, plan.getPerKmRate(), 0.01);
            assertEquals(5.0, plan.getPerMinRate(), 0.01);
            assertEquals(120.0, plan.getMinFare(), 0.01);
        }

        @Test
        @DisplayName("XL 費率符合規格書定義")
        void testXLRatePlan_Compliance() {
            // 規格書: Base=100, PerKm=30, PerMin=6, Min=150
            RatePlan plan = fareService.getRatePlan(VehicleType.XL);
            
            assertEquals(100.0, plan.getBaseFare(), 0.01);
            assertEquals(30.0, plan.getPerKmRate(), 0.01);
            assertEquals(6.0, plan.getPerMinRate(), 0.01);
            assertEquals(150.0, plan.getMinFare(), 0.01);
        }
    }
}
