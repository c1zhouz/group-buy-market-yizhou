function resolveApiOrigin() {
  const host = window.location.hostname;
  const port = window.location.port;
  const isLocalHost = host === "127.0.0.1" || host === "localhost";
  if (isLocalHost && port === "8091") {
    return window.location.origin;
  }
  return isLocalHost ? "http://127.0.0.1:8091" : window.location.origin;
}

const BASE_URL = resolveApiOrigin();

const state = {
  userId: null,
  products: [],
  filteredProducts: [],
  category: "ALL",
  keyword: "",
  selected: null,
  outTradeNo: "",
  directOutTradeNo: "",
  hasPendingGroupOrder: false,
  payMode: "group",
  prices: {
    origin: 0,
    group: 0,
    deduction: 0
  }
};

document.addEventListener("DOMContentLoaded", async () => {
  state.userId = getCookie("username");
  if (!state.userId) {
    window.location.href = "login.html";
    return;
  }

  await loadProducts();

  bindEvents();
  renderProductList();
  renderSelectedProduct();
  syncTradeLayoutHeight();
  await refreshMarketData();
});

function bindEvents() {
  const searchInput = document.getElementById("productSearchInput");
  if (searchInput) {
    searchInput.addEventListener("input", async (event) => {
      state.keyword = String(event.target.value || "").trim();
      applyProductFilters();
      await ensureSelectionAfterFilter();
      renderProductList();
      renderSelectedProduct();
      await refreshMarketData();
    });
  }

  const categoryFilter = document.getElementById("categoryFilter");
  if (categoryFilter) {
    categoryFilter.addEventListener("click", async (event) => {
      const chip = event.target.closest(".category-chip");
      if (!chip) return;
      const next = chip.dataset.category || "ALL";
      if (next === state.category) return;
      state.category = next;
      applyProductFilters();
      await ensureSelectionAfterFilter();
      renderProductList();
      renderSelectedProduct();
      await refreshMarketData();
    });
  }

  document.getElementById("buyAloneBtn").addEventListener("click", onDirectBuy);
  document.getElementById("groupBuyBtn").addEventListener("click", () => onGroupBuy(null));
  document.getElementById("cancelPayment").addEventListener("click", () => closePaymentModal());
  document.getElementById("completePayment").addEventListener("click", onCompletePayment);

  document.getElementById("groupListContainer").addEventListener("click", (event) => {
    const button = event.target.closest(".join-btn");
    if (!button) return;
    onGroupBuy(button.dataset.teamId || null);
  });

  window.addEventListener("click", (event) => {
    if (event.target === document.getElementById("paymentModal")) {
      closePaymentModal();
    }
  });

  window.addEventListener("unhandledrejection", () => {
    renderFallback("服务暂不可用，请稍后再试");
  });

  window.addEventListener("resize", syncTradeLayoutHeight);
}

function renderProductList() {
  renderCategoryFilters();

  const productList = document.getElementById("productList");
  const productCount = document.getElementById("productCount");
  const displayProducts = state.filteredProducts;
  if (productCount) {
    productCount.textContent = `共 ${state.products.length} 件，当前显示 ${displayProducts.length} 件`;
  }

  if (!displayProducts.length) {
    productList.innerHTML = state.products.length
      ? `<div class="empty">当前筛选条件下暂无商品，请调整分类或搜索关键词</div>`
      : `<div class="empty">暂无商品，请先在后台商品管理中新增</div>`;
    syncTradeLayoutHeight();
    return;
  }

  productList.innerHTML = displayProducts.map((item) => `
    <div class="product-item ${item.id === state.selected.id ? "active" : ""}" data-product-id="${item.id}">
      <div class="product-item-main">
        <h4>${item.name}</h4>
        <p>${item.subtitle}</p>
      </div>
      <div class="product-item-side">
        <div class="price">${null == item.groupPrice ? "暂无拼团价" : `拼团 ￥${formatAmount(item.groupPrice)}`}</div>
        <div class="price-sub">单买 ￥${formatAmount(item.originalPrice)}</div>
      </div>
    </div>
  `).join("");

  productList.querySelectorAll(".product-item").forEach((el) => {
    el.addEventListener("click", async () => {
      const next = state.products.find((item) => item.id === el.dataset.productId);
      if (!next || next.id === state.selected.id) return;
      state.selected = next;
      sessionStorage.setItem("selectedGoodsId", next.goodsId);
      state.outTradeNo = "";
      state.prices = {
        origin: next.originalPrice,
        group: null == next.groupPrice ? next.originalPrice : next.groupPrice,
        deduction: null == next.groupPrice ? 0 : next.originalPrice - next.groupPrice
      };
      renderProductList();
      renderSelectedProduct();
      await refreshMarketData();
    });
  });

  syncTradeLayoutHeight();
}

