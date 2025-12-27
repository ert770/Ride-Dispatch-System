# 系統規格書 (System Specification Document)

## 1. 專案概述 (Project Overview)

### 1.1 題目
**共乘／叫車平台 (Ride-Hailing Platform)**

### 1.2 專案定位
本專案為 Uber / Taxi 的核心邏輯模擬系統。
*   **核心重點**：派單與調度後台邏輯（配對、計價、狀態機、併發一致性）。
*   **展示方式**：透過三個模擬手機介面的視窗（乘客端、司機端、管理員端）進行即時互動展示。

### 1.3 專案目標
| 目標編號 | 目標描述 | 驗收標準 |
|---------|---------|---------|
| G1 | 實現完整的叫車流程 | 乘客可發起叫車、司機可接單、完成行程 |
| G2 | 確保併發安全性 | 多司機搶單僅 1 人成功，其餘回傳 409 |
| G3 | 實現冪等性操作 | 重複請求不產生副作用 |
| G4 | 提供完整審計軌跡 | 所有操作記錄於 Audit Log |

---

## 2. 系統架構 (System Architecture)

### 2.1 架構模式
採用 **Client-Server** 架構，模擬真實系統的分層設計。

```
┌─────────────────────────────────────────────────────────────┐
│                     Client Layer (JavaFX)                    │
├──────────────┬──────────────────┬───────────────────────────┤
│ Passenger App│   Driver App     │      Admin Console        │
│  (乘客端)     │    (司機端)       │       (管理後台)          │
└──────┬───────┴────────┬─────────┴─────────────┬─────────────┘
       │                │                       │
       │         HTTP/REST (Polling)            │
       │                │                       │
┌──────▼────────────────▼───────────────────────▼─────────────┐
│                   Server Layer (Spring Boot)                 │
├─────────────────────────────────────────────────────────────┤
│  Controller Layer (REST API)                                 │
├─────────────────────────────────────────────────────────────┤
│  Service Layer (Business Logic)                              │
│  ┌──────────┬──────────┬──────────┬──────────┬────────────┐ │
│  │OrderSvc  │MatchSvc  │ FareSvc  │DriverSvc│ AuditSvc   │ │
│  └──────────┴──────────┴──────────┴──────────┴────────────┘ │
├─────────────────────────────────────────────────────────────┤
│  Repository Layer (In-Memory / JSON)                         │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 技術堆疊
| 層級 | 技術選型 | 版本 | 用途 |
|-----|---------|-----|-----|
| Client | JavaFX | 17+ | 桌面 UI 應用程式 |
| Server | Spring Boot | 3.x | RESTful API 服務 |
| Build | Maven | 3.9+ | 專案建構與依賴管理 |
| Test | JUnit 5 | 5.10+ | 單元與整合測試 |
| Test | Mockito | 5.x | Mock 物件框架 |
| Quality | JaCoCo | 0.8.11+ | 測試覆蓋率分析 |
| Quality | PMD | 7.x | 靜態程式碼分析 |

### 2.3 通訊協定
*   **協定類型**：HTTP/REST
*   **資料格式**：JSON
*   **同步機制**：Polling（每 0.5–1 秒輪詢）

---

## 3. 功能需求 (Functional Requirements)

### 3.1 乘客端應用 (Passenger App)

#### 3.1.1 使用案例 (Use Cases)
| UC-ID | 名稱 | 描述 | 前置條件 | 後置條件 |
|-------|-----|------|---------|---------|
| UC-P01 | 建立叫車 | 乘客輸入上下車座標發起叫車 | 乘客已開啟 App | 訂單狀態為 PENDING |
| UC-P02 | 查看訂單狀態 | 即時顯示訂單進度 | 存在有效訂單 | 顯示最新狀態 |
| UC-P03 | 取消訂單 | 依據規則取消訂單 | 訂單狀態允許取消 | 狀態變更為 CANCELLED |

#### 3.1.2 功能規格
```
FR-P01: 建立叫車
  輸入:
    - pickupX: double (上車點 X 座標)
    - pickupY: double (上車點 Y 座標)
    - dropoffX: double (下車點 X 座標)
    - dropoffY: double (下車點 Y 座標)
    - vehicleType: enum {STANDARD, PREMIUM, XL}
  輸出:
    - orderId: String (UUID)
    - status: PENDING
    - estimatedFare: double
  驗證規則:
    - 座標範圍: 0 ≤ x, y ≤ 100
    - 上下車點不可相同
