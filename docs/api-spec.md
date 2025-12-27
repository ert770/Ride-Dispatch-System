# API 規格文件 (API Specification)

> **版本**: v0.1  
> **最後更新**: 2025-12-25  
> **Base URL**: `http://localhost:8080/api`

---

## 1. 通用規範

### 1.1 回應格式

所有 API 回應統一使用以下 JSON 格式：

**成功回應 (Success)**
```json
{
  "success": true,
  "data": { ... },
  "timestamp": "2025-12-25T10:30:00Z"
}
```

**錯誤回應 (Error)**
```json
{
  "success": false,
  "error": {
    "code": "ERROR_CODE",
    "message": "人類可讀的錯誤訊息"
  },
  "timestamp": "2025-12-25T10:30:00Z"
}
```

### 1.2 錯誤碼規則

| HTTP Code | 使用情境 | 錯誤碼範例 |
|-----------|---------|-----------|
| `400` | 請求格式錯誤、非法狀態轉換 | `INVALID_REQUEST`, `INVALID_STATE` |
| `403` | 無權限執行此操作 | `NOT_ASSIGNED_DRIVER` |
| `404` | 資源不存在 | `ORDER_NOT_FOUND`, `DRIVER_NOT_FOUND` |
| `409` | 併發衝突（搶單失敗） | `ORDER_ALREADY_ACCEPTED` |
| `500` | 伺服器內部錯誤 | `INTERNAL_ERROR` |

### 1.3 409 Conflict 觸發條件

```
409 Conflict 發生於:
  1. 多位司機同時對同一筆 PENDING 訂單執行 accept
  2. 第一位成功的司機獲得訂單
  3. 其餘司機收到 409 回應

觸發流程:
  Driver A ─────┐
  Driver B ─────┼──► accept(order-123) ──► Server
  Driver C ─────┘
                                            │
                     ┌──────────────────────┼──────────────────────┐
                     │                      │                      │
                     ▼                      ▼                      ▼
              200 OK (Success)       409 Conflict           409 Conflict
              Driver A 接單成功       Driver B 失敗          Driver C 失敗
```

---

## 2. Passenger API (乘客端)

### 2.1 建立叫車請求

```http
POST /api/orders
Content-Type: application/json
```

**Request Body**
```json
{
  "passengerId": "passenger-001",
  "pickupLocation": {
    "x": 25.5,
    "y": 30.2
  },
  "dropoffLocation": {
    "x": 45.8,
    "y": 60.1
  },
  "vehicleType": "STANDARD"
}
```

**Success Response (201 Created)**
```json
{
  "success": true,
  "data": {
    "orderId": "order-uuid-123",
    "passengerId": "passenger-001",
    "status": "PENDING",
    "pickupLocation": { "x": 25.5, "y": 30.2 },
    "dropoffLocation": { "x": 45.8, "y": 60.1 },
    "vehicleType": "STANDARD",
    "estimatedFare": 150.00,
    "estimatedDistance": 8.5,
    "createdAt": "2025-12-25T10:30:00Z"
  },
  "timestamp": "2025-12-25T10:30:00Z"
}
```

**Error Response (400 Bad Request)**
```json
{
  "success": false,
  "error": {
    "code": "INVALID_REQUEST",
    "message": "上車地點與下車地點不可相同"
  },
  "timestamp": "2025-12-25T10:30:00Z"
}
```

### 2.2 查詢訂單狀態

```http
GET /api/orders/{orderId}
```