function syncTradeLayoutHeight() {
  const tradeLayout = document.querySelector(".trade-layout");
  const catalogPanel = document.querySelector(".catalog-panel");
  if (!tradeLayout || !catalogPanel) return;

  if (window.innerWidth <= 980) {
    tradeLayout.style.height = "auto";
    return;
  }

  const height = Math.ceil(catalogPanel.getBoundingClientRect().height);
  tradeLayout.style.height = `${height}px`;
}

async function loadProducts() {
  try {
    const response = await fetch(`${BASE_URL}/api/v1/gbm/index/query_market_product_list`, {
      method: "GET"
    });
    const result = await response.json();
    const items = ((result || {}).data || {}).list || [];
    if (!items.length) {
      state.products = [];
      state.selected = null;
      state.prices = { origin: 0, group: 0, deduction: 0 };
      return;
    }

    const baseProducts = items.map((item, idx) => {
      const originalPrice = toNumber(item.price, 0);
      const hasActivity = !!item.activityId;
      const sku = String(item.sku || item.goodsId || "-");
      return {
        id: `p-${item.sku}-${idx}`,
        goodsId: String(item.sku),
        activityId: hasActivity ? toNumber(item.activityId, 0) : 0,
        name: item.name,
        subtitle: formatProductSubtitle(item.activityType, sku),
        category: resolveCategory(String(item.sku), String(item.name)),
        originalPrice,
        groupPrice: null
      };
    });

    state.products = await hydrateTrialPrices(baseProducts);

    applyProductFilters();

    const selectedGoodsId = sessionStorage.getItem("selectedGoodsId");
    state.selected = state.filteredProducts.find((item) => item.goodsId === selectedGoodsId)
      || state.filteredProducts[0]
      || state.products[0];
    state.prices = {
      origin: state.selected.originalPrice,
      group: null == state.selected.groupPrice ? state.selected.originalPrice : state.selected.groupPrice,
      deduction: null == state.selected.groupPrice ? 0 : state.selected.originalPrice - state.selected.groupPrice
    };
  } catch (error) {
    console.error("加载商品列表失败", error);
    state.products = [];
    state.filteredProducts = [];
    state.selected = null;
    state.prices = { origin: 0, group: 0, deduction: 0 };
  }
}

function resolveCategory(goodsId, name) {
  const gid = String(goodsId || "");
  const title = String(name || "").toLowerCase();
  if (gid.startsWith("1101") || /(iphone|mate\s?\d|mate x)/i.test(title)) return "手机";
  if (gid.startsWith("1102") || /(airpods|freebuds)/i.test(title)) return "耳机";
  if (gid.startsWith("1103") || /(ipad|matepad|平板)/i.test(title)) return "平板";
  if (gid.startsWith("1104") || /(macbook|matebook|笔记本|电脑)/i.test(title)) return "电脑";
  return "其他";
}

function applyProductFilters() {
  const keyword = state.keyword.toLowerCase();
  state.filteredProducts = state.products.filter((item) => {
    const inCategory = state.category === "ALL" || item.category === state.category;
    const inKeyword = !keyword || String(item.name || "").toLowerCase().includes(keyword);
    return inCategory && inKeyword;
  });
}

function renderCategoryFilters() {
  const container = document.getElementById("categoryFilter");
  if (!container) return;

  const counts = state.products.reduce((acc, item) => {
    acc[item.category] = (acc[item.category] || 0) + 1;
    return acc;
  }, {});

  const categories = ["ALL", "手机", "耳机", "平板", "电脑", "其他"]
    .filter((category) => category === "ALL" || (counts[category] || 0) > 0);

  container.innerHTML = categories.map((category) => {
    const active = category === state.category ? "active" : "";
    const text = category === "ALL" ? "全部" : category;
    const count = category === "ALL" ? state.products.length : (counts[category] || 0);
    return `<button class="category-chip ${active}" data-category="${category}">${text} (${count})</button>`;
  }).join("");
}