```

### 3.2 司機端應用 (Driver App)

#### 3.2.1 使用案例 (Use Cases)
| UC-ID | 名稱 | 描述 | 前置條件 | 後置條件 |
|-------|-----|------|---------|---------|
| UC-D01 | 上線/下線 | 切換接單狀態 | 已選擇司機身份 | 狀態已更新 |
| UC-D02 | 更新位置 | 設定目前地理座標 | 司機已上線 | 位置已記錄 |
| UC-D03 | 取得訂單列表 | 查看可接訂單 | 司機 Online 且非 Busy | 回傳符合條件訂單 |
| UC-D04 | 接受訂單 | 搶單/接單 | 訂單 PENDING | 訂單 ACCEPTED |
| UC-D05 | 開始行程 | 標記行程開始 | 訂單 ACCEPTED | 訂單 ONGOING |
| UC-D06 | 完成行程 | 標記行程結束 | 訂單 ONGOING | 訂單 COMPLETED |

### 3.3 管理後台 (Admin Console)

#### 3.3.1 使用案例 (Use Cases)
| UC-ID | 名稱 | 描述 |
|-------|-----|------|
| UC-A01 | 訂單監控 | 查看所有訂單列表與詳細資訊 |
| UC-A02 | Audit Log | 檢視事件時間軸與成功/失敗原因 |
| UC-A03 | 配對策略設定 | 配置搜尋半徑、車種篩選規則 |
| UC-A04 | 費率設定 | 管理計價參數 |

---

## 4. 核心邏輯與演算法 (Core Logic & Algorithms)

### 4.1 訂單狀態機 (Order State Machine)

```
                    ┌─────────────┐
                    │   PENDING   │
                    └──────┬──────┘
                           │
              ┌────────────┼────────────┐
              │ accept()   │            │ cancel()
              ▼            │            ▼
        ┌─────────────┐    │    ┌─────────────┐
        │  ACCEPTED   │    │    │  CANCELLED  │
        └──────┬──────┘    │    └─────────────┘
               │           │            ▲
               │ start()   │            │ cancel()
               ▼           │            │
        ┌─────────────┐    │            │
        │   ONGOING   │────┘            │
        └──────┬──────┘─────────────────┘
               │
               │ complete()
               ▼
        ┌─────────────┐
        │  COMPLETED  │
        └─────────────┘
```

#### 4.1.1 狀態轉換表
| 當前狀態 | 事件 | 目標狀態 | 驗證規則 |
|---------|-----|---------|---------|
| PENDING | accept | ACCEPTED | 訂單存在、司機 Online、非 Busy |
| PENDING | cancel | CANCELLED | 訂單存在、來源為乘客 |
| ACCEPTED | start | ONGOING | 操作者為指派司機 |
| ACCEPTED | cancel | CANCELLED | 需計算取消費 |
| ONGOING | complete | COMPLETED | 操作者為指派司機 |

### 4.2 配對演算法 (Matching Algorithm)

```java
Algorithm: FindBestDriver(order)
Input: Order with pickup location and vehicle type
Output: Best matching Driver or null

1. candidates ← []
2. FOR each driver IN driverRepository:
     IF driver.status == ONLINE AND driver.busy == false:
       IF driver.vehicleType == order.vehicleType:
         distance ← calculateDistance(driver.location, order.pickup)
         candidates.add((driver, distance))

3. SORT candidates BY distance ASC, driverId ASC

4. IF candidates is not empty:
     RETURN candidates[0].driver
   ELSE:
     RETURN null
