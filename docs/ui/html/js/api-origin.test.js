const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const vm = require("node:vm");

const files = ["admin.js", "index.js"];

function loadResolveApiOrigin(fileName, location) {
  const source = fs.readFileSync(path.join(__dirname, fileName), "utf8");
  const sandbox = {
    window: { location, addEventListener() {} },
    document: { addEventListener() {} },
    console,
    setTimeout() {},
    clearTimeout() {}
  };
  vm.runInNewContext(source, sandbox, { filename: fileName });
  return sandbox.resolveApiOrigin();
}

for (const fileName of files) {
  test(`${fileName} keeps API calls on localhost:8091 when served by backend`, () => {
    const origin = loadResolveApiOrigin(fileName, {
      hostname: "localhost",
      port: "8091",
      origin: "http://localhost:8091"
    });

    assert.equal(origin, "http://localhost:8091");
  });

  test(`${fileName} keeps API calls on 127.0.0.1:8091 when served by backend`, () => {
    const origin = loadResolveApiOrigin(fileName, {
      hostname: "127.0.0.1",
      port: "8091",
      origin: "http://127.0.0.1:8091"
    });

    assert.equal(origin, "http://127.0.0.1:8091");
  });
}
