# 🚗 Ride-Dispatch Clients

JavaFX 客戶端應用程式，包含乘客端、司機端和管理後台。

## 📦 模組結構

```
clients/
├── shared/          # 共享元件（模型、API 客戶端、工具類）
├── passenger-app/   # 🚕 乘客端應用程式
├── driver-app/      # 🚗 司機端應用程式
└── admin-app/       # 📊 管理後台應用程式
```

## 🛠️ 環境需求

- **JDK 17+** (建議使用 Eclipse Temurin 或 Oracle JDK)
- **Maven 3.9+**
- **伺服器端運行中** (`http://localhost:8080`)

## 🚀 快速開始

### 1. 先啟動後端伺服器

```bash
cd ../server
mvn spring-boot:run
```

### 2. 編譯客戶端

```bash
cd clients
mvn clean install
```

### 3. 運行應用程式

#### 🚕 乘客端 (Passenger App)
```bash
cd passenger-app
mvn javafx:run
```

**功能：**
- 建立叫車請求（選擇上下車座標、車種）
- 查看預估車資
- 即時追蹤訂單狀態
- 取消訂單

#### 🚗 司機端 (Driver App)
```bash
cd driver-app
mvn javafx:run
```

**功能：**
- 司機註冊/登入
- 上線/下線切換
- 查看可接訂單列表
- 接單（搶單模式）
- 開始行程/完成行程
- 取消訂單

#### 📊 管理後台 (Admin Console)
```bash
cd admin-app
mvn javafx:run
```

**功能：**
- 訂單監控與篩選
- 司機狀態管理
- 審計日誌檢視
- 費率設定（預覽）

## 🎮 Demo 流程

### 完整叫車流程演示

1. **啟動後端伺服器**
   ```bash
   cd server && mvn spring-boot:run
   ```

2. **開啟管理後台**（觀察即時狀態）
   ```bash
   cd clients/admin-app && mvn javafx:run
   ```

3. **開啟司機端並上線**
   ```bash
   cd clients/driver-app && mvn javafx:run
   ```
   - 輸入司機資訊
   - 點擊「開始接單」

4. **開啟乘客端並叫車**
   ```bash
   cd clients/passenger-app && mvn javafx:run
   ```
   - 設定上下車座標
   - 選擇車種
   - 點擊「立即叫車」

5. **司機接單並完成行程**
   - 在司機端看到新訂單
   - 點擊「接單」
   - 點擊「開始行程」
   - 點擊「完成行程」

6. **觀察管理後台**
   - 訂單狀態即時更新
   - 審計日誌記錄所有操作

### 搶單演示（併發控制）

1. 開啟 **多個司機端視窗**（使用不同 ID）
2. 所有司機上線
3. 乘客建立一筆訂單
4. 多個司機同時點擊「接單」
5. 觀察結果：只有一位司機成功，其他收到錯誤提示

## 📐 技術架構

```
┌─────────────────────────────────────────────────────────────┐
│                     Client Layer (JavaFX)                    │
├──────────────┬──────────────────┬───────────────────────────┤
│ Passenger App│   Driver App     │      Admin Console        │
└──────┬───────┴────────┬─────────┴─────────────┬─────────────┘
       │                │                       │
       │         HTTP/REST (Polling)            │
       │                │                       │
┌──────▼────────────────▼───────────────────────▼─────────────┐
│                   Server (Spring Boot)                       │
└─────────────────────────────────────────────────────────────┘
```

### 通訊機制
- **協定**: HTTP/REST
- **資料格式**: JSON
- **同步機制**: Polling（每 1 秒輪詢）

## 🎨 UI 設計

- **深色主題** - 護眼設計
- **卡片式佈局** - 現代化介面
- **即時狀態更新** - 輪詢同步
- **漸層按鈕** - 視覺層次
- **狀態顏色標記**
  - 🟠 PENDING（等待中）
  - 🔵 ACCEPTED（已接單）
  - 🟢 ONGOING（進行中）
  - ⚫ COMPLETED（已完成）
  - 🔴 CANCELLED（已取消）

## 📁 核心類別

### Shared Module
| 類別 | 說明 |
|-----|------|
| `ApiClient` | REST API 客戶端，封裝所有 HTTP 請求 |
| `ApiResponse` | 統一回應格式 |
| `Order` | 訂單資料模型 |
| `Driver` | 司機資料模型 |
| `Theme` | UI 主題常數和樣式 |
| `UIUtils` | UI 工具類（對話框、格式化） |

### Passenger App
| 類別 | 說明 |
|-----|------|
| `PassengerApp` | 應用程式入口 |
| `MainController` | 主控制器（叫車、追蹤） |

### Driver App
| 類別 | 說明 |
|-----|------|
| `DriverApp` | 應用程式入口 |
| `MainController` | 主控制器（登入、接單、行程） |

### Admin Console
| 類別 | 說明 |
|-----|------|
| `AdminApp` | 應用程式入口 |
| `MainController` | 主控制器（監控、日誌） |

## ⚠️ 注意事項

1. **伺服器必須運行中** - 客戶端需要連接 `http://localhost:8080`
2. **JavaFX 模組系統** - 使用 Maven JavaFX Plugin 自動處理
3. **輪詢間隔** - 預設 1-2 秒，可在程式碼中調整

## 🔧 常見問題

### Q: 編譯時出現 JavaFX 相關錯誤？
A: 確保使用 JDK 17+，並使用 `mvn javafx:run` 運行

### Q: 無法連接伺服器？
A: 確認後端伺服器已啟動在 `http://localhost:8080`

### Q: 畫面顯示亂碼？
A: 確保系統已安裝中文字型（Microsoft JhengHei）

---

**版本**: v1.0  
**最後更新**: 2025-12-26
