package com.uber.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uber.dto.*;
import com.uber.model.*;
import com.uber.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * DriverController 整合測試
 * 
 * Issue #16: 驗證 DriverController REST API 完整性
 */
@WebMvcTest(DriverController.class)
class DriverControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DriverService driverService;

    private Driver sampleDriver;

    @BeforeEach
    void setUp() {
        sampleDriver = Driver.builder()
                .driverId("driver-456")
                .name("王大明")
                .phone("0912-345-678")
                .vehiclePlate("ABC-1234")
                .vehicleType(VehicleType.STANDARD)
                .status(DriverStatus.OFFLINE)
                .busy(false)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("POST /api/drivers - 註冊司機")
    class RegisterDriverTests {

        @Test
        @DisplayName("成功註冊司機回傳 201 Created")
        void registerDriver_Success() throws Exception {
            when(driverService.registerDriver(anyString(), anyString(), anyString(), anyString(), any()))
                    .thenReturn(sampleDriver);

            RegisterDriverRequest request = new RegisterDriverRequest();
            request.setDriverId("driver-456");
            request.setName("王大明");
            request.setPhone("0912-345-678");
            request.setVehiclePlate("ABC-1234");
            request.setVehicleType(VehicleType.STANDARD);

            mockMvc.perform(post("/api/drivers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.driverId").value("driver-456"))
                    .andExpect(jsonPath("$.data.name").value("王大明"));
        }
    }

    @Nested
    @DisplayName("PUT /api/drivers/{driverId}/online - 司機上線")
    class GoOnlineTests {

        @Test
        @DisplayName("成功上線回傳 200 OK")
        void goOnline_Success() throws Exception {
            Driver onlineDriver = Driver.builder()
                    .driverId("driver-456")
                    .name("王大明")
                    .status(DriverStatus.ONLINE)
                    .vehicleType(VehicleType.STANDARD)
                    .location(new Location(20.0, 25.0))
                    .busy(false)
                    .lastUpdatedAt(Instant.now())
                    .build();

            when(driverService.goOnline(eq("driver-456"), any(Location.class)))
                    .thenReturn(onlineDriver);

            DriverOnlineRequest request = new DriverOnlineRequest();
            request.setX(20.0);
            request.setY(25.0);

            mockMvc.perform(put("/api/drivers/driver-456/online")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.driverId").value("driver-456"))
                    .andExpect(jsonPath("$.data.status").value("ONLINE"));
        }
    }

    @Nested
    @DisplayName("PUT /api/drivers/{driverId}/offline - 司機下線")
    class GoOfflineTests {

        @Test
        @DisplayName("成功下線回傳 200 OK")
        void goOffline_Success() throws Exception {
            Driver offlineDriver = Driver.builder()
                    .driverId("driver-456")
                    .name("王大明")
                    .status(DriverStatus.OFFLINE)
                    .vehicleType(VehicleType.STANDARD)
                    .lastUpdatedAt(Instant.now())
                    .build();

            when(driverService.goOffline("driver-456")).thenReturn(offlineDriver);

            mockMvc.perform(put("/api/drivers/driver-456/offline"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.driverId").value("driver-456"))
                    .andExpect(jsonPath("$.data.status").value("OFFLINE"));
        }
    }

    @Nested
    @DisplayName("PUT /api/drivers/{driverId}/location - 更新位置")
    class UpdateLocationTests {

        @Test
        @DisplayName("成功更新位置回傳 200 OK")
        void updateLocation_Success() throws Exception {
            Driver updatedDriver = Driver.builder()
                    .driverId("driver-456")
                    .location(new Location(22.5, 28.3))
                    .lastUpdatedAt(Instant.now())
                    .build();

            when(driverService.updateLocation(eq("driver-456"), any(Location.class)))
                    .thenReturn(updatedDriver);

            Location location = new Location(22.5, 28.3);

            mockMvc.perform(put("/api/drivers/driver-456/location")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(location)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.driverId").value("driver-456"))
                    .andExpect(jsonPath("$.data.location.x").value(22.5))
                    .andExpect(jsonPath("$.data.location.y").value(28.3));
        }
    }

    @Nested
    @DisplayName("GET /api/drivers/{driverId}/offers - 取得可接訂單")
    class GetOffersTests {

        @Test
        @DisplayName("成功取得訂單列表回傳 200 OK")
        void getOffers_Success() throws Exception {
            Order order1 = Order.builder()
                    .orderId("order-123")
                    .pickupLocation(new Location(25.5, 30.2))
                    .dropoffLocation(new Location(45.8, 60.1))
                    .vehicleType(VehicleType.STANDARD)
                    .distance(5.2)
                    .estimatedFare(150.00)
                    .createdAt(Instant.now())
                    .build();

            when(driverService.getOffers("driver-456")).thenReturn(List.of(order1));

            mockMvc.perform(get("/api/drivers/driver-456/offers"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.offers").isArray())
                    .andExpect(jsonPath("$.data.offers[0].orderId").value("order-123"))
                    .andExpect(jsonPath("$.data.count").value(1));
        }

        @Test
        @DisplayName("無訂單時回傳空列表")
        void getOffers_Empty() throws Exception {
            when(driverService.getOffers("driver-456")).thenReturn(List.of());

            mockMvc.perform(get("/api/drivers/driver-456/offers"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.offers").isEmpty())
                    .andExpect(jsonPath("$.data.count").value(0));
        }
    }

    @Nested
    @DisplayName("GET /api/drivers/{driverId} - 取得司機資訊")
    class GetDriverTests {

        @Test
        @DisplayName("成功取得司機資訊回傳 200 OK")
        void getDriver_Success() throws Exception {
            when(driverService.getDriver("driver-456")).thenReturn(sampleDriver);

            mockMvc.perform(get("/api/drivers/driver-456"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.driverId").value("driver-456"))
                    .andExpect(jsonPath("$.data.name").value("王大明"));
        }

        @Test
        @DisplayName("司機包含所有選填欄位")
        void getDriver_WithAllFields() throws Exception {
            Driver fullDriver = Driver.builder()
                    .driverId("driver-full")
                    .name("完整司機")
                    .phone("0912-345-678")
                    .vehiclePlate("XYZ-9999")
                    .vehicleType(VehicleType.PREMIUM)
                    .status(DriverStatus.ONLINE)
                    .location(new Location(25.0, 30.0))
                    .busy(true)
                    .currentOrderId("order-999")
                    .lastUpdatedAt(Instant.now())
                    .build();

            when(driverService.getDriver("driver-full")).thenReturn(fullDriver);

            mockMvc.perform(get("/api/drivers/driver-full"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.phone").value("0912-345-678"))
                    .andExpect(jsonPath("$.data.vehiclePlate").value("XYZ-9999"))
                    .andExpect(jsonPath("$.data.location").exists())
                    .andExpect(jsonPath("$.data.currentOrderId").value("order-999"));
        }

        @Test
        @DisplayName("司機無選填欄位時正確處理")
        void getDriver_WithMinimalFields() throws Exception {
            Driver minimalDriver = Driver.builder()
                    .driverId("driver-minimal")
                    .name("最小司機")
                    .status(DriverStatus.OFFLINE)
                    .vehicleType(VehicleType.STANDARD)
                    .busy(false)
                    .lastUpdatedAt(Instant.now())
                    .build();

            when(driverService.getDriver("driver-minimal")).thenReturn(minimalDriver);

            mockMvc.perform(get("/api/drivers/driver-minimal"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.phone").doesNotExist())
                    .andExpect(jsonPath("$.data.vehiclePlate").doesNotExist())
                    .andExpect(jsonPath("$.data.location").doesNotExist())
                    .andExpect(jsonPath("$.data.currentOrderId").doesNotExist());
        }
    }

    @Nested
    @DisplayName("GET /api/drivers - 取得所有司機")
    class GetAllDriversTests {

        @Test
        @DisplayName("成功取得所有司機回傳 200 OK")
        void getAllDrivers_Success() throws Exception {
            when(driverService.getAllDrivers()).thenReturn(List.of(sampleDriver));

            mockMvc.perform(get("/api/drivers"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.drivers").isArray())
                    .andExpect(jsonPath("$.data.drivers[0].driverId").value("driver-456"))
                    .andExpect(jsonPath("$.data.count").value(1));
        }

        @Test
        @DisplayName("支援狀態篩選")
        void getAllDrivers_WithStatusFilter() throws Exception {
            Driver onlineDriver = Driver.builder()
                    .driverId("driver-456")
                    .name("王大明")
                    .status(DriverStatus.ONLINE)
                    .vehicleType(VehicleType.STANDARD)
                    .build();

            when(driverService.getAllDrivers()).thenReturn(List.of(onlineDriver, sampleDriver));

            mockMvc.perform(get("/api/drivers")
                            .param("status", "ONLINE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.count").value(1));
        }

        @Test
        @DisplayName("空字串狀態參數時不篩選")
        void getAllDrivers_WithEmptyStatus() throws Exception {
            when(driverService.getAllDrivers()).thenReturn(List.of(sampleDriver));

            mockMvc.perform(get("/api/drivers")
                            .param("status", ""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.count").value(1));
        }
    }
}
