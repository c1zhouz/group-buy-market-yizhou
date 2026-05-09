const moduleMeta = {
  products: { title: "商品管理", hint: "管理商品上下架、库存与价格" },
  orders: { title: "订单管理", hint: "查看订单状态与支付进度" },
  marketing: { title: "营销管理", hint: "管理拼团活动与优惠策略" },
  users: { title: "用户管理", hint: "查看用户下单行为与订单明细" }
};

const adminState = {
  currentModule: "products",
  orderFilter: "all",
  orderStartDate: "",
  orderEndDate: "",
  orderPage: 1,
  orderPageSize: 10,
  orderFilteredTotal: 0,
  products: [],
  productSummary: { total: 0, online: 0, lowStock: 0, avgPrice: 0 },
  orders: [],
  orderSummary: { total: 0, waiting: 0, paid: 0, canceled: 0 },
  marketing: [],
  marketingDiscountTypes: [],
  marketingDiscountOptions: [],
  marketingSummary: { total: 0, active: 0, pending: 0, closed: 0 },
  users: [],
  userKeyword: "",
  userPage: 1,
  userPageSize: 10,
  userTotal: 0,
  orderSelectedKeys: new Set()
};

function resolveApiOrigin() {
  const host = window.location.hostname;
  const isLocalHost = host === "127.0.0.1" || host === "localhost";
  return isLocalHost ? "http://127.0.0.1:8091" : window.location.origin;
}

const API_BASE = `${resolveApiOrigin()}/api/v1/gbm/admin`;
let globalErrorTimer = null;
let lastErrorMessage = "";
let lastErrorTime = 0;

document.addEventListener("DOMContentLoaded", () => {
  const username = getCookie("username");
  if (!username) {
    window.location.href = "login.html?redirect=admin.html";
    return;
  }

  document.getElementById("currentUser").textContent = `当前用户：${username}`;
  bindUserSessionUI();
  bindGlobalErrorBarEvents();

  const nav = document.getElementById("adminNav");
  const actionBar = document.getElementById("moduleActionBar");
  const content = document.getElementById("moduleContent");

  nav.addEventListener("click", async (e) => {
    const btn = e.target.closest(".nav-item");
    if (!btn) return;

    nav.querySelectorAll(".nav-item").forEach((it) => it.classList.remove("active"));
    btn.classList.add("active");

    adminState.currentModule = btn.dataset.module;
    if (adminState.currentModule !== "orders") {
      adminState.orderFilter = "all";
    }
    await safeRenderModule(adminState.currentModule);
  });

  content.addEventListener("click", async (e) => {
    if (e.target.matches("[data-action='edit-product']")) {
      const index = Number(e.target.dataset.index);
      await openProductModal("编辑商品", adminState.products[index], index);
      return;
    }

    if (e.target.matches("[data-action='delete-product']")) {
      const index = Number(e.target.dataset.index);
      const item = adminState.products[index];
      if (!item) return;
      if (!confirm(`确认删除商品 ${item.name} (${item.sku}) 吗？`)) return;
      try {
        await apiDelete(`${API_BASE}/products/${encodeURIComponent(item.sku)}`);
        await safeRenderModule("products");
      } catch (error) {
        showGlobalError(error.message || "删除商品失败");
      }
      return;
    }

    if (e.target.matches("[data-action='transition-activity']")) {
      const idx = Number(e.target.dataset.index);
      const item = adminState.marketing[idx];
      if (!item || item.activityId === undefined || item.activityId === null) return;

      const toStatus = Number(e.target.dataset.toStatus);
      try {
        await apiPut(`${API_BASE}/marketing/activities/${encodeURIComponent(item.activityId)}/status`, {
          fromStatus: item.statusCode,
          toStatus
        });
        await safeRenderModule("marketing");
      } catch (error) {
        showGlobalError(error.message || "更新活动状态失败");
      }
      return;
    }

    if (e.target.matches("[data-action='delete-activity']")) {
      const idx = Number(e.target.dataset.index);
      const item = adminState.marketing[idx];
      if (!item || item.activityId === undefined || item.activityId === null) return;
      if (Number(item.statusCode) === 1) {
        showGlobalError("活动进行中，请先结束后再删除");
        return;
      }
      if (!confirm(`确认删除活动 ${item.name} (${item.activityId}) 吗？`)) return;

      try {
        await apiDelete(`${API_BASE}/marketing/activities/${encodeURIComponent(item.activityId)}`);
        await safeRenderModule("marketing");
      } catch (error) {
        showGlobalError(error.message || "删除活动失败");
      }
      return;
    }

    if (e.target.matches("[data-action='delete-order']")) {
      const index = Number(e.target.dataset.index);
      const item = adminState.orders[index];
      if (!item) return;
      if (!isCancelableOrder(item)) {
        showGlobalError("只能取消待支付订单，请先筛选待支付订单后操作");
        return;
      }
      if (!confirm(`确认取消待支付订单 ${item.no} 吗？`)) return;
      try {
        await apiDelete(`${API_BASE}/orders/${encodeURIComponent(item.no)}?userId=${encodeURIComponent(item.user)}`);
        await safeRenderModule("orders");
      } catch (error) {
        showGlobalError(error.message || "取消订单失败");
      }
      return;
    }

    if (e.target.matches("[data-action='view-user-orders']")) {
      const index = Number(e.target.dataset.index);
      const item = adminState.users[index];
      if (!item?.userId) return;
      try {
        await openUserOrdersModal(item.userId);
      } catch (error) {
        showGlobalError(error.message || "加载用户订单失败");
      }
    }
  });

  content.addEventListener("change", (e) => {
    if (e.target.matches("[data-action='toggle-order-select']")) {
      const index = Number(e.target.dataset.index);
      const item = adminState.orders[index];
      if (!item) return;
      if (!isCancelableOrder(item)) {
        e.target.checked = false;
        adminState.orderSelectedKeys.delete(getOrderSelectionKey(item));
        renderOrderActionBar();
        return;
      }
      const key = getOrderSelectionKey(item);
      if (e.target.checked) {
        adminState.orderSelectedKeys.add(key);
      } else {
        adminState.orderSelectedKeys.delete(key);
      }
      renderOrderActionBar();
      return;
    }

    if (e.target.matches("#orderSelectAll")) {
      const checked = !!e.target.checked;
      adminState.orders.forEach((item) => {
        const key = getOrderSelectionKey(item);
        if (checked && isCancelableOrder(item)) {
          adminState.orderSelectedKeys.add(key);
        } else {
          adminState.orderSelectedKeys.delete(key);
        }
      });
      const rowCheckboxes = content.querySelectorAll("[data-action='toggle-order-select']");
      rowCheckboxes.forEach((checkbox) => {
        checkbox.checked = checked;
      });
      renderOrderActionBar();
    }
  });

  actionBar.addEventListener("click", async (e) => {
    if (e.target.matches("[data-action='delete-orders-batch']")) {
      const selectedOrders = getSelectedOrdersOnCurrentPage();
      if (!selectedOrders.length) {
        showGlobalError("请先选择要取消的待支付订单");
        return;
      }

      if (!confirm(`确认批量取消 ${selectedOrders.length} 条待支付订单吗？`)) return;

      try {
        const payload = {
          orders: selectedOrders.map((item) => ({
            userId: item.user,
            outTradeNo: item.no
          }))
        };
        const resp = await apiPost(`${API_BASE}/orders/batch-delete`, payload);
        const data = resp.data || {};
        const successCount = Number(data.successCount || 0);
        const failCount = Number(data.failCount || 0);
        if (failCount > 0) {
          alert(`批量取消完成，成功 ${successCount} 条，失败 ${failCount} 条`);
        }
        await safeRenderModule("orders");
      } catch (error) {
        showGlobalError(error.message || "批量取消订单失败");
      }
      return;
    }

    if (!e.target.matches("[data-action='initialize-default-products']")) return;
    if (!confirm("将删除当前全部商品并重建默认商品，确认继续吗？")) return;

    const btn = e.target;
    const oldText = btn.textContent;
    btn.disabled = true;
    btn.textContent = "初始化中...";
    try {
      const resp = await apiPost(`${API_BASE}/products/initialize-default`, {});
      const data = (resp && resp.data) || {};
      await safeRenderModule("products");
      alert(`初始化成功：共 ${data.total || 0} 件，写入 ${data.inserted || 0} 件`);
    } catch (error) {
      showGlobalError(error.message || "初始化默认商品失败");
    } finally {
      btn.disabled = false;
      btn.textContent = oldText;
    }
  });

  bindModalBaseEvents();
  safeRenderModule("products");
});