async function ensureSelectionAfterFilter() {
  if (!state.filteredProducts.length) {
    state.selected = null;
    return;
  }

  const stillVisible = state.selected && state.filteredProducts.some((item) => item.id === state.selected.id);
  if (stillVisible) {
    return;
  }

  state.selected = state.filteredProducts[0];
  sessionStorage.setItem("selectedGoodsId", state.selected.goodsId);
  state.outTradeNo = "";
  state.prices = {
    origin: state.selected.originalPrice,
    group: null == state.selected.groupPrice ? state.selected.originalPrice : state.selected.groupPrice,
    deduction: null == state.selected.groupPrice ? 0 : state.selected.originalPrice - state.selected.groupPrice
  };
}

async function hydrateTrialPrices(products) {
  return Promise.all(products.map(async (product) => {
    if (!product.activityId) {
      return product;
    }

    const trial = await queryTrialPrice(product.goodsId);
    if (!trial) {
      return product;
    }

    const payPrice = toNumber(trial.payPrice, product.originalPrice);
    return {
      ...product,
      originalPrice: toNumber(trial.originalPrice, product.originalPrice),
      groupPrice: payPrice
    };
  }));
}

async function queryTrialPrice(goodsId) {
  try {
    const response = await fetch(`${BASE_URL}/api/v1/gbm/index/query_group_buy_market_config`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        userId: state.userId,
        source: "s01",
        channel: "c01",
        goodsId
      })
    });
    const result = await response.json();
    if (result.code !== "0000") {
      return null;
    }
    const goods = (result.data || {}).goods || {};
    return {
      originalPrice: goods.originalPrice,
      payPrice: goods.payPrice,
      deductionPrice: goods.deductionPrice
    };
  } catch (error) {
    console.warn("试算价格加载失败 goodsId:", goodsId, error);
    return null;
  }
}

function formatProductSubtitle(activityType, sku) {
  const normalizedType = String(activityType || "无活动 / 原价购买").trim();
  const normalizedSku = String(sku || "-").trim();
  return `${normalizedType} | SKU ${normalizedSku}`;
}

function renderSelectedProduct() {
  if (!state.selected) {
    renderNoProductState();
    return;
  }
  document.getElementById("productTitle").textContent = state.selected.name;
  document.getElementById("productSubtitle").textContent = state.selected.subtitle;
  document.getElementById("promotionText").textContent = state.selected.activityId
    ? `拼团立减 ￥${formatAmount(state.prices.deduction)}`
    : "该商品暂无拼团活动，仅支持原价购买";
  document.getElementById("originPrice").textContent = `￥${formatAmount(state.prices.origin)}`;
  document.getElementById("groupPrice").textContent = `￥${formatAmount(state.prices.group)}`;
  document.getElementById("saveAmount").textContent = `立省 ￥${formatAmount(state.prices.deduction)}`;
  renderActionBar();
}

function renderNoProductState() {
  document.getElementById("productTitle").textContent = "暂无商品";
  document.getElementById("productSubtitle").textContent = "请前往后台商品管理新增商品";
  document.getElementById("promotionText").textContent = "当前暂无可售商品";
  document.getElementById("originPrice").textContent = "￥0";
  document.getElementById("groupPrice").textContent = "￥0";
  document.getElementById("saveAmount").textContent = "立省 ￥0";
  document.getElementById("groupListContainer").innerHTML = `<div class="empty">当前暂无可参与团队</div>`;
  renderActionBar();
}

function renderActionBar() {
  if (!state.selected) {
    const aloneBtn = document.getElementById("buyAloneBtn");
    const groupBtn = document.getElementById("groupBuyBtn");
    aloneBtn.textContent = "单独购买(暂无商品)";
    groupBtn.textContent = "开团购买(暂无商品)";
    aloneBtn.disabled = true;
    groupBtn.disabled = true;
    return;
  }

  document.getElementById("buyAloneBtn").textContent = `单独购买(￥${formatAmount(state.prices.origin)})`;
  document.getElementById("buyAloneBtn").disabled = false;
  const groupBtn = document.getElementById("groupBuyBtn");
  groupBtn.textContent = state.selected.activityId ? `开团购买(￥${formatAmount(state.prices.group)})` : "开团购买(暂无活动)";
  groupBtn.disabled = !state.selected.activityId;
}