**Success Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "orderId": "order-uuid-123",
    "passengerId": "passenger-001",
    "status": "ACCEPTED",
    "driverId": "driver-456",
    "driverName": "王大明",
    "driverPhone": "0912-345-678",
    "vehicleType": "STANDARD",
    "vehiclePlate": "ABC-1234",
    "pickupLocation": { "x": 25.5, "y": 30.2 },
    "dropoffLocation": { "x": 45.8, "y": 60.1 },
    "estimatedFare": 150.00,
    "createdAt": "2025-12-25T10:30:00Z",
    "acceptedAt": "2025-12-25T10:32:00Z"
  },
  "timestamp": "2025-12-25T10:32:30Z"
}
```

### 2.3 取消訂單

```http
PUT /api/orders/{orderId}/cancel
Content-Type: application/json
```

**Request Body**
```json
{
  "cancelledBy": "passenger-001",
  "reason": "等待時間過長"
}
```

**Success Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "orderId": "order-uuid-123",
    "status": "CANCELLED",
    "cancelledAt": "2025-12-25T10:35:00Z",
    "cancelledBy": "passenger-001",
    "cancelFee": 0
  },
  "timestamp": "2025-12-25T10:35:00Z"
}
```

---

## 3. Driver API (司機端)

### 3.1 司機上線

```http
PUT /api/drivers/{driverId}/online
Content-Type: application/json
```

**Request Body**
```json
{
  "location": {
    "x": 20.0,
    "y": 25.0
  }
}
```

**Success Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "driverId": "driver-456",
    "status": "ONLINE",
    "location": { "x": 20.0, "y": 25.0 },
    "busy": false,
    "updatedAt": "2025-12-25T10:30:00Z"
  },
  "timestamp": "2025-12-25T10:30:00Z"
}
```

### 3.2 司機下線

```http
PUT /api/drivers/{driverId}/offline
```

**Success Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "driverId": "driver-456",
    "status": "OFFLINE",
    "updatedAt": "2025-12-25T18:00:00Z"
  },
  "timestamp": "2025-12-25T18:00:00Z"
}
```

### 3.3 更新司機位置

```http
PUT /api/drivers/{driverId}/location
Content-Type: application/json
```

**Request Body**
```json
{
  "x": 22.5,
  "y": 28.3
}
```

**Success Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "driverId": "driver-456",
    "location": { "x": 22.5, "y": 28.3 },
    "updatedAt": "2025-12-25T10:31:00Z"
  },
  "timestamp": "2025-12-25T10:31:00Z"
}
```

### 3.4 取得可接訂單列表

```http
GET /api/drivers/{driverId}/offers
```

**Success Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "offers": [
      {
        "orderId": "order-123",
        "pickupLocation": { "x": 25.5, "y": 30.2 },
        "dropoffLocation": { "x": 45.8, "y": 60.1 },
        "vehicleType": "STANDARD",
        "distance": 5.2,
        "estimatedFare": 150.00,
        "createdAt": "2025-12-25T10:30:00Z"
      },
      {
        "orderId": "order-456",
        "pickupLocation": { "x": 28.0, "y": 32.5 },
        "dropoffLocation": { "x": 50.0, "y": 55.0 },
        "vehicleType": "STANDARD",
        "distance": 7.8,
        "estimatedFare": 180.00,
        "createdAt": "2025-12-25T10:28:00Z"
      }
    ],
    "count": 2
  },
  "timestamp": "2025-12-25T10:31:00Z"
}
```

### 3.5 接受訂單 ⚠️ 重點 API

```http
PUT /api/orders/{orderId}/accept
Content-Type: application/json
```

**Request Body**
```json
{
  "driverId": "driver-456"
}
```

**Success Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "orderId": "order-123",
    "status": "ACCEPTED",
    "driverId": "driver-456",
    "acceptedAt": "2025-12-25T10:32:00Z",
    "pickupLocation": { "x": 25.5, "y": 30.2 },
    "dropoffLocation": { "x": 45.8, "y": 60.1 }
  },
  "timestamp": "2025-12-25T10:32:00Z"
}
```

**Error Response (409 Conflict) - 搶單失敗**
```json
{
  "success": false,
  "error": {
    "code": "ORDER_ALREADY_ACCEPTED",
    "message": "此訂單已被其他司機接受"
  },
  "timestamp": "2025-12-25T10:32:00Z"
}
```

**Error Response (400 Bad Request) - 非法狀態**
```json
{
  "success": false,
  "error": {
    "code": "INVALID_STATE",
    "message": "訂單狀態不允許接單操作"
  },
  "timestamp": "2025-12-25T10:32:00Z"
}
```

### 3.6 開始行程

```http
PUT /api/orders/{orderId}/start
Content-Type: application/json
```

**Request Body**
```json
{
  "driverId": "driver-456"
}
```

**Success Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "orderId": "order-123",
    "status": "ONGOING",
    "startedAt": "2025-12-25T10:40:00Z"
  },
  "timestamp": "2025-12-25T10:40:00Z"
}
```