async function safeRenderModule(module) {
  try {
    await renderModule(module);
    hideGlobalError();
  } catch (error) {
    const actionBar = document.getElementById("moduleActionBar");
    const content = document.getElementById("moduleContent");
    actionBar.innerHTML = "";
    const message = error.message || "请检查后端服务是否已启动";
    showGlobalError(`加载失败：${message}`);
    content.innerHTML = `<div class="empty">加载失败：${message}</div>`;
  }
}

function bindUserSessionUI() {
  const logoutBtn = document.getElementById("logoutBtn");
  logoutBtn.addEventListener("click", () => {
    clearCookie("username");
    window.location.href = "login.html";
  });
}

function bindGlobalErrorBarEvents() {
  const closeBtn = document.getElementById("globalErrorClose");
  if (!closeBtn) return;
  closeBtn.addEventListener("click", hideGlobalError);
}

function bindModalBaseEvents() {
  const modal = document.getElementById("adminModal");
  document.getElementById("adminModalClose").addEventListener("click", closeModal);
  modal.addEventListener("click", (e) => {
    if (e.target === modal) closeModal();
  });
}

async function renderModule(module) {
  const title = document.getElementById("moduleTitle");
  const hint = document.getElementById("moduleHint");
  const content = document.getElementById("moduleContent");
  const actionBar = document.getElementById("moduleActionBar");

  title.textContent = moduleMeta[module].title;
  hint.textContent = moduleMeta[module].hint;

  if (module === "products") {
    await loadProducts();
    await loadMarketing();
    actionBar.innerHTML = `
      <button class="btn btn-primary" data-action="initialize-default-products">初始化默认商品</button>
      <button class="btn btn-primary" id="addProductBtn">新增商品</button>
    `;
    content.innerHTML = renderProducts();
    document.getElementById("addProductBtn").addEventListener("click", async () => openProductModal("新增商品"));
    return;
  }

  if (module === "orders") {
    await loadOrders();
    actionBar.innerHTML = renderOrderFilterChips();
    content.innerHTML = renderOrders();
    bindOrderFilterEvents();
    bindOrderPaginationEvents();
    return;
  }

  if (module === "marketing") {
    await loadMarketing();
    actionBar.innerHTML = `<button class="btn btn-primary" id="addMarketingBtn">新增活动</button>`;
    content.innerHTML = renderMarketing();
    document.getElementById("addMarketingBtn").addEventListener("click", openMarketingModal);
    return;
  }

  await loadUsers();
  actionBar.innerHTML = renderUserFilterBar();
  content.innerHTML = renderUsers();
  bindUserFilterEvents();
  bindUserPaginationEvents();
}