```

### 4.3 計價模型 (Pricing Model)

#### 4.3.1 車資公式
```
Total Fare = max(
    Base Fare + (Distance × Per Km Rate) + (Duration × Per Min Rate),
    Min Fare
)
```

#### 4.3.2 費率表
| 車種 | Base Fare | Per Km | Per Min | Min Fare |
|-----|----------|--------|---------|----------|
| STANDARD | $50 | $15 | $3 | $70 |
| PREMIUM | $80 | $25 | $5 | $120 |
| XL | $100 | $30 | $6 | $150 |

---

## 5. API 規格 (API Specification)

### 5.1 訂單 API

| Method | Endpoint | 描述 | 回應碼 |
|--------|----------|-----|-------|
| POST | /api/orders | 建立訂單 | 201, 400 |
| GET | /api/orders/{id} | 查詢訂單 | 200, 404 |
| PUT | /api/orders/{id}/accept | 接受訂單 | 200, 409, 400 |
| PUT | /api/orders/{id}/start | 開始行程 | 200, 400 |
| PUT | /api/orders/{id}/complete | 完成行程 | 200, 400 |
| PUT | /api/orders/{id}/cancel | 取消訂單 | 200, 400 |

### 5.2 錯誤碼定義
| HTTP Code | Error Code | 描述 |
|-----------|-----------|------|
| 400 | INVALID_STATE | 非法狀態轉換 |
| 404 | NOT_FOUND | 資源不存在 |
| 409 | CONFLICT | 併發衝突（搶單失敗） |

---

## 6. 非功能需求 (Non-Functional Requirements)

### 6.1 併發控制 (Concurrency Control)
*   **搶單情境 (H2)**：10 司機同時 Accept，僅 1 成功
*   **實作方式**：使用 `synchronized` 或 `ReentrantLock`

### 6.2 冪等性 (Idempotency)
*   **重送情境 (H4)**：重複請求不產生副作用
*   **實作方式**：檢查當前狀態，已處理則回傳成功

---

## 7. 測試計畫 (Test Plan)

### 7.1 測試策略概述

```
┌─────────────────────────────────────────────────────────────┐
│                      測試金字塔                              │
├─────────────────────────────────────────────────────────────┤
│                    ┌─────────┐                              │
│                    │  E2E    │  ← 手動 Demo                 │
│                   ┌┴─────────┴┐                             │
│                   │Integration│  ← 流程驗證                 │
│                  ┌┴───────────┴┐                            │
│                  │  Unit Tests │  ← 核心邏輯                │
│                 └──────────────┘                            │
└─────────────────────────────────────────────────────────────┘
```

### 7.2 單元測試 (Unit Tests)

#### 7.2.1 測試案例清單
| 測試 ID | 測試類別 | 測試方法 | 預期結果 |
|--------|---------|---------|---------|
| UT-D01 | 距離計算 | testDistance_GeneralCase | 正確計算歐幾里得距離 |
| UT-D02 | 距離計算 | testDistance_SamePoint | 回傳 0 |
| UT-D03 | 距離計算 | testDistance_Symmetry | d(A,B) == d(B,A) |
| UT-M01 | 配對邏輯 | testMatch_OnlineDriverOnly | 僅回傳上線司機 |
| UT-M02 | 配對邏輯 | testMatch_NonBusyOnly | 排除忙碌司機 |
| UT-M03 | 配對邏輯 | testMatch_VehicleTypeFilter | 車種篩選正確 |
| UT-M04 | 配對邏輯 | testMatch_DistanceSort | 距離最近者優先 |
| UT-M05 | 配對邏輯 | testMatch_TieBreakById | ID 小者勝出 |
| UT-F01 | 車資計算 | testFare_NormalCalc | 公式計算正確 |
| UT-F02 | 車資計算 | testFare_MinFareBoundary | 低於最低車資則收最低 |
| UT-F03 | 車資計算 | testFare_CancelFee | 取消費計算正確 |
| UT-S01 | 狀態機 | testState_PendingToAccepted | 合法轉換成功 |
| UT-S02 | 狀態機 | testState_IllegalTransition | 非法轉換拒絕 |

### 7.3 整合測試 (Integration Tests)

#### 7.3.1 完整流程測試
```
IT-01: Happy Path Test
  1. POST /api/orders → 201, status=PENDING
  2. PUT /api/drivers/{id}/online → 200
  3. GET /api/drivers/{id}/offers → 包含新訂單
  4. PUT /api/orders/{id}/accept → 200, status=ACCEPTED
  5. PUT /api/orders/{id}/start → 200, status=ONGOING
  6. PUT /api/orders/{id}/complete → 200, status=COMPLETED, fare > 0