### 3.7 完成行程

```http
PUT /api/orders/{orderId}/complete
Content-Type: application/json
```

**Request Body**
```json
{
  "driverId": "driver-456"
}
```

**Success Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "orderId": "order-123",
    "status": "COMPLETED",
    "completedAt": "2025-12-25T10:55:00Z",
    "fare": 185.50,
    "distance": 8.5,
    "duration": 15,
    "fareBreakdown": {
      "baseFare": 50.00,
      "distanceFare": 127.50,
      "timeFare": 45.00,
      "discount": 37.00,
      "total": 185.50
    }
  },
  "timestamp": "2025-12-25T10:55:00Z"
}
```

---

## 4. Admin API (管理端)

### 4.1 取得所有訂單

```http
GET /api/admin/orders?status={status}&page={page}&size={size}
```

**Query Parameters**
| 參數 | 類型 | 必填 | 說明 |
|-----|-----|-----|------|
| status | string | ❌ | 篩選狀態 (PENDING/ACCEPTED/ONGOING/COMPLETED/CANCELLED) |
| page | int | ❌ | 頁碼，預設 0 |
| size | int | ❌ | 每頁筆數，預設 20 |

**Success Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "orders": [
      {
        "orderId": "order-123",
        "passengerId": "passenger-001",
        "driverId": "driver-456",
        "status": "COMPLETED",
        "fare": 185.50,
        "createdAt": "2025-12-25T10:30:00Z",
        "completedAt": "2025-12-25T10:55:00Z"
      }
    ],
    "pagination": {
      "page": 0,
      "size": 20,
      "totalElements": 1,
      "totalPages": 1
    }
  },
  "timestamp": "2025-12-25T11:00:00Z"
}
```

### 4.2 取得 Audit Log

```http
GET /api/admin/audit-logs?orderId={orderId}&action={action}
```

**Success Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "logs": [
      {
        "id": "audit-001",
        "timestamp": "2025-12-25T10:30:00Z",
        "orderId": "order-123",
        "action": "CREATE",
        "actorType": "PASSENGER",
        "actorId": "passenger-001",
        "previousState": null,
        "newState": "PENDING",
        "success": true,
        "failureReason": null
      },
      {
        "id": "audit-002",
        "timestamp": "2025-12-25T10:32:00Z",
        "orderId": "order-123",
        "action": "ACCEPT",
        "actorType": "DRIVER",
        "actorId": "driver-456",
        "previousState": "PENDING",
        "newState": "ACCEPTED",
        "success": true,
        "failureReason": null
      },
      {
        "id": "audit-003",
        "timestamp": "2025-12-25T10:32:00Z",
        "orderId": "order-123",
        "action": "ACCEPT",
        "actorType": "DRIVER",
        "actorId": "driver-789",
        "previousState": "PENDING",
        "newState": "PENDING",
        "success": false,
        "failureReason": "ORDER_ALREADY_ACCEPTED"
      }
    ],
    "count": 3
  },
  "timestamp": "2025-12-25T11:00:00Z"
}
```

### 4.3 取得費率設定

```http
GET /api/admin/rate-plans
```

**Success Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "ratePlans": [
      {
        "vehicleType": "STANDARD",
        "baseFare": 50.00,
        "perKmRate": 15.00,
        "perMinRate": 3.00,
        "minFare": 70.00
      },
      {
        "vehicleType": "PREMIUM",
        "baseFare": 80.00,
        "perKmRate": 25.00,
        "perMinRate": 5.00,
        "minFare": 120.00
      },
      {
        "vehicleType": "XL",
        "baseFare": 100.00,
        "perKmRate": 30.00,
        "perMinRate": 6.00,
        "minFare": 150.00
      }
    ]
  },
  "timestamp": "2025-12-25T11:00:00Z"
}
```