async function loadProducts() {
  const resp = await apiGet(`${API_BASE}/products`);
  const data = resp.data || {};
  adminState.products = data.list || [];
  adminState.productSummary = data.summary || { total: 0, online: 0, lowStock: 0, avgPrice: 0 };
}

async function loadOrders() {
  adminState.orderSelectedKeys.clear();
  const status = adminState.orderFilter;
  const query = new URLSearchParams({
    status,
    page: String(adminState.orderPage),
    pageSize: String(adminState.orderPageSize)
  });
  if (adminState.orderStartDate) query.set("startDate", adminState.orderStartDate);
  if (adminState.orderEndDate) query.set("endDate", adminState.orderEndDate);
  const resp = await apiGet(`${API_BASE}/orders?${query.toString()}`);
  const data = resp.data || {};
  adminState.orders = data.list || [];
  adminState.orderFilteredTotal = Number(data.filteredTotal || 0);
  adminState.orderPage = Number(data.page || adminState.orderPage || 1);
  adminState.orderPageSize = Number(data.pageSize || adminState.orderPageSize || 10);
  adminState.orderSummary = data.summary || { total: 0, waiting: 0, paid: 0, canceled: 0 };
}

async function loadMarketing() {
  const resp = await apiGet(`${API_BASE}/marketing/activities`);
  const data = resp.data || {};
  adminState.marketing = data.list || [];
  adminState.marketingDiscountTypes = data.discountTypes || [];
  adminState.marketingDiscountOptions = data.discountOptions || [];
  adminState.marketingSummary = data.summary || { total: 0, active: 0, pending: 0, closed: 0 };
}

async function loadUsers() {
  const query = new URLSearchParams({
    keyword: adminState.userKeyword || "",
    page: String(adminState.userPage),
    pageSize: String(adminState.userPageSize)
  });
  const resp = await apiGet(`${API_BASE}/users?${query.toString()}`);
  const data = resp.data || {};
  adminState.users = data.list || [];
  adminState.userTotal = Number(data.total || 0);
  adminState.userPage = Number(data.page || adminState.userPage || 1);
  adminState.userPageSize = Number(data.pageSize || adminState.userPageSize || 10);
}

async function loadUserOrders(userId, page = 1, pageSize = 10) {
  const query = new URLSearchParams({ page: String(page), pageSize: String(pageSize) });
  const resp = await apiGet(`${API_BASE}/users/${encodeURIComponent(userId)}/orders?${query.toString()}`);
  return resp.data || { list: [], total: 0, page, pageSize };
}

function renderProducts() {
  const summary = adminState.productSummary;
  return `
    <div class="grid-cards">
      <div class="kpi"><div class="label">商品总数</div><div class="value">${summary.total || 0}</div></div>
      <div class="kpi"><div class="label">上架商品</div><div class="value">${summary.online || 0}</div></div>
      <div class="kpi"><div class="label">低库存</div><div class="value">${summary.lowStock || 0}</div></div>
      <div class="kpi"><div class="label">平均售价</div><div class="value">${summary.avgPrice || 0}</div></div>
    </div>
    <table class="table">
      <thead><tr><th>商品名称</th><th>SKU</th><th>活动类型</th><th>库存</th><th>状态</th><th>售价</th><th>操作</th></tr></thead>
      <tbody>
        ${adminState.products.map((item, idx) => `<tr><td>${item.name}</td><td>${item.sku}</td><td>${item.activityTypeDisplay || item.activityType || "-"}</td><td>${item.stock}</td><td>${item.status}</td><td>${item.price}</td><td><button class="btn btn-light" data-action="edit-product" data-index="${idx}">编辑</button> <button class="btn btn-light" data-action="delete-product" data-index="${idx}">删除</button></td></tr>`).join("")}
      </tbody>
    </table>
  `;
}

function renderOrderFilterChips() {
  const selectedCount = getSelectedOrdersOnCurrentPage().length;
  const options = [
    { key: "all", text: "全部" },
    { key: "待支付", text: "待支付" },
    { key: "已支付", text: "已支付" },
    { key: "已取消", text: "已取消" }
  ];
  const chips = options
    .map((opt) => `<button class="chip ${adminState.orderFilter === opt.key ? "active" : ""}" data-order-filter="${opt.key}">${opt.text}</button>`)
    .join("");

  return `
    ${chips}
    <input class="chip" type="date" id="orderStartDate" value="${adminState.orderStartDate}">
    <input class="chip" type="date" id="orderEndDate" value="${adminState.orderEndDate}">
    <button class="btn btn-light" id="orderDateSearchBtn">筛选</button>
    <button class="btn btn-light" id="orderDateResetBtn">重置</button>
    <button class="btn btn-primary" data-action="delete-orders-batch" ${selectedCount > 0 ? "" : "disabled"}>批量取消(${selectedCount})</button>
  `;
}