```

### 7.4 併發測試 (Concurrency Tests)

#### 7.4.1 CT-H2: 搶單測試
```java
@Test
void testConcurrentAccept_OnlyOneSucceeds() {
    // Given: 1 PENDING order, 10 drivers
    Order order = createPendingOrder();
    List<Driver> drivers = createOnlineDrivers(10);
    
    // When: 10 threads accept simultaneously
    ExecutorService executor = Executors.newFixedThreadPool(10);
    List<Future<Response>> futures = drivers.stream()
        .map(d -> executor.submit(() -> acceptOrder(order.id, d.id)))
        .collect(toList());
    
    // Then: exactly 1 success (200), 9 conflicts (409)
    long successCount = futures.stream()
        .filter(f -> f.get().status == 200).count();
    assertEquals(1, successCount);
}
```

---

## 8. 軟體品質保證 (Software Quality Assurance)

### 8.1 PMD 程式碼審查 (PMD Code Review)

#### 8.1.1 PMD 規則集配置
```xml
<!-- pmd-ruleset.xml -->
<?xml version="1.0"?>
<ruleset name="UberPlatform Rules">
    <description>Custom PMD rules for Uber Platform</description>
    
    <!-- Best Practices -->
    <rule ref="category/java/bestpractices.xml/UnusedLocalVariable"/>
    <rule ref="category/java/bestpractices.xml/UnusedPrivateField"/>
    <rule ref="category/java/bestpractices.xml/AvoidReassigningParameters"/>
    
    <!-- Code Style -->
    <rule ref="category/java/codestyle.xml/ClassNamingConventions"/>
    <rule ref="category/java/codestyle.xml/MethodNamingConventions"/>
    <rule ref="category/java/codestyle.xml/FieldNamingConventions"/>
    
    <!-- Design -->
    <rule ref="category/java/design.xml/CyclomaticComplexity">
        <properties>
            <property name="methodReportLevel" value="10"/>
        </properties>
    </rule>
    <rule ref="category/java/design.xml/NPathComplexity"/>
    <rule ref="category/java/design.xml/CognitiveComplexity"/>
    
    <!-- Error Prone -->
    <rule ref="category/java/errorprone.xml/EmptyCatchBlock"/>
    <rule ref="category/java/errorprone.xml/AvoidBranchingStatementAsLastInLoop"/>
    
    <!-- Multithreading -->
    <rule ref="category/java/multithreading.xml/AvoidSynchronizedAtMethodLevel"/>
    <rule ref="category/java/multithreading.xml/UseConcurrentHashMap"/>
</ruleset>
```

#### 8.1.2 PMD Maven 配置
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-pmd-plugin</artifactId>
    <version>3.21.2</version>
    <configuration>
        <rulesets>
            <ruleset>pmd-ruleset.xml</ruleset>
        </rulesets>
        <printFailingErrors>true</printFailingErrors>
        <failOnViolation>true</failOnViolation>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

#### 8.1.3 PMD 報告範例
```
================================================================================
PMD Code Review Report
專案: Uber Platform
日期: 2025-12-25
================================================================================

Summary:
  Total Files Analyzed: 25
  Total Violations: 3
  Priority 1 (Blocker): 0
  Priority 2 (Critical): 1
  Priority 3 (Major): 2

Violations:
┌────────────────────────────────────────────────────────────────────────────┐
│ File: OrderService.java                                                    │
│ Line: 45                                                                   │
│ Rule: CyclomaticComplexity                                                 │
│ Priority: 2                                                                │
│ Message: Method 'processOrder' has a cyclomatic complexity of 12          │
│ Recommendation: Refactor into smaller methods                              │
└────────────────────────────────────────────────────────────────────────────┘

Status: ✅ PASSED (No Priority 1 violations)
```

### 8.2 JaCoCo 測試覆蓋率 (JaCoCo Coverage)

#### 8.2.1 JaCoCo Maven 配置
```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.11</version>
    <executions>
        <execution>
            <id>prepare-agent</id>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals><goal>report</goal></goals>
        </execution>
        <execution>
            <id>check</id>
            <goals><goal>check</goal></goals>
            <configuration>
                <rules>
                    <rule>
                        <element>BUNDLE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.80</minimum>
                            </limit>
                            <limit>
                                <counter>BRANCH</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.70</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

