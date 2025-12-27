# 訂單狀態機合約 (Order State Machine Contract)

> **版本**: v0.1  
> **最後更新**: 2025-12-25  
> **狀態**: 已定案，組員可依此撰寫測試

---

## 1. 狀態列表 (State List)

| 狀態 | 英文名稱 | 說明 | 是否終態 |
|-----|---------|------|---------|
| 🟡 等待中 | `PENDING` | 訂單已建立，等待司機接單 | ❌ |
| 🔵 已接單 | `ACCEPTED` | 司機已接受訂單 | ❌ |
| 🟢 行程中 | `ONGOING` | 行程進行中 | ❌ |
| ✅ 已完成 | `COMPLETED` | 行程已完成並計費 | ✔️ |
| ❌ 已取消 | `CANCELLED` | 訂單已取消 | ✔️ |

---

## 2. 狀態轉換表 (State Transition Table)

### 2.1 合法轉換

| 當前狀態 | 動作 (Action) | 目標狀態 | 觸發者 |
|---------|--------------|---------|-------|
| `PENDING` | `accept` | `ACCEPTED` | Driver |
| `PENDING` | `cancel` | `CANCELLED` | Passenger |
| `ACCEPTED` | `start` | `ONGOING` | Driver |
| `ACCEPTED` | `cancel` | `CANCELLED` | Passenger/Driver |
| `ONGOING` | `complete` | `COMPLETED` | Driver |

### 2.2 狀態轉換圖

```
                         cancel()
              ┌──────────────────────────────────┐
              │                                  │
              ▼                                  │
        ┌───────────┐     accept()      ┌───────┴─────┐
 create │  PENDING  │──────────────────▶│  ACCEPTED   │
 ──────▶│   (P)     │                   │    (A)      │
        └─────┬─────┘                   └──────┬──────┘
              │                                │
              │ cancel()                       │ start()
              │                                │
              ▼                                ▼
        ┌───────────┐                   ┌─────────────┐
        │ CANCELLED │◀──────────────────│   ONGOING   │
        │   (X)     │     cancel()      │    (O)      │
        └───────────┘                   └──────┬──────┘
                                               │
                                               │ complete()
                                               ▼
                                        ┌─────────────┐
                                        │  COMPLETED  │
                                        │    (C)      │
                                        └─────────────┘
```

### 2.3 非法轉換（必須拒絕）

以下轉換嘗試將回傳 `400 INVALID_STATE`：

| 當前狀態 | 禁止動作 | 原因 |
|---------|---------|-----|
| `PENDING` | `start`, `complete` | 尚未有司機接單 |
| `ACCEPTED` | `accept`, `complete` | 不可重複接單或跳過行程 |
| `ONGOING` | `accept`, `start`, `cancel` | 行程中不可變更 |
| `COMPLETED` | 任何動作 | 終態不可變更 |
| `CANCELLED` | 任何動作 | 終態不可變更 |

---

## 3. 各動作規格 (Action Specifications)

### 3.1 `accept` - 接受訂單

```yaml
Action: accept
Endpoint: PUT /api/orders/{orderId}/accept
Trigger: Driver

前置條件 (Preconditions):
  - 訂單狀態必須為 PENDING
  - 司機狀態必須為 ONLINE
  - 司機不可為 BUSY (無進行中訂單)
  - 司機車種必須符合訂單需求

成功回應 (Success):
  HTTP: 200 OK
  Body:
    success: true
    data:
      orderId: "order-123"
      status: "ACCEPTED"
      driverId: "driver-456"
      acceptedAt: "2025-12-25T10:30:00Z"

失敗回應 (Failure):
  # 併發衝突 - 其他司機已接單
  HTTP: 409 Conflict
  Body:
    success: false
    error:
      code: "ORDER_ALREADY_ACCEPTED"
      message: "此訂單已被其他司機接受"

  # 非法狀態
  HTTP: 400 Bad Request
  Body:
    success: false
    error:
      code: "INVALID_STATE"
      message: "訂單狀態不允許此操作"

  # 訂單不存在
  HTTP: 404 Not Found
  Body:
    success: false
    error:
      code: "ORDER_NOT_FOUND"
      message: "訂單不存在"
```

### 3.2 `start` - 開始行程

```yaml
Action: start
Endpoint: PUT /api/orders/{orderId}/start
Trigger: Driver

前置條件 (Preconditions):
  - 訂單狀態必須為 ACCEPTED
  - 操作者必須為該訂單的指派司機

成功回應 (Success):
  HTTP: 200 OK
  Body:
    success: true
    data:
      orderId: "order-123"
      status: "ONGOING"
      startedAt: "2025-12-25T10:35:00Z"

失敗回應 (Failure):
  # 非指派司機
  HTTP: 403 Forbidden
  Body:
    success: false
    error:
      code: "NOT_ASSIGNED_DRIVER"
      message: "您不是此訂單的指派司機"

  # 非法狀態
  HTTP: 400 Bad Request
  Body:
    success: false
    error:
      code: "INVALID_STATE"
      message: "訂單狀態不允許此操作"
```

### 3.3 `complete` - 完成行程