function renderOrderActionBar() {
  const actionBar = document.getElementById("moduleActionBar");
  if (!actionBar) return;
  actionBar.innerHTML = renderOrderFilterChips();
  bindOrderFilterEvents();
}

function bindOrderFilterEvents() {
  document.getElementById("moduleActionBar").querySelectorAll("[data-order-filter]").forEach((btn) => {
    btn.addEventListener("click", async () => {
      adminState.orderFilter = btn.dataset.orderFilter;
      adminState.orderPage = 1;
      await safeRenderModule("orders");
    });
  });

  const searchBtn = document.getElementById("orderDateSearchBtn");
  const resetBtn = document.getElementById("orderDateResetBtn");

  if (searchBtn) {
    searchBtn.addEventListener("click", async () => {
      adminState.orderStartDate = document.getElementById("orderStartDate")?.value || "";
      adminState.orderEndDate = document.getElementById("orderEndDate")?.value || "";
      adminState.orderPage = 1;
      await safeRenderModule("orders");
    });
  }

  if (resetBtn) {
    resetBtn.addEventListener("click", async () => {
      adminState.orderStartDate = "";
      adminState.orderEndDate = "";
      adminState.orderPage = 1;
      await safeRenderModule("orders");
    });
  }
}

function bindOrderPaginationEvents() {
  const prevBtn = document.getElementById("orderPrevPageBtn");
  const nextBtn = document.getElementById("orderNextPageBtn");
  const sizeSelect = document.getElementById("orderPageSizeSelect");

  if (prevBtn) {
    prevBtn.addEventListener("click", async () => {
      if (adminState.orderPage <= 1) return;
      adminState.orderPage -= 1;
      await safeRenderModule("orders");
    });
  }

  if (nextBtn) {
    nextBtn.addEventListener("click", async () => {
      const maxPage = Math.max(1, Math.ceil(adminState.orderFilteredTotal / adminState.orderPageSize));
      if (adminState.orderPage >= maxPage) return;
      adminState.orderPage += 1;
      await safeRenderModule("orders");
    });
  }

  if (sizeSelect) {
    sizeSelect.addEventListener("change", async () => {
      adminState.orderPageSize = Number(sizeSelect.value || 10);
      adminState.orderPage = 1;
      await safeRenderModule("orders");
    });
  }
}

function renderOrders() {
  const summary = adminState.orderSummary;
  const maxPage = Math.max(1, Math.ceil((adminState.orderFilteredTotal || 0) / (adminState.orderPageSize || 10)));
  const currentPage = Math.min(adminState.orderPage || 1, maxPage);
  const from = adminState.orderFilteredTotal === 0 ? 0 : (currentPage - 1) * adminState.orderPageSize + 1;
  const to = Math.min(currentPage * adminState.orderPageSize, adminState.orderFilteredTotal);
  const selectedCount = getSelectedOrdersOnCurrentPage().length;
  const cancellableOrders = adminState.orders.filter(isCancelableOrder);
  const allSelected = cancellableOrders.length > 0 && selectedCount === cancellableOrders.length;
  return `
    <div class="grid-cards">
      <div class="kpi"><div class="label">订单总数</div><div class="value">${summary.total || 0}</div></div>
      <div class="kpi"><div class="label">待支付</div><div class="value">${summary.waiting || 0}</div></div>
      <div class="kpi"><div class="label">已支付</div><div class="value">${summary.paid || 0}</div></div>
      <div class="kpi"><div class="label">已取消</div><div class="value">${summary.canceled || 0}</div></div>
    </div>
    <table class="table">
      <thead><tr><th><input type="checkbox" id="orderSelectAll" ${allSelected ? "checked" : ""}></th><th>订单号</th><th>用户</th><th>商品</th><th>订单类型</th><th>金额</th><th>状态</th><th>操作</th></tr></thead>
      <tbody>
        ${adminState.orders.map((item, idx) => `<tr><td>${renderOrderSelectCell(item, idx)}</td><td>${item.no}</td><td>${item.user}</td><td>${item.product}</td><td>${item.orderType || "拼团购买"}</td><td>${item.amount}</td><td>${item.status}</td><td>${renderOrderActionButton(item, idx)}</td></tr>`).join("")}
      </tbody>
    </table>
    <div style="display:flex;justify-content:space-between;align-items:center;margin-top:12px;color:#6b7280;">
      <span>显示 ${from}-${to} / ${adminState.orderFilteredTotal || 0}</span>
      <div style="display:flex;gap:8px;align-items:center;">
        <select id="orderPageSizeSelect" class="chip">
          <option value="10" ${adminState.orderPageSize === 10 ? "selected" : ""}>10条/页</option>
          <option value="20" ${adminState.orderPageSize === 20 ? "selected" : ""}>20条/页</option>
          <option value="50" ${adminState.orderPageSize === 50 ? "selected" : ""}>50条/页</option>
        </select>
        <button class="btn btn-light" id="orderPrevPageBtn" ${currentPage <= 1 ? "disabled" : ""}>上一页</button>
        <span>${currentPage} / ${maxPage}</span>
        <button class="btn btn-light" id="orderNextPageBtn" ${currentPage >= maxPage ? "disabled" : ""}>下一页</button>
      </div>
    </div>
  `;
}

