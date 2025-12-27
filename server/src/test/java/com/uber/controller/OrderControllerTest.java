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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * OrderController 整合測試
 * 
 * Issue #15: 驗證 OrderController REST API 完整性
 */
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private FareService fareService;

    private Order sampleOrder;
    private RatePlan sampleRatePlan;

    @BeforeEach
    void setUp() {
        sampleOrder = Order.builder()
                .orderId("order-123")
                .passengerId("passenger-001")
                .status(OrderStatus.PENDING)
                .vehicleType(VehicleType.STANDARD)
                .pickupLocation(new Location(25.5, 30.2))
                .dropoffLocation(new Location(45.8, 60.1))
                .estimatedFare(150.00)
                .distance(8.5)
                .createdAt(Instant.now())
                .build();

        sampleRatePlan = RatePlan.builder()
                .vehicleType(VehicleType.STANDARD)
                .baseFare(50.0)
                .perKmRate(15.0)
                .perMinRate(3.0)
                .minFare(70.0)
                .cancelFee(30.0)
                .build();
    }

    @Nested
    @DisplayName("POST /api/orders - 建立叫車請求")
    class CreateOrderTests {

        @Test
        @DisplayName("成功建立訂單回傳 201 Created")
        void createOrder_Success() throws Exception {
            when(orderService.createOrder(anyString(), any(), any(), any()))
                    .thenReturn(sampleOrder);

            CreateOrderRequest request = new CreateOrderRequest();
            request.setPassengerId("passenger-001");
            request.setPickupX(25.5);
            request.setPickupY(30.2);
            request.setDropoffX(45.8);
            request.setDropoffY(60.1);
            request.setVehicleType(VehicleType.STANDARD);

            mockMvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.orderId").value("order-123"))
                    .andExpect(jsonPath("$.data.status").value("PENDING"))
                    .andExpect(jsonPath("$.data.passengerId").value("passenger-001"));
        }

        @Test
        @DisplayName("缺少必填欄位回傳 400 Bad Request")
        void createOrder_MissingFields() throws Exception {
            CreateOrderRequest request = new CreateOrderRequest();
            // 缺少所有必填欄位

            mockMvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/orders/{orderId} - 查詢訂單")
    class GetOrderTests {

        @Test
        @DisplayName("成功查詢訂單回傳 200 OK")
        void getOrder_Success() throws Exception {
            when(orderService.getOrder("order-123")).thenReturn(sampleOrder);

            mockMvc.perform(get("/api/orders/order-123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.orderId").value("order-123"))
                    .andExpect(jsonPath("$.data.status").value("PENDING"));
        }

        @Test
        @DisplayName("已接單訂單包含 driverId 和 acceptedAt")
        void getOrder_AcceptedOrder() throws Exception {
            Order acceptedOrder = Order.builder()
                    .orderId("order-accepted")
                    .passengerId("passenger-001")
                    .driverId("driver-456")
                    .status(OrderStatus.ACCEPTED)
                    .vehicleType(VehicleType.STANDARD)
                    .pickupLocation(new Location(25.5, 30.2))
                    .dropoffLocation(new Location(45.8, 60.1))
                    .estimatedFare(150.00)
                    .distance(8.5)
                    .createdAt(Instant.now())
                    .acceptedAt(Instant.now())
                    .build();

            when(orderService.getOrder("order-accepted")).thenReturn(acceptedOrder);

            mockMvc.perform(get("/api/orders/order-accepted"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.driverId").value("driver-456"))
                    .andExpect(jsonPath("$.data.acceptedAt").exists());
        }

        @Test
        @DisplayName("進行中訂單包含 startedAt")
        void getOrder_OngoingOrder() throws Exception {
            Order ongoingOrder = Order.builder()
                    .orderId("order-ongoing")
                    .passengerId("passenger-001")
                    .driverId("driver-456")
                    .status(OrderStatus.ONGOING)
                    .vehicleType(VehicleType.STANDARD)
                    .pickupLocation(new Location(25.5, 30.2))
                    .dropoffLocation(new Location(45.8, 60.1))
                    .estimatedFare(150.00)
                    .distance(8.5)
                    .createdAt(Instant.now())
                    .acceptedAt(Instant.now())
                    .startedAt(Instant.now())
                    .build();

            when(orderService.getOrder("order-ongoing")).thenReturn(ongoingOrder);

            mockMvc.perform(get("/api/orders/order-ongoing"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.startedAt").exists());
        }

        @Test
        @DisplayName("已完成訂單包含完整資訊")
        void getOrder_CompletedOrder() throws Exception {
            Order completedOrder = Order.builder()
                    .orderId("order-completed")
                    .passengerId("passenger-001")
                    .driverId("driver-456")
                    .status(OrderStatus.COMPLETED)
                    .vehicleType(VehicleType.STANDARD)
                    .pickupLocation(new Location(25.5, 30.2))
                    .dropoffLocation(new Location(45.8, 60.1))
                    .estimatedFare(150.00)
                    .actualFare(185.50)
                    .distance(8.5)
                    .duration(15)
                    .createdAt(Instant.now())
                    .acceptedAt(Instant.now())
                    .startedAt(Instant.now())
                    .completedAt(Instant.now())
                    .build();

            when(orderService.getOrder("order-completed")).thenReturn(completedOrder);

            mockMvc.perform(get("/api/orders/order-completed"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.completedAt").exists())
                    .andExpect(jsonPath("$.data.fare").value(185.50))
                    .andExpect(jsonPath("$.data.duration").value(15));
        }

        @Test
        @DisplayName("已取消訂單包含取消資訊")
        void getOrder_CancelledOrder() throws Exception {
            Order cancelledOrder = Order.builder()
                    .orderId("order-cancelled")
                    .passengerId("passenger-001")
                    .status(OrderStatus.CANCELLED)
                    .vehicleType(VehicleType.STANDARD)
                    .pickupLocation(new Location(25.5, 30.2))
                    .dropoffLocation(new Location(45.8, 60.1))
                    .estimatedFare(150.00)
                    .distance(8.5)
                    .createdAt(Instant.now())
                    .cancelledAt(Instant.now())
                    .cancelledBy("passenger-001")
                    .cancelFee(0.0)
                    .build();

            when(orderService.getOrder("order-cancelled")).thenReturn(cancelledOrder);

            mockMvc.perform(get("/api/orders/order-cancelled"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.data.cancelledAt").exists())
                    .andExpect(jsonPath("$.data.cancelledBy").value("passenger-001"))
                    .andExpect(jsonPath("$.data.cancelFee").value(0.0));
        }
    }

    @Nested
    @DisplayName("PUT /api/orders/{orderId}/accept - 接受訂單")
    class AcceptOrderTests {

        @Test
        @DisplayName("成功接單回傳 200 OK")
        void acceptOrder_Success() throws Exception {
            Order acceptedOrder = Order.builder()
                    .orderId("order-123")
                    .passengerId("passenger-001")
                    .driverId("driver-456")
                    .status(OrderStatus.ACCEPTED)
                    .vehicleType(VehicleType.STANDARD)
                    .pickupLocation(new Location(25.5, 30.2))
                    .dropoffLocation(new Location(45.8, 60.1))
                    .acceptedAt(Instant.now())
                    .build();

            when(orderService.acceptOrder("order-123", "driver-456"))
                    .thenReturn(acceptedOrder);

            AcceptOrderRequest request = new AcceptOrderRequest();
            request.setDriverId("driver-456");

            mockMvc.perform(put("/api/orders/order-123/accept")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.orderId").value("order-123"))
                    .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                    .andExpect(jsonPath("$.data.driverId").value("driver-456"));
        }
    }

    @Nested
    @DisplayName("PUT /api/orders/{orderId}/start - 開始行程")
    class StartTripTests {

        @Test
        @DisplayName("成功開始行程回傳 200 OK")
        void startTrip_Success() throws Exception {
            Order ongoingOrder = Order.builder()
                    .orderId("order-123")
                    .status(OrderStatus.ONGOING)
                    .driverId("driver-456")
                    .startedAt(Instant.now())
                    .build();

            when(orderService.startTrip("order-123", "driver-456"))
                    .thenReturn(ongoingOrder);

            AcceptOrderRequest request = new AcceptOrderRequest();
            request.setDriverId("driver-456");

            mockMvc.perform(put("/api/orders/order-123/start")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.orderId").value("order-123"))
                    .andExpect(jsonPath("$.data.status").value("ONGOING"));
        }
    }

    @Nested
    @DisplayName("PUT /api/orders/{orderId}/complete - 完成行程")
    class CompleteTripTests {

        @Test
        @DisplayName("成功完成行程回傳 200 OK 與費用明細")
        void completeTrip_Success() throws Exception {
            Order completedOrder = Order.builder()
                    .orderId("order-123")
                    .status(OrderStatus.COMPLETED)
                    .driverId("driver-456")
                    .vehicleType(VehicleType.STANDARD)
                    .distance(8.5)
                    .duration(15)
                    .actualFare(185.50)
                    .completedAt(Instant.now())
                    .build();

            when(orderService.completeTrip("order-123", "driver-456"))
                    .thenReturn(completedOrder);
            when(fareService.getRatePlan(VehicleType.STANDARD))
                    .thenReturn(sampleRatePlan);

            AcceptOrderRequest request = new AcceptOrderRequest();
            request.setDriverId("driver-456");

            mockMvc.perform(put("/api/orders/order-123/complete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.orderId").value("order-123"))
                    .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.data.fare").value(185.50))
                    .andExpect(jsonPath("$.data.fareBreakdown").exists());
        }
    }

    @Nested
    @DisplayName("PUT /api/orders/{orderId}/cancel - 取消訂單")
    class CancelOrderTests {

        @Test
        @DisplayName("成功取消訂單回傳 200 OK")
        void cancelOrder_Success() throws Exception {
            Order cancelledOrder = Order.builder()
                    .orderId("order-123")
                    .status(OrderStatus.CANCELLED)
                    .cancelledAt(Instant.now())
                    .cancelledBy("passenger-001")
                    .cancelFee(0.0)
                    .build();

            when(orderService.cancelOrder("order-123", "passenger-001"))
                    .thenReturn(cancelledOrder);

            CancelOrderRequest request = new CancelOrderRequest();
            request.setCancelledBy("passenger-001");
            request.setReason("等待時間過長");

            mockMvc.perform(put("/api/orders/order-123/cancel")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.orderId").value("order-123"))
                    .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.data.cancelFee").value(0.0));
        }
    }
}
