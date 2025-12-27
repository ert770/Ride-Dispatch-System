# Phase 4B 工作計畫與指引

## 📋 概述

目前專案已完成 **Phase 4A (前端開發)**，接下來將進入 **Phase 4B (測試開發)**。
此階段的目標是確保系統的穩定性、正確性以及在高併發場景下的可靠性。

本報告旨在指導團隊成員完成 Phase 4B 的各項測試任務，並說明文件結構的調整。

---

## 📂 文件結構變更通知 (Classification)

為了讓文件管理更清晰，原 `docs/` 目錄下的規格與合約文件已分類移動至 `docs/specs/` 子目錄：

| 檔案類型 | 舊路徑 | **新路徑** | 說明 |
|---------|--------|------------|------|
| 狀態機合約 | `docs/state-machine.md` | **`docs/specs/state-machine.md`** |定義訂單與司機的狀態流轉 |
| API 規格 | `docs/api-spec.md` | **`docs/specs/api-spec.md`** | 定義前後端通訊介面 |
| 系統規格 | `docs/SYSTEM_SPEC.md` | **`docs/specs/SYSTEM_SPEC.md`** | 系統整體架構與規格 |

> ⚠️ **注意**: 請團隊成員在閱讀文件或撰寫文件連結時，更新至新路徑。`docs/DEVELOPMENT_ROADMAP.md` 中的連結已更新。

---

## ✅ Phase 4B 待辦任務清單

此階段重點在於**測試**，請依據以下分類進行開發。詳細依賴關係請參考 `docs/DEVELOPMENT_ROADMAP.md`。

### 1. 單元測試 (Unit Testing)
- **相關 Issue**: #8
- **優先級**: 🟡 P2
- **目標**: 驗證核心業務邏輯的最小單位正確性。
- **執行項目**:
  - **狀態機測試**: 針對 `OrderService` 與 `DriverService`，測試所有合法的狀態轉換 (e.g., `CREATED` -> `ACCEPTED`) 是否成功。
  - **異常路徑測試**: 測試非法的狀態轉換 (e.g., `CREATED` -> `FINISHED`) 是否拋出正確的 `IllegalAction` 異常。
  - **邊界條件**: 測試輸入參數的邊界值與 Null 處理。

### 2. 整合測試 (Integration Testing)
- **相關 Issue**: #9
- **優先級**: 🟢 P3
- **目標**: 驗證各模組 (Controller -> Service -> Repository) 間的協作與 API 合約。
- **執行項目**:
  - **Happy Path (完整流程)**:
    1. Passenger `createOrder`
    2. Driver `register` & `updateStatus` (Online)
    3. Driver `acceptOrder`
    4. Driver `startTrip`
    5. Driver `completeTrip`
  - 使用 `@SpringBootTest` 啟動完整 Context 進行測試。
  - 驗證 HTTP Status Code 與 Response Body 結構是否符合 `api-spec.md`。

### 3. 併發測試 - 搶單 (H2 Concurrency)
- **相關 Issue**: #10
- **優先級**: ⭐ 重點驗收
- **目標**: 驗證系統在高併發搶單時的資料一致性。
- **執行項目**:
  - **場景**: 建立一張訂單，模擬 10-50 個司機執行緒**同時**發送 `POST /orders/{id}/accept` 請求。
  - **驗收標準**:
    - 資料庫中該訂單的 `driver_id` 只能有一位。
    - 只有 1 個 HTTP 請求回傳 200 OK。
    - 其餘請求回傳 409 Conflict 或相關錯誤訊息 (Order Already Accepted)。
  - **工具建議**: 使用 `CountDownLatch` 同步啟動執行緒。

### 4. 併發測試 - 冪等性 (H4 Idempotency)
- **相關 Issue**: #11
- **優先級**: ⭐ 重點驗收
- **目標**: 驗證系統對重複請求的處理能力。
- **執行項目**:
  - **場景**: 針對同一操作 (如 `acceptOrder`, `cancelOrder`) 連續發送多次相同請求。
  - **驗收標準**:
    - **Accept Order**: 若同一司機重複送出，結果應一致 (成功)，但不應重複記錄 Log 或重複指派。
    - **Cancel Order**: 若重複送出取消，第一次成功，後續應回傳成功 (或是 "已取消" 的提示)，但不應觸發多次扣款或補償邏輯。
    - 資料庫狀態不可因重複請求而損壞。

---

## 🛠️ 技術指引

1. **測試框架**
   - 使用 **JUnit 5** 作為主要測試框架。
   - 使用 **Mockito** 隔離依賴 (特別是在單元測試中)。
   - 使用 **MockMvc** 進行 Controller 層的測試。

2. **資料庫環境**
   - 建議設定 `application-test.yml`，使用 **H2 Database (In-Memory)** 進行測試，以確保測試速度並避免污染開發資料庫。

3. **報告回報**
   - 測試完成後，請確保所有 Test Cases 通過。
   - 若發現 Bug，請建立新的 Issue 並標記 `bug` 標籤，指派給相關開發者。

---

## 📅 下一步 (Future)

Phase 4B 完成後，我們將進入 **Phase 4C** (CI/CD 與程式碼品質檢查) 及 **Phase 4D** (系統驗收文件)。
請團隊成員全力完成上述測試任務，確保我們的 Ride-Dispatch-System 堅若磐石！
