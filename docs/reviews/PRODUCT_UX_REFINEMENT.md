# HiMarket CAS 对接产品体验优化路线图

## 1. 核心宗旨：把复杂留给代码，把简单留给用户
基于 `cas-auth-support` 分支的联调成果，下一步演进目标是从“工程实现”转向“商业化产品体验”。

## 2. 重点优化项

### Phase 1: 认知减负 (Cognitive Load Reduction)
*   **极简表单模式**：将 50+ 参数归类。默认仅展示 `Server URL`、`Provider ID` 和 `Display Name`。
*   **高级设置折叠**：将所有协议细节（如 Ticket 验证算法、代理回调、MFA）默认隐藏，仅在“高级模式”下展开。

### Phase 2: 自动化推导 (Zero-Config)
*   **智能路径补全**：管理员填入 `https://sso.example.com/cas` 后，系统自动填充：
    *   登录地址 -> `/login`
    *   登出地址 -> `/logout`
    *   校验地址 -> `/p3/serviceValidate`
*   **厂商模板库**：内置 Apereo CAS, Jasig CAS, Keycloak 等预设配置模板。

### Phase 3: 确定性反馈 (Confidence Builder)
*   **“测试连接”功能**：在配置页面增加探测按钮。
    *   后端主动发起对 CAS Server 的健康检查。
    *   展示“人话”版本的错误提示，而非原始堆栈。

### Phase 4: 属性自动对齐 (Auto-Mapping)
*   **属性自动扫描**：在测试连接成功后，自动抓取 CAS 返回的 Sample Attributes，供用户通过下拉框选择映射，无需手工输入 Key。

## 3. 执行标准 (CSE Audit)
*   **原则**：凡是能通过 Server URL 自动生成的，绝不让用户手工填。
*   **原则**：凡是涉及底层协议参数的，默认必须有稳健的缺省值。

---
*记录时间：2026-03-19*
*发起人：Gemini CLI (Product UX Advocate)*