function renderOrderSelectCell(item, idx) {
  const disabled = isCancelableOrder(item) ? "" : "disabled";
  const checked = isCancelableOrder(item) && adminState.orderSelectedKeys.has(getOrderSelectionKey(item)) ? "checked" : "";
  return `<input type="checkbox" data-action="toggle-order-select" data-index="${idx}" ${checked} ${disabled}>`;
}

function renderOrderActionButton(item, idx) {
  if (!isCancelableOrder(item)) {
    return `<span style="color:#9ca3af;">不可取消</span>`;
  }
  return `<button class="btn btn-light" data-action="delete-order" data-index="${idx}">取消</button>`;
}

function renderMarketing() {
  const summary = adminState.marketingSummary;
  const typeTags = renderMarketingTypeTags();
  const discountTags = renderMarketingDiscountTags();
  return `
    <div class="grid-cards">
      <div class="kpi"><div class="label">活动总数</div><div class="value">${summary.total || 0}</div></div>
      <div class="kpi"><div class="label">进行中</div><div class="value">${summary.active || 0}</div></div>
      <div class="kpi"><div class="label">待上线</div><div class="value">${summary.pending || 0}</div></div>
      <div class="kpi"><div class="label">已结束</div><div class="value">${summary.closed || 0}</div></div>
    </div>
    <div style="margin: 12px 0; color: #6b7280; font-size: 13px;">已识别拼团类型：${typeTags}</div>
    <div style="margin: 0 0 12px; color: #6b7280; font-size: 13px;">数据库优惠类型：${discountTags}</div>
    <table class="table">
      <thead><tr><th>活动ID</th><th>活动名称</th><th>类型</th><th>拼团方式</th><th>开始时间</th><th>结束时间</th><th>状态</th><th>操作</th></tr></thead>
      <tbody>
        ${adminState.marketing.map((item, idx) => `<tr><td>${item.activityId || "-"}</td><td>${item.name}</td><td>${item.type}</td><td>${item.groupTypeText || "-"}</td><td>${formatDate(item.start)}</td><td>${formatDate(item.end)}</td><td>${item.status}</td><td>${renderActivityActionButton(item, idx)}</td></tr>`).join("")}
      </tbody>
    </table>
  `;
}

function renderMarketingTypeTags() {
  const targets = getMarketingTypeOptions();
  if (!targets.length) return "暂无";
  return targets.map((target) => `${target}人拼团`).join(" / ");
}

function renderMarketingDiscountTags() {
  const types = (adminState.marketingDiscountTypes || []).filter(Boolean);
  if (!types.length) return "暂无";
  return Array.from(new Set(types)).join(" / ");
}

function getMarketingTypeOptions() {
  const fromDb = (adminState.marketing || [])
    .map((item) => {
      const target = Number(item.target);
      if (Number.isFinite(target)) return target;
      return parseTargetFromTypeText(item.type);
    })
    .filter((target) => Number.isFinite(target) && target >= 2 && target <= 100);
  return Array.from(new Set(fromDb)).sort((a, b) => a - b);
}

function parseTargetFromTypeText(typeText) {
  const text = String(typeText || "");
  const matched = text.match(/(\d+)\s*人拼团/);
  if (!matched) return NaN;
  return Number(matched[1]);
}

function groupDiscountOptionsByPlan(options) {
  const grouped = {};
  (options || []).forEach((option) => {
    const plan = String(option.marketPlan || "").trim();
    if (!plan) return;
    if (!grouped[plan]) grouped[plan] = [];
    grouped[plan].push(option);
  });

  if (grouped.N) {
    grouped.N = grouped.N.sort((a, b) => Number(a.marketExpr) - Number(b.marketExpr));
  }
  return grouped;
}

function renderActivityActionButton(item, idx) {
  const statusCode = Number(item.statusCode);
  if (statusCode === 0) {
    return `<button class="btn btn-light" data-action="transition-activity" data-index="${idx}" data-to-status="1">上线</button> <button class="btn btn-light" data-action="delete-activity" data-index="${idx}">删除</button>`;
  }
  if (statusCode === 1) {
    return `<button class="btn btn-light" data-action="transition-activity" data-index="${idx}" data-to-status="2">结束</button>`;
  }
  if (statusCode === 2) {
    return `<button class="btn btn-light" data-action="transition-activity" data-index="${idx}" data-to-status="1">上线</button> <button class="btn btn-light" data-action="delete-activity" data-index="${idx}">删除</button>`;
  }
  return `<button class="btn btn-light" data-action="delete-activity" data-index="${idx}">删除</button>`;
}

function renderUserFilterBar() {
  return `
    <input class="chip" id="userKeywordInput" placeholder="按用户ID搜索" value="${adminState.userKeyword || ""}">
    <button class="btn btn-light" id="userSearchBtn">搜索</button>
    <button class="btn btn-light" id="userResetBtn">重置</button>
  `;
}

function bindUserFilterEvents() {
  const searchBtn = document.getElementById("userSearchBtn");
  const resetBtn = document.getElementById("userResetBtn");
  if (searchBtn) {
    searchBtn.addEventListener("click", async () => {
      adminState.userKeyword = String(document.getElementById("userKeywordInput")?.value || "").trim();
      adminState.userPage = 1;
      await safeRenderModule("users");
    });
  }
  if (resetBtn) {
    resetBtn.addEventListener("click", async () => {
      adminState.userKeyword = "";
      adminState.userPage = 1;
      await safeRenderModule("users");
    });
  }
}

