# UI Structure (Mall + Admin)

This folder now follows "C-end mall main entry + admin management system".

## Files

- `docs/ui/html/index.html`: C-end mall page (goods detail, team-up, lock, pay, settlement)
- `docs/ui/html/admin.html`: Admin management page
- `docs/ui/html/css/admin.css`: Admin page style
- `docs/ui/html/js/admin.js`: Admin module rendering logic
- `docs/ui/html/css/tokens.css`: Shared design tokens

## Canonical Runtime Entry

- Canonical mall entry: `docs/ui/html/index.html`
- Canonical login entry: `docs/ui/html/login.html`
- Canonical admin entry: `docs/ui/html/admin.html`
- Legacy pages under `docs/dev-ops/nginx/html/` and `docs/tag/v1.0/nginx/html/` are deprecated and should not be used as runtime entries.

## Local Preview

Run from project root:

```bash
cd docs/ui/html
python3 -m http.server 8088
```

Open:

- `http://127.0.0.1:8088/index.html`
- `http://127.0.0.1:8088/login.html`
- `http://127.0.0.1:8088/admin.html`

## Admin Modules

- 商品管理
- 订单管理
- 营销管理
- 统计报表
