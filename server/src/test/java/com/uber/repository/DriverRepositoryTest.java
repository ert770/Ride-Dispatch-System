package com.uber.repository;

import com.uber.model.Driver;
import com.uber.model.DriverStatus;
import com.uber.model.Location;
import com.uber.model.VehicleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DriverRepository 單元測試
 *
 * 驗證所有 CRUD 操作和查詢方法，達到 100% 分支覆蓋率
 */
@DisplayName("DriverRepository 測試")
class DriverRepositoryTest {

    private DriverRepository repository;
    private Driver sampleDriver;

    @BeforeEach
    void setUp() {
        repository = new DriverRepository();

        sampleDriver = Driver.builder()
                .driverId("driver-001")
                .name("王大明")
                .phone("0912-345-678")
                .vehiclePlate("ABC-1234")
                .vehicleType(VehicleType.STANDARD)
                .status(DriverStatus.OFFLINE)
                .busy(false)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("save() - 成功儲存司機")
    void save_Success() {
        Driver saved = repository.save(sampleDriver);

        assertThat(saved).isNotNull();
        assertThat(saved.getDriverId()).isEqualTo("driver-001");
    }

    @Test
    @DisplayName("save() - 更新現有司機")
    void save_UpdateExisting() {
        repository.save(sampleDriver);

        Driver updated = Driver.builder()
                .driverId("driver-001")
                .name("王大明")
                .phone("0912-345-678")
                .vehiclePlate("ABC-1234")
                .vehicleType(VehicleType.STANDARD)
                .status(DriverStatus.ONLINE)
                .busy(false)
                .lastUpdatedAt(Instant.now())
                .build();

        Driver saved = repository.save(updated);

        assertThat(saved.getStatus()).isEqualTo(DriverStatus.ONLINE);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("findById() - 成功找到司機")
    void findById_Found() {
        repository.save(sampleDriver);

        Optional<Driver> found = repository.findById("driver-001");

        assertThat(found).isPresent();
        assertThat(found.get().getDriverId()).isEqualTo("driver-001");
    }

    @Test
    @DisplayName("findById() - 找不到司機")
    void findById_NotFound() {
        Optional<Driver> found = repository.findById("non-existent");

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findAll() - 回傳所有司機")
    void findAll_ReturnsAll() {
        Driver driver1 = sampleDriver;
        Driver driver2 = Driver.builder()
                .driverId("driver-002")
                .name("李小華")
                .vehicleType(VehicleType.PREMIUM)
                .status(DriverStatus.ONLINE)
                .busy(false)
                .lastUpdatedAt(Instant.now())
                .build();

        repository.save(driver1);
        repository.save(driver2);

        List<Driver> all = repository.findAll();

        assertThat(all).hasSize(2);
    }

    @Test
    @DisplayName("findAll() - 空列表")
    void findAll_Empty() {
        List<Driver> all = repository.findAll();

        assertThat(all).isEmpty();
    }

    @Test
    @DisplayName("findAvailableDrivers() - 找到符合條件的司機")
    void findAvailableDrivers_Found() {
        Driver availableDriver = Driver.builder()
                .driverId("driver-001")
                .name("王大明")
                .phone("0912-345-678")
                .vehiclePlate("ABC-1234")
                .vehicleType(VehicleType.STANDARD)
                .status(DriverStatus.ONLINE)
                .busy(false)
                .lastUpdatedAt(Instant.now())
                .build();

        repository.save(availableDriver);

        List<Driver> available = repository.findAvailableDrivers(VehicleType.STANDARD);

        assertThat(available).hasSize(1);
        assertThat(available.getFirst().getDriverId()).isEqualTo("driver-001");
    }

    @Test
    @DisplayName("findAvailableDrivers() - 排除離線司機")
    void findAvailableDrivers_ExcludeOffline() {
        Driver offlineDriver = Driver.builder()
                .driverId("driver-001")
                .name("王大明")
                .vehicleType(VehicleType.STANDARD)
                .status(DriverStatus.OFFLINE)
                .busy(false)
                .lastUpdatedAt(Instant.now())
                .build();

        repository.save(offlineDriver);

        List<Driver> available = repository.findAvailableDrivers(VehicleType.STANDARD);

        assertThat(available).isEmpty();
    }

    @Test
    @DisplayName("findAvailableDrivers() - 排除忙碌司機")
    void findAvailableDrivers_ExcludeBusy() {
        Driver busyDriver = Driver.builder()
                .driverId("driver-001")
                .name("王大明")
                .vehicleType(VehicleType.STANDARD)
                .status(DriverStatus.ONLINE)
                .busy(true)
                .lastUpdatedAt(Instant.now())
                .build();

        repository.save(busyDriver);

        List<Driver> available = repository.findAvailableDrivers(VehicleType.STANDARD);

        assertThat(available).isEmpty();
    }

    @Test
    @DisplayName("findAvailableDrivers() - 排除不同車型司機")
    void findAvailableDrivers_ExcludeDifferentVehicleType() {
        Driver premiumDriver = Driver.builder()
                .driverId("driver-001")
                .name("王大明")
                .vehicleType(VehicleType.PREMIUM)
                .status(DriverStatus.ONLINE)
                .busy(false)
                .lastUpdatedAt(Instant.now())
                .build();

        repository.save(premiumDriver);

        List<Driver> available = repository.findAvailableDrivers(VehicleType.STANDARD);

        assertThat(available).isEmpty();
    }

    @Test
    @DisplayName("findAvailableDrivers() - 符合所有條件的司機")
    void findAvailableDrivers_MultipleConditions() {
        // 符合條件：ONLINE + 不忙 + 正確車型
        Driver available = Driver.builder()
                .driverId("driver-available")
                .name("可用司機")
                .vehicleType(VehicleType.STANDARD)
                .status(DriverStatus.ONLINE)
                .busy(false)
                .lastUpdatedAt(Instant.now())
                .build();

        // 不符合：OFFLINE
        Driver offline = Driver.builder()
                .driverId("driver-offline")
                .name("離線司機")
                .vehicleType(VehicleType.STANDARD)
                .status(DriverStatus.OFFLINE)
                .busy(false)
                .lastUpdatedAt(Instant.now())
                .build();

        // 不符合：忙碌
        Driver busy = Driver.builder()
                .driverId("driver-busy")
                .name("忙碌司機")
                .vehicleType(VehicleType.STANDARD)
                .status(DriverStatus.ONLINE)
                .busy(true)
                .lastUpdatedAt(Instant.now())
                .build();

        // 不符合：車型不同
        Driver premium = Driver.builder()
                .driverId("driver-premium")
                .name("高級車司機")
                .vehicleType(VehicleType.PREMIUM)
                .status(DriverStatus.ONLINE)
                .busy(false)
                .lastUpdatedAt(Instant.now())
                .build();

        repository.save(available);
        repository.save(offline);
        repository.save(busy);
        repository.save(premium);

        List<Driver> result = repository.findAvailableDrivers(VehicleType.STANDARD);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getDriverId()).isEqualTo("driver-available");
    }

    @Test
    @DisplayName("findOnlineDrivers() - 找到所有線上司機")
    void findOnlineDrivers_Found() {
        Driver onlineDriver1 = Driver.builder()
                .driverId("driver-online-1")
                .name("線上司機1")
                .vehicleType(VehicleType.STANDARD)
                .status(DriverStatus.ONLINE)
                .busy(false)
                .lastUpdatedAt(Instant.now())
                .build();

        Driver onlineDriver2 = Driver.builder()
                .driverId("driver-online-2")
                .name("線上司機2")
                .vehicleType(VehicleType.STANDARD)
                .status(DriverStatus.ONLINE)
                .busy(true)
                .lastUpdatedAt(Instant.now())
                .build();

        Driver offlineDriver = Driver.builder()
                .driverId("driver-offline")
                .name("離線司機")
                .vehicleType(VehicleType.STANDARD)
                .status(DriverStatus.OFFLINE)
                .busy(false)
                .lastUpdatedAt(Instant.now())
                .build();

        repository.save(onlineDriver1);
        repository.save(onlineDriver2);
        repository.save(offlineDriver);

        List<Driver> online = repository.findOnlineDrivers();

        assertThat(online).hasSize(2);
    }

    @Test
    @DisplayName("findOnlineDrivers() - 空列表")
    void findOnlineDrivers_Empty() {
        repository.save(sampleDriver); // OFFLINE

        List<Driver> online = repository.findOnlineDrivers();

        assertThat(online).isEmpty();
    }

    @Test
    @DisplayName("deleteAll() - 清空所有司機")
    void deleteAll_Success() {
        repository.save(sampleDriver);
        assertThat(repository.count()).isEqualTo(1);

        repository.deleteAll();

        assertThat(repository.count()).isEqualTo(0);
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("count() - 回傳正確數量")
    void count_ReturnsCorrectCount() {
        assertThat(repository.count()).isEqualTo(0);

        repository.save(sampleDriver);
        assertThat(repository.count()).isEqualTo(1);

        Driver driver2 = Driver.builder()
                .driverId("driver-002")
                .name("李小華")
                .vehicleType(VehicleType.STANDARD)
                .status(DriverStatus.OFFLINE)
                .busy(false)
                .lastUpdatedAt(Instant.now())
                .build();
        repository.save(driver2);
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("並發場景 - 多個司機同時操作")
    void concurrentScenario() {
        // 模擬多個司機同時註冊和更新狀態
        Driver d1 = Driver.builder()
                .driverId("driver-001")
                .name("司機1")
                .vehicleType(VehicleType.STANDARD)
                .status(DriverStatus.OFFLINE)
                .busy(false)
                .lastUpdatedAt(Instant.now())
                .build();

        Driver d2 = Driver.builder()
                .driverId("driver-002")
                .name("司機2")
                .vehicleType(VehicleType.PREMIUM)
                .status(DriverStatus.ONLINE)
                .busy(false)
                .location(new Location(25.0, 30.0))
                .lastUpdatedAt(Instant.now())
                .build();

        repository.save(d1);
        repository.save(d2);

        // 更新 d1 上線
        Driver d1Online = Driver.builder()
                .driverId("driver-001")
                .name("司機1")
                .vehicleType(VehicleType.STANDARD)
                .status(DriverStatus.ONLINE)
                .busy(false)
                .location(new Location(26.0, 31.0))
                .lastUpdatedAt(Instant.now())
                .build();
        repository.save(d1Online);

        // 驗證狀態
        assertThat(repository.count()).isEqualTo(2);
        assertThat(repository.findOnlineDrivers()).hasSize(2);
        assertThat(repository.findAvailableDrivers(VehicleType.STANDARD)).hasSize(1);
        assertThat(repository.findAvailableDrivers(VehicleType.PREMIUM)).hasSize(1);
    }
}