function bindUserPaginationEvents() {
  const prevBtn = document.getElementById("userPrevPageBtn");
  const nextBtn = document.getElementById("userNextPageBtn");
  const sizeSelect = document.getElementById("userPageSizeSelect");

  if (prevBtn) {
    prevBtn.addEventListener("click", async () => {
      if (adminState.userPage <= 1) return;
      adminState.userPage -= 1;
      await safeRenderModule("users");
    });
  }

  if (nextBtn) {
    nextBtn.addEventListener("click", async () => {
      const maxPage = Math.max(1, Math.ceil(adminState.userTotal / adminState.userPageSize));
      if (adminState.userPage >= maxPage) return;
      adminState.userPage += 1;
      await safeRenderModule("users");
    });
  }

  if (sizeSelect) {
    sizeSelect.addEventListener("change", async () => {
      adminState.userPageSize = Number(sizeSelect.value || 10);
      adminState.userPage = 1;
      await safeRenderModule("users");
    });
  }
}

function renderUsers() {
  const maxPage = Math.max(1, Math.ceil((adminState.userTotal || 0) / (adminState.userPageSize || 10)));
  const currentPage = Math.min(adminState.userPage || 1, maxPage);
  const from = adminState.userTotal === 0 ? 0 : (currentPage - 1) * adminState.userPageSize + 1;
  const to = Math.min(currentPage * adminState.userPageSize, adminState.userTotal);
  return `
    <table class="table">
      <thead><tr><th>用户ID</th><th>总订单</th><th>已支付</th><th>GMV</th><th>最近下单时间</th><th>操作</th></tr></thead>
      <tbody>
        ${adminState.users.map((item, idx) => `<tr><td>${item.userId || "-"}</td><td>${item.orderCount || 0}</td><td>${item.paidCount || 0}</td><td>${item.gmv || 0}</td><td>${formatDate(item.lastOrderTime) || "-"}</td><td><button class="btn btn-light" data-action="view-user-orders" data-index="${idx}">查看订单</button></td></tr>`).join("")}
      </tbody>
    </table>
    <div style="display:flex;justify-content:space-between;align-items:center;margin-top:12px;color:#6b7280;">
      <span>显示 ${from}-${to} / ${adminState.userTotal || 0}</span>
      <div style="display:flex;gap:8px;align-items:center;">
        <select id="userPageSizeSelect" class="chip">
          <option value="10" ${adminState.userPageSize === 10 ? "selected" : ""}>10条/页</option>
          <option value="20" ${adminState.userPageSize === 20 ? "selected" : ""}>20条/页</option>
          <option value="50" ${adminState.userPageSize === 50 ? "selected" : ""}>50条/页</option>
        </select>
        <button class="btn btn-light" id="userPrevPageBtn" ${currentPage <= 1 ? "disabled" : ""}>上一页</button>
        <span>${currentPage} / ${maxPage}</span>
        <button class="btn btn-light" id="userNextPageBtn" ${currentPage >= maxPage ? "disabled" : ""}>下一页</button>
      </div>
    </div>
  `;
}

async function openUserOrdersModal(userId) {
  const data = await loadUserOrders(userId, 1, 10);
  const body = document.getElementById("adminModalBody");
  document.getElementById("adminModalTitle").textContent = `用户订单 - ${userId}`;
  body.innerHTML = `
    <table class="table">
      <thead><tr><th>订单号</th><th>商品</th><th>订单类型</th><th>金额</th><th>状态</th><th>时间</th></tr></thead>
      <tbody>
        ${(data.list || []).map((item) => `<tr><td>${item.no || "-"}</td><td>${item.product || "-"}</td><td>${item.orderType || "拼团购买"}</td><td>${item.amount || 0}</td><td>${item.status || "-"}</td><td>${formatDate(item.createTime)}</td></tr>`).join("")}
      </tbody>
    </table>
    <div style="margin-top:8px;color:#6b7280;">共 ${Number(data.total || 0)} 条</div>
  `;
  openModal();
}

