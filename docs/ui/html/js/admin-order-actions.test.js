const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const vm = require("node:vm");

function loadAdminTestApi() {
  const source = fs.readFileSync(path.join(__dirname, "admin.js"), "utf8");
  const sandbox = {
    window: {
      location: {
        hostname: "localhost",
        port: "8091",
        origin: "http://localhost:8091"
      },
      addEventListener() {}
    },
    document: {
      cookie: "username=admin",
      addEventListener() {},
      getElementById() {
        return {
          querySelectorAll() {
            return [];
          }
        };
      }
    },
    console,
    confirm() {
      return true;
    },
    setTimeout() {},
    clearTimeout() {}
  };
  vm.runInNewContext(`${source}\n;globalThis.__testApi = { adminState, renderOrders, getSelectedOrdersOnCurrentPage };`, sandbox, { filename: "admin.js" });
  return sandbox.__testApi;
}

test("admin orders only expose cancel action for pending orders", () => {
  const { adminState, renderOrders } = loadAdminTestApi();
  adminState.orders = [
    { no: "wait-001", user: "u001", product: "10001", amount: 99, status: "待支付" },
    { no: "paid-001", user: "u002", product: "10002", amount: 199, status: "已支付" }
  ];
  adminState.orderFilteredTotal = 2;

  const html = renderOrders();

  assert.match(html, /data-action="delete-order" data-index="0"/);
  assert.doesNotMatch(html, /data-action="delete-order" data-index="1"/);
  assert.match(html, /不可取消/);
});

test("admin order batch selection ignores non-pending orders", () => {
  const { adminState, getSelectedOrdersOnCurrentPage } = loadAdminTestApi();
  adminState.orders = [
    { no: "wait-001", user: "u001", status: "待支付" },
    { no: "paid-001", user: "u002", status: "已支付" }
  ];
  adminState.orderSelectedKeys.add("u001::wait-001");
  adminState.orderSelectedKeys.add("u002::paid-001");

  assert.deepEqual(getSelectedOrdersOnCurrentPage().map((item) => item.no), ["wait-001"]);
});