#### 8.2.2 覆蓋率目標
| 指標 | 目標值 | 說明 |
|-----|-------|-----|
| Line Coverage | ≥ 80% | 程式碼行覆蓋率 |
| Branch Coverage | ≥ 70% | 分支覆蓋率 |
| Method Coverage | ≥ 75% | 方法覆蓋率 |
| Class Coverage | 100% | 類別覆蓋率 |

#### 8.2.3 JaCoCo 報告範例
```
================================================================================
JaCoCo Coverage Report
專案: Uber Platform
日期: 2025-12-25
================================================================================

Overall Coverage:
┌─────────────────┬──────────┬──────────┬──────────┐
│ Metric          │ Covered  │ Missed   │ Coverage │
├─────────────────┼──────────┼──────────┼──────────┤
│ Instructions    │ 2,456    │ 312      │ 88.7%    │
│ Branches        │ 189      │ 45       │ 80.8%    │
│ Lines           │ 567      │ 89       │ 86.4%    │
│ Methods         │ 78       │ 12       │ 86.7%    │
│ Classes         │ 18       │ 0        │ 100%     │
└─────────────────┴──────────┴──────────┴──────────┘

Package Coverage:
┌─────────────────────────────────┬───────────┬──────────┐
│ Package                         │ Line Cov. │ Branch   │
├─────────────────────────────────┼───────────┼──────────┤
│ com.uber.service               │ 92.3%     │ 85.2%    │
│ com.uber.controller            │ 88.1%     │ 78.4%    │
│ com.uber.model                 │ 95.0%     │ 90.0%    │
│ com.uber.repository            │ 82.5%     │ 72.1%    │
└─────────────────────────────────┴───────────┴──────────┘

Critical Classes Coverage:
┌─────────────────────────────────┬───────────┬──────────┐
│ Class                           │ Line Cov. │ Branch   │
├─────────────────────────────────┼───────────┼──────────┤
│ OrderService                    │ 95.2%     │ 88.9%    │
│ MatchingService                 │ 91.8%     │ 84.6%    │
│ FareCalculator                  │ 98.5%     │ 95.0%    │
│ OrderStateMachine              │ 100%      │ 100%     │
└─────────────────────────────────┴───────────┴──────────┘

Status: ✅ PASSED (All coverage thresholds met)
```

### 8.3 測試執行指令

```bash
# 執行所有測試並產生報告
mvn clean test

# 產生 JaCoCo 覆蓋率報告
mvn jacoco:report
# 報告位置: target/site/jacoco/index.html

# 執行 PMD 程式碼分析
mvn pmd:pmd pmd:check
# 報告位置: target/pmd.xml

# 一次執行所有品質檢查
mvn clean verify
```

---

## 9. 開發工作流 (Development Workflow)

### 9.1 Git 分支策略
```
main
  └── develop
        ├── feature/order-service
        ├── feature/matching-algorithm
        ├── feature/fare-calculator
        └── feature/javafx-client
```

### 9.2 開發階段
| 階段 | 交付物 | 負責人 |
|-----|-------|-------|
| Phase 1 | API 規格、狀態機表、錯誤碼定義 | 設計者 |
| Phase 2 | 核心服務 (StateMachine, Matching, Fare) | 後端開發 |
| Phase 3 | REST Controller + 整合測試 | 後端開發 |
| Phase 4 | JavaFX 三視窗 + Polling | 前端開發 |
| Phase 5 | 併發測試 + 品質報告 | 測試 |

---

## 10. 附錄 (Appendix)

### 10.1 術語表
| 術語 | 定義 |
|-----|-----|
| Order | 一筆叫車請求 |
| Driver | 司機實體 |
| Polling | 定時輪詢同步機制 |
| Idempotency | 冪等性，重複操作結果一致 |

### 10.2 參考文件
*   req.md - 原始需求文件
*   JUnit 5 User Guide
*   JaCoCo Documentation
*   PMD Rule Reference