async function refreshMarketData() {
  if (!state.selected) {
    renderNoProductState();
    return;
  }

  if (!state.selected.activityId) {
    state.prices.origin = state.selected.originalPrice;
    state.prices.group = state.selected.originalPrice;
    state.prices.deduction = 0;
    document.getElementById("promotionText").textContent = "该商品暂无拼团活动，仅支持原价购买";
    document.getElementById("originPrice").textContent = `￥${formatAmount(state.prices.origin)}`;
    document.getElementById("groupPrice").textContent = `￥${formatAmount(state.prices.group)}`;
    document.getElementById("groupListContainer").innerHTML = `<div class="empty">该商品未绑定拼团活动</div>`;
    renderActionBar();
    return;
  }

  try {
    const response = await fetch(`${BASE_URL}/api/v1/gbm/index/query_group_buy_market_config`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        userId: state.userId,
        source: "s01",
        channel: "c01",
        goodsId: state.selected.goodsId
      })
    });

    const result = await response.json();
    if (result.code !== "0000") {
      const apiMessage = result.info || "营销信息加载失败，已展示默认价格";
      if (/活动|拼团/.test(apiMessage)) {
        renderNoActivityState(apiMessage);
      } else {
        renderFallback(apiMessage);
      }
      return;
    }

    const data = result.data || {};
    const goods = data.goods || {};
    const teamList = data.teamList || [];
    const teamStatistic = data.teamStatistic || { allTeamUserCount: 0 };

    state.prices.origin = toNumber(goods.originalPrice, state.selected.originalPrice);
    state.prices.group = toNumber(goods.payPrice, state.selected.groupPrice);
    state.prices.deduction = toNumber(goods.deductionPrice, state.prices.origin - state.prices.group);

    document.getElementById("promotionText").textContent = `直降 ￥${formatAmount(state.prices.deduction)}，${teamStatistic.allTeamUserCount || 0} 人正在拼`;
    document.getElementById("originPrice").textContent = `￥${formatAmount(state.prices.origin)}`;
    document.getElementById("groupPrice").textContent = `￥${formatAmount(state.prices.group)}`;
    renderActionBar();

    renderGroupList(teamList);
  } catch (error) {
    console.error("加载营销配置失败", error);
    renderFallback("服务暂不可用，请稍后再试");
  }
}

function renderGroupList(teamList) {
  const container = document.getElementById("groupListContainer");
  state.outTradeNo = "";
  state.hasPendingGroupOrder = false;
  if (!teamList.length) {
    container.innerHTML = `<div class="empty">当前暂无可参与团队，点击“开团购买”发起新团</div>`;
    return;
  }

  container.innerHTML = teamList.map((team) => {
    const target = toNumber(team.targetCount, 0);
    const lock = toNumber(team.lockCount, 0);
    const remain = Math.max(target - lock, 0);
    const countdown = normalizeCountdown(team.validTimeCountdown);

    const memberUserIds = Array.isArray(team.memberUserIds) ? team.memberUserIds : [];
    const isCurrentUserInTeam = state.userId === team.userId || memberUserIds.includes(state.userId);
    if (isCurrentUserInTeam && countdown !== "已结束") {
      state.hasPendingGroupOrder = true;
    }

    const uniqueMembers = [];
    const pushUnique = (userId) => {
      if (!userId) return;
      if (uniqueMembers.includes(userId)) return;
      uniqueMembers.push(userId);
    };
    pushUnique(team.userId);
    memberUserIds.forEach(pushUnique);
    const teamUserText = uniqueMembers.join(" | ");

    return `
      <div class="group-item">
        <div class="group-user">${teamUserText}</div>
        <div class="group-desc">${remain} 人</div>
        <div class="group-desc"><span class="countdown">${countdown}</span></div>
        <button class="join-btn" data-team-id="${team.teamId}">参与拼团</button>
      </div>
    `;
  }).join("");

  initCountdowns();
}