async function openProductModal(title, item, index) {
  if (!adminState.marketing.length) {
    try {
      await loadMarketing();
    } catch (error) {
      showGlobalError(error.message || "加载活动列表失败");
    }
  }

  const body = document.getElementById("adminModalBody");
  document.getElementById("adminModalTitle").textContent = title;
  const activeMarketing = adminState.marketing.filter((activity) => Number(activity.statusCode) === 1);
  const activityOptions = activeMarketing.map((activity) => {
    const selected = Number(item?.activityId) === Number(activity.activityId) ? "selected" : "";
    return `<option value="${activity.activityId}" ${selected}>${activity.activityId} | ${activity.type} | ${activity.name}</option>`;
  }).join("");
  const noActivitySelected = !item?.activityId ? "selected" : "";
  body.innerHTML = `
    <form id="productForm" class="form-grid">
      <label>商品名称<input name="name" value="${item ? item.name : ""}" required></label>
      <label>SKU<input name="sku" value="${item ? item.sku : ""}" required></label>
      <label>绑定活动(可选)
        <select name="activityId">
          <option value="" ${noActivitySelected}>无活动(原价购买)</option>
          ${activityOptions}
        </select>
      </label>
      <label>库存<input name="stock" type="number" min="0" value="${item ? item.stock : 0}" required></label>
      <label>状态
        <select name="status">
          <option value="上架" ${item && item.status === "上架" ? "selected" : ""}>上架</option>
          <option value="下架" ${item && item.status === "下架" ? "selected" : ""}>下架</option>
        </select>
      </label>
      <label>售价<input name="price" type="number" min="0" value="${item ? item.price : 0}" required></label>
      <div class="form-actions">
        <button class="btn btn-light" type="button" id="cancelForm">取消</button>
        <button class="btn btn-primary" type="submit">保存</button>
      </div>
    </form>
  `;

  openModal();
  document.getElementById("cancelForm").addEventListener("click", closeModal);
  document.getElementById("productForm").addEventListener("submit", async (e) => {
    e.preventDefault();

    const form = new FormData(e.target);
    const activityId = String(form.get("activityId") || "").trim();

    const payload = {
      name: String(form.get("name")).trim(),
      sku: String(form.get("sku")).trim(),
      activityId,
      stock: Number(form.get("stock")),
      status: String(form.get("status")),
      price: Number(form.get("price"))
    };

    try {
      if (index === undefined) {
        await apiPost(`${API_BASE}/products`, payload);
      } else {
        const goodsId = adminState.products[index].sku;
        await apiPut(`${API_BASE}/products/${encodeURIComponent(goodsId)}`, payload);
      }
      closeModal();
      await safeRenderModule("products");
    } catch (error) {
      showGlobalError(error.message || "保存商品失败");
    }
  });
}

function openMarketingModal() {
  const body = document.getElementById("adminModalBody");
  document.getElementById("adminModalTitle").textContent = "新增营销活动";
  const typeOptions = getMarketingTypeOptions();
  const defaultTarget = typeOptions[0] || "";
  const targetDatalist = typeOptions.map((target) => `<option value="${target}">${target}人拼团</option>`).join("");
  const discountOptions = adminState.marketingDiscountOptions || [];
  const groupedOptions = groupDiscountOptionsByPlan(discountOptions);
  const planSelectOptions = Object.keys(groupedOptions).map((plan) => {
    const first = groupedOptions[plan]?.[0] || {};
    const planText = first.marketPlanText || plan;
    return `<option value="${plan}">${planText}</option>`;
  }).join("");

  body.innerHTML = `
    <form id="marketingForm" class="form-grid">
      <label>活动名称<input name="name" required></label>
      <label>活动类型(拼团人数)
        <input name="target" type="number" min="2" max="100" step="1" list="marketingTypeOptions" value="${defaultTarget}" required>
        <datalist id="marketingTypeOptions">${targetDatalist}</datalist>
      </label>
      <label>优惠类型
        <select name="discountPlan" id="discountPlanSelect" required>
          <option value="">请选择优惠类型</option>
          ${planSelectOptions}
        </select>
      </label>
      <label id="discountOptionLabel">优惠方案
        <select name="discountId" id="discountOptionSelect" required>
          <option value="">请先选择优惠类型</option>
        </select>
      </label>
      <label>开始日期<input name="start" type="date" required></label>
      <label>结束日期<input name="end" type="date" required></label>
      <div class="form-actions">
        <button class="btn btn-light" type="button" id="cancelForm">取消</button>
        <button class="btn btn-primary" type="submit">创建</button>
      </div>
    </form>
  `;

  openModal();
  document.getElementById("cancelForm").addEventListener("click", closeModal);

  if (!discountOptions.length) {
    showGlobalError("当前无可用优惠类型，请先初始化优惠配置");
  }

  const discountPlanSelect = document.getElementById("discountPlanSelect");
  const discountOptionSelect = document.getElementById("discountOptionSelect");
  const discountOptionLabel = document.getElementById("discountOptionLabel");

  const renderDiscountOptionsByPlan = (plan) => {
    const currentOptions = groupedOptions[plan] || [];
    const isNPlan = plan === "N";

    if (discountOptionSelect) {
      discountOptionSelect.innerHTML = currentOptions.length
        ? currentOptions.map((option, index) => {
          const discountId = String(option.discountId || "");
          const discountName = option.discountName || "未命名优惠";
          const expr = String(option.marketExpr || "").trim();
          const selected = index === 0 ? "selected" : "";
          if (isNPlan) {
            return `<option value="${discountId}" ${selected}>${expr || discountName}元</option>`;
          }
          return `<option value="${discountId}" ${selected}>${discountId} | ${discountName}</option>`;
        }).join("")
        : `<option value="">该类型暂无可用优惠</option>`;
    }

    if (discountOptionLabel) {
      discountOptionLabel.childNodes[0].nodeValue = isNPlan ? "N元购金额档位" : "优惠方案";
    }
  };

  if (discountPlanSelect) {
    discountPlanSelect.addEventListener("change", () => renderDiscountOptionsByPlan(discountPlanSelect.value));
    const defaultPlan = groupedOptions.N ? "N" : (Object.keys(groupedOptions)[0] || "");
    discountPlanSelect.value = defaultPlan;
    renderDiscountOptionsByPlan(defaultPlan);
  }

  const targetInput = document.querySelector("#marketingForm input[name='target']");
  const nameInput = document.querySelector("#marketingForm input[name='name']");
  if (targetInput && nameInput) {
    targetInput.addEventListener("input", () => {
      const target = Number(targetInput.value);
      if (!Number.isFinite(target) || target < 2) return;
      if (!nameInput.value.trim() || /人拼团活动$/.test(nameInput.value.trim())) {
        nameInput.value = `${target}人拼团活动`;
      }
    });
    nameInput.value = `${targetInput.value}人拼团活动`;
  }
  document.getElementById("marketingForm").addEventListener("submit", async (e) => {
    e.preventDefault();
    const form = new FormData(e.target);
    const name = String(form.get("name")).trim();
    const target = Number(form.get("target"));
    const discountId = String(form.get("discountId") || "").trim();
    const start = String(form.get("start"));
    const end = String(form.get("end"));

    if (!name || !start || !end) {
      showGlobalError("请完整填写活动信息");
      return;
    }

    if (!discountId) {
      showGlobalError("请选择优惠类型");
      return;
    }

    if (!Number.isFinite(target) || target < 2 || target > 100) {
      showGlobalError("活动类型(拼团人数)不合法，支持2-100人");
      return;
    }

    const startTimestamp = new Date(start + "T00:00:00").getTime();
    const endTimestamp = new Date(end + "T23:59:59").getTime();
    if (!Number.isFinite(startTimestamp) || !Number.isFinite(endTimestamp) || startTimestamp >= endTimestamp) {
      showGlobalError("活动开始时间必须早于结束时间");
      return;
    }

    const submitBtn = e.target.querySelector("button[type='submit']");
    if (submitBtn) submitBtn.disabled = true;

    try {
      await apiPost(`${API_BASE}/marketing/activities`, {
        name,
        type: String(target),
        target,
        discountId,
        start,
        end,
        startTimestamp,
        endTimestamp
      });
      closeModal();
      await safeRenderModule("marketing");
    } catch (error) {
      showGlobalError(error.message || "创建活动失败");
    } finally {
      if (submitBtn) submitBtn.disabled = false;
    }
  });
}

