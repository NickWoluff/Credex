# Credex 自定义接口适配列表

Credex 的“自定义接口”按请求路径格式分类适配。填写根地址时，应填写服务提供的 API 根地址，而不是登录页、余额接口或控制台页面。应用会依据根地址选择固定分类；对于尚未登记的根地址，则按预设顺序尝试不同分类。

## 已适配的 API 根地址

以下根地址应与表格完全一致。末级路径属于根地址的一部分，不能省略或自行替换。

| API 根地址 | 接口分类 | 登录请求 | 余额请求 |
| --- | --- | --- | --- |
| `https://ai.091233.xyz/api/v1` | 标准邮箱密码接口 | `POST /auth/login` | `GET /auth/me`，兼容 `GET /user/profile` |
| `https://zhiyaoapi.top` | 用户名密码接口 | `POST /user/login` | `GET /user/me?include_statistics=false` |
| `https://yujianwudi.top` | New API 接口 | `POST /api/user/login` | `GET /api/user/self` |

标准邮箱密码接口的请求体使用 `email` 和 `password`；用户名密码接口使用 `username` 和 `password`。应用目前可从 `balance`、`available_balance`、`fixed_balance` 或 `fixedBalance` 字段读取余额。

## 未知根地址的尝试顺序

未登记在清单中的 HTTPS 根地址会依次尝试：

1. 标准邮箱密码接口：`/auth/login`、`/auth/refresh`、`/auth/me`，并兼容 `/user/profile`
2. 用户名密码接口：`/user/login`、`/user/me?include_statistics=false`
3. New API 接口：`/api/user/login`、`/api/user/self`、`/api/status`；站点启用密码加密时会读取 `/api/user/login/encryption-key` 并使用 RSA-OAEP（SHA-256）加密密码

只有当登录响应包含有效令牌，并且余额接口返回可解析的余额字段时，应用才会将该分类记录为已连接。之后刷新时会继续使用已命中的分类，不会重复探测。

如果服务端明确返回 `401`、`403`、用户名/密码错误或账号不可用，应用会优先提示检查账号和密码，不再误报为 API 根地址错误，也不会显示适配申请入口。

如果所有分类都失败，请先检查：

- API 根地址是否包含正确的 `/api/v1` 等末级路径；
- 是否误填了 `/user/login`、`/user/profile` 等具体接口；
- 账号和密码是否有效；
- 根地址是否支持 HTTPS。

确认以上信息无误后，可在应用内点击“前往 GitHub 发布适配 Issue”，或在 [Credex Issues](https://github.com/NickWoluff/Credex/issues/new) 提交适配申请。请勿在 Issue 中填写密码、访问令牌或其他敏感凭据。

## 维护规则

- 新中转站的接口格式如果与现有分类一致，只需在 `CustomEndpointCatalog.knownRoots` 中登记根地址和分类。
- 只有请求路径、认证字段或余额结构确实不同时，才新增分类和请求实现。
- 新增根地址后，同步更新本清单及 `CustomEndpointAdapterTest`。