```yaml
Action: complete
Endpoint: PUT /api/orders/{orderId}/complete
Trigger: Driver

前置條件 (Preconditions):
  - 訂單狀態必須為 ONGOING
  - 操作者必須為該訂單的指派司機

成功回應 (Success):
  HTTP: 200 OK
  Body:
    success: true
    data:
      orderId: "order-123"
      status: "COMPLETED"
      completedAt: "2025-12-25T10:50:00Z"
      fare: 185.50
      distance: 8.5
      duration: 15

失敗回應 (Failure):
  # 非法狀態
  HTTP: 400 Bad Request
  Body:
    success: false
    error:
      code: "INVALID_STATE"
      message: "訂單狀態不允許此操作"
```

### 3.4 `cancel` - 取消訂單

```yaml
Action: cancel
Endpoint: PUT /api/orders/{orderId}/cancel
Trigger: Passenger / Driver

前置條件 (Preconditions):
  - 訂單狀態必須為 PENDING 或 ACCEPTED
  - ONGOING 狀態不可取消

成功回應 (Success):
  HTTP: 200 OK
  Body:
    success: true
    data:
      orderId: "order-123"
      status: "CANCELLED"
      cancelledAt: "2025-12-25T10:32:00Z"
      cancelledBy: "passenger-789"
      cancelFee: 0  # PENDING 取消無費用
      # cancelFee: 50  # ACCEPTED 取消有費用

失敗回應 (Failure):
  # 非法狀態 (ONGOING/COMPLETED/CANCELLED)
  HTTP: 400 Bad Request
  Body:
    success: false
    error:
      code: "INVALID_STATE"
      message: "行程中無法取消訂單"
```

---

## 4. 併發規則 (Concurrency Rules)

### 4.1 H2 規則：搶單 (Race Condition)

> **場景**：多位司機同時對同一筆 `PENDING` 訂單發送 `accept` 請求

```
規則:
  - 僅有 1 位司機成功 (HTTP 200)
  - 其餘司機失敗 (HTTP 409 Conflict)
  - 最終訂單只能有 1 個 driverId
  - 所有嘗試都必須記錄在 Audit Log

實作方式:
  - 使用 synchronized 或 ReentrantLock 保護狀態轉換
  - 採用 Compare-And-Swap (CAS) 模式

測試驗證:
  CT-H2-01: 10 位司機同時 accept 同一訂單
    預期: 成功=1, 失敗=9
    驗證: 
      - 最終狀態為 ACCEPTED
      - driverId 唯一且不變
      - Audit Log 記錄 1 成功 + 9 失敗
```

### 4.2 H4 規則：冪等性 (Idempotency)

> **場景**：同一請求因網路問題被重複發送

```
規則:
  - 重複請求不產生副作用
  - 不會覆寫已設定的關鍵資料
  - 回傳一致的成功訊息

適用動作: accept, start, complete

冪等行為表:
  | 動作     | 重複請求行為                          | 回應           |
  |---------|--------------------------------------|---------------|
  | accept  | 若同一司機已接此單，回傳成功          | 200 + 原資料   |
  | accept  | 若其他司機已接此單，回傳衝突          | 409 Conflict  |
  | start   | 若已為 ONGOING，回傳成功             | 200 + 原資料   |
  | complete| 若已為 COMPLETED，回傳成功           | 200 + 原資料   |

不可覆寫欄位:
  - driverId (一旦設定不可變更)
  - acceptedAt (接單時間)
  - startedAt (開始時間)
  - completedAt (完成時間)
  - fare (車資)

測試驗證:
  CT-H4-01: 同一司機重複 accept
    預期: 全部回傳 200，driverId 不變
  
  CT-H4-02: 重複 start
    預期: 全部回傳 200，startedAt 不變
  
  CT-H4-03: 重複 complete
    預期: 全部回傳 200，fare 不變
```

---

## 5. Audit Log 規格

### 5.1 Log 欄位

```json
{
  "id": "audit-001",
  "timestamp": "2025-12-25T10:30:00.123Z",
  "orderId": "order-123",
  "action": "ACCEPT",
  "actorType": "DRIVER",
  "actorId": "driver-456",
  "previousState": "PENDING",
  "newState": "ACCEPTED",
  "success": true,
  "failureReason": null,
  "metadata": {
    "clientIp": "192.168.1.100",
    "requestId": "req-abc-123"
  }
}
```

### 5.2 失敗原因代碼

| 代碼 | 說明 |
|-----|-----|
| `ORDER_ALREADY_ACCEPTED` | 訂單已被其他司機接受 (H2 衝突) |
| `INVALID_STATE` | 非法狀態轉換 |
| `NOT_ASSIGNED_DRIVER` | 非指派司機嘗試操作 |
| `DRIVER_BUSY` | 司機正在忙碌 |
| `DRIVER_OFFLINE` | 司機不在線 |

---

## 6. 測試案例對照表

| 測試 ID | 類型 | 測試內容 | 對應規則 |
|--------|-----|---------|---------|
| UT-SM-01 | Unit | PENDING → ACCEPTED 合法 | 2.1 |
| UT-SM-02 | Unit | PENDING → ONGOING 非法 | 2.3 |
| UT-SM-03 | Unit | COMPLETED 不可變更 | 2.3 |
| IT-FLOW-01 | Integration | 完整 Happy Path | 2.1 全流程 |
| CT-H2-01 | Concurrency | 10 司機搶單 | 4.1 |
| CT-H4-01 | Concurrency | accept 冪等性 | 4.2 |
| CT-H4-02 | Concurrency | start 冪等性 | 4.2 |
| CT-H4-03 | Concurrency | complete 冪等性 | 4.2 |