function showGlobalError(message) {
  const bar = document.getElementById("globalErrorBar");
  const text = document.getElementById("globalErrorText");
  const now = Date.now();
  const isRepeated = message === lastErrorMessage && now - lastErrorTime < 3000;

  if (!isRepeated) {
    text.textContent = message;
    lastErrorMessage = message;
    lastErrorTime = now;
  }

  bar.style.display = "block";

  if (globalErrorTimer) {
    clearTimeout(globalErrorTimer);
  }
  globalErrorTimer = setTimeout(() => {
    hideGlobalError();
  }, 5000);
}

function hideGlobalError() {
  const bar = document.getElementById("globalErrorBar");
  const text = document.getElementById("globalErrorText");
  text.textContent = "";
  bar.style.display = "none";
  if (globalErrorTimer) {
    clearTimeout(globalErrorTimer);
    globalErrorTimer = null;
  }
}

function openModal() {
  document.getElementById("adminModal").style.display = "block";
}

function closeModal() {
  document.getElementById("adminModal").style.display = "none";
}

async function apiGet(url) {
  const response = await fetch(url, {
    method: "GET",
    headers: { "X-Admin-User": getCookie("username") || "" }
  });
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }
  const json = await response.json();
  if (json.code !== "0000") {
    throw new Error(json.info || "接口调用失败");
  }
  return json;
}

async function apiPost(url, payload) {
  const response = await fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Admin-User": getCookie("username") || ""
    },
    body: JSON.stringify(payload)
  });
  const json = await response.json();
  if (json.code !== "0000") {
    throw new Error(json.info || "接口调用失败");
  }
  return json;
}

async function apiPut(url, payload) {
  const response = await fetch(url, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
      "X-Admin-User": getCookie("username") || ""
    },
    body: JSON.stringify(payload)
  });
  const json = await response.json();
  if (json.code !== "0000") {
    throw new Error(json.info || "接口调用失败");
  }
  return json;
}

async function apiDelete(url) {
  const response = await fetch(url, {
    method: "DELETE",
    headers: { "X-Admin-User": getCookie("username") || "" }
  });
  const json = await response.json();
  if (json.code !== "0000") {
    throw new Error(json.info || "接口调用失败");
  }
  return json;
}

function formatDate(input) {
  if (!input) return "-";
  if (typeof input === "string") {
    if (input.length >= 10) return input.substring(0, 10);
    return input;
  }
  return String(input).substring(0, 10);
}

function getOrderSelectionKey(item) {
  return `${item.user || ""}::${item.no || ""}`;
}

function isCancelableOrder(item) {
  return !!item && item.status === "待支付";
}

function getSelectedOrdersOnCurrentPage() {
  return adminState.orders.filter((item) => isCancelableOrder(item) && adminState.orderSelectedKeys.has(getOrderSelectionKey(item)));
}

function getCookie(name) {
  const cookieArr = document.cookie.split(";");
  for (let i = 0; i < cookieArr.length; i++) {
    const cookiePair = cookieArr[i].split("=");
    if (name === cookiePair[0].trim()) {
      return decodeURIComponent(cookiePair[1]);
    }
  }
  return null;
}

function clearCookie(name) {
  document.cookie = `${name}=; expires=${new Date(0).toUTCString()}; path=/`;
}