function renderFallback(message) {
  document.getElementById("promotionText").textContent = message;
  document.getElementById("groupListContainer").innerHTML = `<div class="empty">${message}</div>`;
  state.hasPendingGroupOrder = false;
  state.prices.origin = state.selected.originalPrice;
  state.prices.group = null == state.selected.groupPrice ? state.selected.originalPrice : state.selected.groupPrice;
  state.prices.deduction = null == state.selected.groupPrice ? 0 : state.selected.originalPrice - state.selected.groupPrice;
  renderSelectedProduct();
}

function renderNoActivityState(message) {
  state.prices.origin = state.selected.originalPrice;
  state.prices.group = state.selected.originalPrice;
  state.prices.deduction = 0;
  document.getElementById("promotionText").textContent = message || "该商品暂无拼团活动，仅支持原价购买";
  document.getElementById("originPrice").textContent = `￥${formatAmount(state.prices.origin)}`;
  document.getElementById("groupPrice").textContent = `￥${formatAmount(state.prices.group)}`;
  document.getElementById("groupListContainer").innerHTML = `<div class="empty">该商品未绑定拼团活动</div>`;
  state.hasPendingGroupOrder = false;
  renderActionBar();
}

async function onGroupBuy(teamId) {
  if (!state.selected) {
    alert("当前暂无商品，请先在后台新增商品");
    return;
  }

  if (!state.selected.activityId) {
    alert("该商品暂无拼团活动，请选择单独购买");
    return;
  }

  state.payMode = "group";
  const groupPrice = state.prices.group;

  if (!teamId && state.hasPendingGroupOrder) {
    alert("E0107：您有拼团订单未完成，请勿重复开团");
    return;
  }

  const generatedTradeNo = generateRandomNumber(12);
  const payload = {
    userId: state.userId,
    teamId,
    activityId: state.selected.activityId,
    goodsId: state.selected.goodsId,
    source: "s01",
    channel: "c01",
    outTradeNo: generatedTradeNo,
    notifyUrl: `${BASE_URL}/api/v1/test/group_buy_notify`
  };

  try {
    const response = await fetch(`${BASE_URL}/api/v1/gbm/trade/lock_market_pay_order`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(payload)
    });
    const result = await response.json();
    if (result.code !== "0000") {
      alert(`${result.code}：${result.info}`);
      return;
    }
    state.outTradeNo = generatedTradeNo;
    openPaymentModal(groupPrice, state.outTradeNo);
  } catch (error) {
    console.error("锁单失败", error);
    alert("锁单失败，请稍后重试");
  }
}

function onDirectBuy() {
  if (!state.selected) {
    alert("当前暂无商品，请先在后台新增商品");
    return;
  }
  state.payMode = "direct";
  state.directOutTradeNo = `D${generateRandomNumber(11)}`;
  openPaymentModal(state.prices.origin, state.directOutTradeNo);
}

async function onCompletePayment() {
  if (state.payMode === "direct") {
    await submitDirectBuy();
    return;
  }
  await submitGroupSettlement();
}

async function submitDirectBuy() {
  const payload = {
    userId: state.userId,
    outTradeNo: state.directOutTradeNo,
    goodsId: state.selected.goodsId,
    source: "s01",
    channel: "c01",
    payAmount: String(state.prices.origin),
    outTradeTime: new Date()
  };

  try {
    await fetch(`${BASE_URL}/api/v1/test/direct_buy_notify`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(payload)
    });
    alert("单独购买成功！已完成支付");
    state.directOutTradeNo = "";
    closePaymentModal({ cancelPending: false });
  } catch (error) {
    console.error("单独购买失败", error);
    alert("单独购买请求失败，请检查网络连接");
  }
}

async function submitGroupSettlement() {
  const payload = {
    source: "s01",
    channel: "c01",
    userId: state.userId,
    outTradeNo: state.outTradeNo,
    outTradeTime: new Date()
  };

  try {
    const response = await fetch(`${BASE_URL}/api/v1/gbm/trade/settlement_market_pay_order`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(payload)
    });
    const result = await response.json();
    if (result.code === "0000") {
      alert("支付&结算成功！");
      state.outTradeNo = "";
      await refreshMarketData();
      return;
    }
    alert(`支付&结算失败：${result.info || "未知错误"}`);
  } catch (error) {
    console.error("结算失败", error);
    alert("网络异常，请检查连接");
  } finally {
    closePaymentModal({ cancelPending: false });
  }
}