### 4.4 更新費率設定

```http
PUT /api/admin/rate-plans/{vehicleType}
Content-Type: application/json
```

**Request Body**
```json
{
  "baseFare": 55.00,
  "perKmRate": 16.00,
  "perMinRate": 3.50,
  "minFare": 75.00
}
```

**Success Response (200 OK)**
```json
{
  "success": true,
  "data": {
    "vehicleType": "STANDARD",
    "baseFare": 55.00,
    "perKmRate": 16.00,
    "perMinRate": 3.50,
    "minFare": 75.00,
    "updatedAt": "2025-12-25T11:05:00Z"
  },
  "timestamp": "2025-12-25T11:05:00Z"
}
```

---

## 5. API 端點總覽

### 5.1 Passenger 端點
| Method | Endpoint | 說明 |
|--------|----------|-----|
| POST | `/api/orders` | 建立叫車請求 |
| GET | `/api/orders/{orderId}` | 查詢訂單狀態 |
| PUT | `/api/orders/{orderId}/cancel` | 取消訂單 |

### 5.2 Driver 端點
| Method | Endpoint | 說明 |
|--------|----------|-----|
| PUT | `/api/drivers/{driverId}/online` | 司機上線 |
| PUT | `/api/drivers/{driverId}/offline` | 司機下線 |
| PUT | `/api/drivers/{driverId}/location` | 更新位置 |
| GET | `/api/drivers/{driverId}/offers` | 取得可接訂單 |
| PUT | `/api/orders/{orderId}/accept` | 接受訂單 |
| PUT | `/api/orders/{orderId}/start` | 開始行程 |
| PUT | `/api/orders/{orderId}/complete` | 完成行程 |

### 5.3 Admin 端點
| Method | Endpoint | 說明 |
|--------|----------|-----|
| GET | `/api/admin/orders` | 取得所有訂單 |
| GET | `/api/admin/audit-logs` | 取得 Audit Log |
| GET | `/api/admin/rate-plans` | 取得費率設定 |
| PUT | `/api/admin/rate-plans/{vehicleType}` | 更新費率設定 |

---

## 6. 測試用 Postman Collection

```json
{
  "info": {
    "name": "Ride-Dispatch-System API",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "variable": [
    { "key": "baseUrl", "value": "http://localhost:8080/api" }
  ],
  "item": [
    {
      "name": "1. Create Order",
      "request": {
        "method": "POST",
        "url": "{{baseUrl}}/orders",
        "body": {
          "mode": "raw",
          "raw": "{\"passengerId\":\"p1\",\"pickupLocation\":{\"x\":10,\"y\":20},\"dropoffLocation\":{\"x\":50,\"y\":60},\"vehicleType\":\"STANDARD\"}"
        }
      }
    },
    {
      "name": "2. Driver Online",
      "request": {
        "method": "PUT",
        "url": "{{baseUrl}}/drivers/d1/online",
        "body": {
          "mode": "raw",
          "raw": "{\"location\":{\"x\":15,\"y\":25}}"
        }
      }
    },
    {
      "name": "3. Get Offers",
      "request": {
        "method": "GET",
        "url": "{{baseUrl}}/drivers/d1/offers"
      }
    },
    {
      "name": "4. Accept Order",
      "request": {
        "method": "PUT",
        "url": "{{baseUrl}}/orders/{{orderId}}/accept",
        "body": {
          "mode": "raw",
          "raw": "{\"driverId\":\"d1\"}"
        }
      }
    }
  ]
}
```