function openPaymentModal(amount, tradeNo) {
  if (!state.selected) return;
  document.getElementById("paymentProductName").textContent = `商品：${state.selected.name}`;
  document.getElementById("paymentAmount").textContent = `支付金额：￥${toNumber(amount, 0).toFixed(0)}`;
  document.getElementById("outTradeNo").textContent = tradeNo || "";
  document.getElementById("paymentModal").style.display = "block";
}

async function closePaymentModal(options = { cancelPending: true }) {
  document.getElementById("paymentModal").style.display = "none";

  const shouldCancel = options && Object.prototype.hasOwnProperty.call(options, "cancelPending")
    ? !!options.cancelPending
    : true;
  if (!shouldCancel) {
    return;
  }

  if (state.payMode === "direct") {
    if (!state.directOutTradeNo) {
      return;
    }
    await cancelPendingDirectOrder();
    return;
  }

  if (state.payMode !== "group" || !state.outTradeNo) {
    return;
  }

  await cancelPendingGroupOrder();
}

async function cancelPendingDirectOrder() {
  const payload = {
    source: "s01",
    channel: "c01",
    userId: state.userId,
    outTradeNo: state.directOutTradeNo,
    goodsId: state.selected ? state.selected.goodsId : "DIRECT-BUY",
    payAmount: String(state.prices.origin)
  };

  try {
    const response = await fetch(`${BASE_URL}/api/v1/test/direct_buy_cancel_notify`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(payload)
    });
    const result = await response.text();
    if (String(result).trim().toLowerCase() !== "success") {
      alert("单独购买取消记录失败，请稍后重试");
      return;
    }

    state.directOutTradeNo = "";
  } catch (error) {
    console.error("单独购买取消记录失败", error);
    alert("单独购买取消记录失败，请稍后重试");
  }
}

async function cancelPendingGroupOrder() {
  const payload = {
    source: "s01",
    channel: "c01",
    userId: state.userId,
    outTradeNo: state.outTradeNo
  };

  try {
    const response = await fetch(`${BASE_URL}/api/v1/gbm/trade/cancel_market_pay_order`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(payload)
    });
    const result = await response.json();
    if (result.code !== "0000") {
      alert(`取消未支付订单失败：${result.info || "未知错误"}`);
      return;
    }

    state.outTradeNo = "";
    await refreshMarketData();
  } catch (error) {
    console.error("取消未支付订单失败", error);
    alert("取消支付失败，请稍后重试");
  }
}

function initCountdowns() {
  document.querySelectorAll(".countdown").forEach((el) => {
    const text = el.textContent.trim();
    if (!/^\d{2}:\d{2}:\d{2}$/.test(text) || text === "00:00:00") {
      el.textContent = "已结束";
      return;
    }
    new Countdown(el, text);
  });
}

function normalizeCountdown(value) {
  const text = String(value || "").trim();
  if (/^\d{2}:\d{2}:\d{2}$/.test(text) && text !== "00:00:00") {
    return text;
  }
  return "已结束";
}

function toNumber(value, fallback) {
  const num = Number(value);
  return Number.isFinite(num) ? num : Number(fallback);
}

function formatAmount(value) {
  const amount = toNumber(value, 0);
  return String(Math.round(amount));
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

function generateRandomNumber(length) {
  let result = "";
  for (let i = 0; i < length; i++) {
    result += Math.floor(Math.random() * 10);
  }
  return result;
}

class Countdown {
  constructor(element, initialTime) {
    this.element = element;
    this.remaining = this.parseTime(initialTime);
    this.timer = setInterval(() => this.tick(), 1000);
  }

  parseTime(timeString) {
    const [hours, minutes, seconds] = timeString.split(":").map(Number);
    return hours * 3600 + minutes * 60 + seconds;
  }

  tick() {
    this.remaining -= 1;
    if (this.remaining <= 0) {
      this.element.textContent = "已结束";
      clearInterval(this.timer);
      return;
    }

    const h = Math.floor(this.remaining / 3600).toString().padStart(2, "0");
    const m = Math.floor((this.remaining % 3600) / 60).toString().padStart(2, "0");
    const s = (this.remaining % 60).toString().padStart(2, "0");
    this.element.textContent = `${h}:${m}:${s}`;
  }
}
