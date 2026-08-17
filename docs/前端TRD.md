# 水电表抄表管理 App — 前端技术设计文档 (前端 TRD)

| 项目 | 内容 |
|------|------|
| 文档版本 | v0.1 (初稿) |
| 创建日期 | 2026-08-13 |
| 客户端形态 | 移动 Web / PWA(React + Vite + TypeScript + Tailwind) |
| 部署方式 | 同源部署(前端 build 产物由后端 / nginx 静态托管,无跨域) |
| 关联文档 | [PRD.md](./PRD.md)、[TRD.md](./TRD.md)(后端)、[注册流程说明.md](./注册流程说明.md) |
| 状态 | 待评审 |

> **形态变更说明**:PRD 原定「iOS / Android 原生双端」。因开发机无 Xcode / Android SDK,原生双端无法编译与端到端验证,且与「起步自用、控制成本」的目标冲突,故客户端改为**移动 Web / PWA**:一套代码,浏览器「添加到主屏」即可当 App 用。原生双端作为后续规划保留。

---

## 1. 技术选型与依赖

后端已完成(Spring Boot 3.2.5 + Java 17 + MySQL,详见 [TRD.md](./TRD.md)),本文档只覆盖前端。前端严格对接后端既有 REST 契约,不要求后端为前端改接口(唯一例外见 §3 同源托管的 SecurityConfig 放行)。

### 1.1 选型决策表

| 关注点 | 选型 | 理由 |
|--------|------|------|
| 框架 / 构建 | **React 18 + Vite 5 + TypeScript** | 生态成熟、资料多;Vite dev proxy 天然解决联调跨域 |
| 样式 | **Tailwind CSS 3**,手搭,不引组件库 | 移动端单栏布局用 utility class 足够;包体最小、完全可控 |
| 路由 | **react-router-dom v6** | SPA 标准,支持嵌套路由与守卫 |
| 服务端状态 | **TanStack Query (react-query)** | 列表 / 详情 / 汇总全是「取数 + 缓存 + 失效」,免写 loading/error/重试样板;天然承载 id→name 缓存与 mutation 后失效 |
| 客户端状态 | **Zustand** | auth 会话、离线队列 UI 状态等轻量全局态;比 Context 无 re-render 放大 |
| 请求库 | **axios** | 拦截器承载 Bearer 注入、`{code}` 解包、401 单飞 refresh 重放、blob 分支 |
| 离线存储 | **idb**(IndexedDB 轻封装) | 存未同步读数队列;比 localStorage 适合结构化数据 |
| 幂等键 | `crypto.randomUUID()` | 免依赖(https / localhost 安全上下文可用) |
| PWA | **vite-plugin-pwa**(Workbox) | 自动生成 manifest + service worker、precache app shell、autoUpdate |
| 图表(二期) | **recharts** | React 友好、体量适中,对接 `/reports/charts` 聚合 JSON |

### 1.2 状态方案说明

采用 **Zustand(客户端态)+ TanStack Query(服务端态)** 组合,不用裸 Context。理由:Context 无缓存、无请求去重 / 失效,几十个列表页会退化成手写 `useState + useEffect + loading/error`;react-query 一次性解决取数生命周期。

### 1.3 package.json 核心依赖

- **dependencies**:`react`、`react-dom`、`react-router-dom`、`axios`、`@tanstack/react-query`、`zustand`、`idb`、`clsx`(可选)
- **devDependencies**:`vite`、`@vitejs/plugin-react`、`typescript`、`tailwindcss`、`postcss`、`autoprefixer`、`vite-plugin-pwa`、`@types/react`、`@types/react-dom`、`eslint` + `prettier`(可选)
- **二期新增**:`recharts`

---

## 2. 工程结构与架构

前端新建于仓库 `frontend/` 目录,与 `backend/` 平级。

```
frontend/src/
  main.tsx / App.tsx          # 入口 + QueryClientProvider + RouterProvider
  config/                     # env、API base('/api/v1')、常量、枚举字典
  api/
    client.ts                 # axios 实例 + 拦截器(核心)
    auth.ts ledger.ts reading.ts report.ts org.ts reader.ts export.ts
  types/
    dto.ts                    # 镜像后端 record 的 TS interface
    error.ts                  # ErrorCode 枚举 + ApiError 类
  hooks/                      # react-query 封装:useCompanies/useMeters/
                              # usePeriods/useTasks/useReadings/useSummary/
                              # useDunning + 各 mutation
  store/
    auth.ts                   # Zustand:token/role/orgId + login/logout
    offlineQueue.ts           # 离线队列状态 + 待同步计数
  offline/
    db.ts                     # idb schema(pendingReadings 表)
    sync.ts                   # 排空队列 → /readings/batch
  components/
    ui/                       # Button/Input/Modal/Toast/Spinner/
                              # EmptyState/ErrorState/Badge
    layout/                   # AppShell/TabBar(底部导航)/TopBar
    guards/                   # RequireAuth / RequireRole
  pages/
    auth/                     # Login / Register
    ledger/                   # CompanyList / MeterList
    period/                   # PeriodList / PeriodForm
    reading/                  # PeriodPick / TaskList / EntryForm
    audit/                    # AuditList
    report/                   # Summary / Dunning
  routes/index.tsx            # 路由表 + 守卫装配
  utils/
    format.ts                 # 数字 / 日期 / 金额格式化
    download.ts               # blob 下载
    idToName.ts               # id→name 映射
```

**分层数据流**:`pages` 只组合 UI 与 hooks → `hooks`(react-query)封装取数与失效 → `api/*` 调 `client.ts` → 拦截器统一解包 / 鉴权 / 错误。auth 与离线队列走 Zustand,横切所有页面。

**id→name 解析**(重要,见 §4.4):后端多数列表 / 汇总 DTO **只返回 ID 不返回名称**(`TaskItem` 有 `companyId` 无 `companyName`,`ReadingResponse` 只有 `meterId`,`SummaryResponse.Row` / `DunningResponse.Row` 只有 `companyId`)。前端需常驻两个 query(`/companies`、`/meters`)构建 `id→name` 映射,供所有列表 / 汇总 / 审核页渲染名称。这是贯穿性需求,集中在 `utils/idToName` + react-query 缓存。

---

## 3. 同源部署方案(dev proxy + prod 托管)

后端现状已确认:**无 CORS 配置、无静态资源托管、无 `resources/static` 目录**,`SecurityConfig` 以 `.anyRequest().authenticated()` 收尾。这决定了跨域与托管方式。

### 3.1 开发期 —— Vite dev proxy

`vite.config.ts`:

```ts
server: {
  proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: true } }
}
base: '/'
```

浏览器视角同源,无 CORS,**后端无需加 CORS**(现状无 CORS 正好不用动)。

### 3.2 生产期 —— 两条托管路径

#### 路径 A:Spring Boot 静态托管(单 jar 自用)

`npm run build` 产物打进后端 jar,单产物部署。需要**后端三处改动**:

1. **Vite** `build.outDir` 指向 `../backend/src/main/resources/static`,`emptyOutDir: true`;该目录加入 `backend/.gitignore`(生成物不入库)。
2. **新增 `WebMvcConfigurer`(SPA fallback)**:`addResourceHandlers` 挂 `classpath:/static/`,自定义 `PathResourceResolver.getResource` —— 资源存在则返回,否则当「路径不以 `api/` 开头且无文件后缀」时回退 `index.html`,保证深链刷新可用,且**不拦截 `/api/**` 与 `/actuator/**`**。
3. **改 `SecurityConfig`(关键,否则整站 401)**:在 `.anyRequest().authenticated()` 之前放行 SPA 壳与静态资源:
   ```java
   .requestMatchers(HttpMethod.GET, "/", "/index.html", "/assets/**",
       "/icons/**", "/*.js", "/*.css", "/*.png", "/*.svg", "/*.ico",
       "/*.webmanifest", "/manifest.webmanifest", "/sw.js", "/registerSW.js").permitAll()
   ```
   > 原因:当前 `anyRequest().authenticated()` 会把 `/index.html` 与所有前端路由挡成 `{"code":2001}`(返回 JSON 而非 HTML),SPA 根本加载不出来。**这是路径 A 最容易漏、最先踩的联调阻塞点。**

#### 路径 B:利用现有 nginx(推荐现网 docker 部署)

`deploy/nginx/conf.d/meter.conf` 已反代 `/api/`、托管 `/uploads/`。只需在 `server{443}` 增加:

```nginx
root /usr/share/nginx/html;
location / { try_files $uri /index.html; }
```

并把 SPA 产物挂进 nginx 容器。**后端零改动、`SecurityConfig` 不用动**,`/api` 与静态资源物理分离最干净。

### 3.3 推荐

- **现网 docker 部署 → 路径 B**(与现有 nginx 架构一致、后端不动)。
- **本地「一个 jar 跑起来自用」/ 无 nginx 环境 → 路径 A**。

两者都需 `base:'/'` 与 PWA `scope:'/'`。

---

## 4. 鉴权与请求层

### 4.1 Token 存储

用 **localStorage**(不用 sessionStorage)。理由:PWA「添加到主屏」当 App 用,sessionStorage 关闭即失;refresh TTL 7d、单人自用,localStorage 持久登录体验最好。存 `accessToken` + `refreshToken` + `{userId, orgId, role}`。风险(XSS 读取)缓解:无 `dangerouslySetInnerHTML`、同源、输入转义。后端用 Bearer header 而非 cookie,故不做 httpOnly cookie(改造成本超范围)。

### 4.2 请求拦截器

注入 `Authorization: Bearer <accessToken>`。免登录端点(register / login / refresh / sms-code / ping)不依赖 token。

### 4.3 响应拦截器(统一解包 + 错误漏斗)

后端契约:**成功 = HTTP 2xx 且 `body.code === 0`;业务错误 = HTTP 200 但 `code ≠ 0`(axios 不会抛)**;校验 / 鉴权 / 服务器错才是非 2xx。

- **成功分支(2xx)**:若 `responseType === 'blob'` → 直接放行(见 §7 导出);否则读 `body.code`,`0` 返回 `body.data`,非 `0` 抛 `ApiError(code, message)`。
- **错误分支(非 2xx)**:从 `err.response.data` 取 `{code, message}` 组 `ApiError`;无 body 按网络错误处理(交给离线队列)。

两条路径都汇入统一的 `ApiError`,页面只需 catch 一种错误类型。

### 4.4 401 自动 refresh + 重放

- **触发条件 = HTTP 401**(不区分内层 code)。**关键**:access token 过期在后端 `JwtAuthFilter` 里被静默忽略,最终由 entry point 返回 **HTTP 401 + code 2001**(不是 2002);`2002` 只在 `/auth/refresh` 端点(refresh token 过期)出现。故前端 refresh 触发以 **HTTP 401** 为准,不能判 `code === 2002`。
- **单飞(single-flight)**:并发 401 只发一次 `/auth/refresh`,其余挂起等结果;成功 → 写回**新的 access + refresh**(后端滚动发放,两个都要存)→ 重放原请求一次(带重试标记防死循环)。
- refresh 失败 / 再次 401 / refresh 返回 2002 → 清 token → 跳 `/login`(记录 returnTo)。

### 4.5 错误码 → UI 语义映射

| code | 语义 | 前端动作 |
|------|------|----------|
| 0 | 成功 | 返回 data |
| 1001 | 参数非法 | 表单级红字 / toast |
| 1002 | 短信码无效 | 验证码输入框报错 |
| 2001 | 未登录 | 触发 refresh 或跳登录 |
| 2002 | token 过期 | (refresh 端点)跳登录 |
| 2003 | 无权限 | toast「无权限」,**不跳登录** |
| 2004 | 跨组织 | toast「越权访问」 |
| 3001 | 不存在 | 空态 / toast,刷新列表 |
| 3002 | 冲突(重名 / 已注册 / 有表计禁删 / 已结算) | 就地表单红字 |
| 3003 | 邀请无效 | 邀请码输入框报错(二期) |
| 4001 | 导入解析失败 | 导入结果面板(二期) |
| 4002 | 读数异常 | 一般走 200 + isAbnormal,少见 |
| 4003 | 周期不可结算 | toast 说明(有未审 / 缺费率) |
| 5000 | 服务器错误 | 全局错误 toast + 重试 |

---

## 5. 路由与权限守卫

**守卫**:`RequireAuth`(无 token → `/login`);`RequireRole roles={[...]}`(角色不符 → 403 页或首页 toast)。角色取自 auth store 的 `role`,以 `GET /auth/me` 校准。

### 5.1 页面 ↔ 路由 ↔ 角色 ↔ API 映射(首版核心闭环)

| 页面 | 路由 | 登录态 / 角色 | 主要 API |
|------|------|--------------|----------|
| 登录 | `/login` | 公开 | POST `/auth/login` |
| 注册 | `/register` | 公开 | POST `/auth/sms-code`、`/auth/register` |
| 首页 / 入口 | `/` | 登录·全角色 | GET `/auth/me`、`/periods` |
| 公司台账 | `/ledger/companies` | ADMIN 增删改;VIEWER 只读 | GET/POST/PUT/DELETE `/companies` |
| 表计台账 | `/ledger/meters` | ADMIN 增删改;VIEWER 只读 | GET/POST/PUT/DELETE `/meters`、GET `/companies` |
| 周期管理 | `/periods` | 列表全角色;增删改 + 结算 ADMIN | GET/POST/PUT `/periods`、POST `/periods/{id}/settle` |
| 抄表-选周期 | `/reading` | ADMIN·READER | GET `/periods` |
| 抄表-待抄清单 | `/reading/:periodId/tasks` | ADMIN·READER | GET `/periods/{id}/tasks` |
| 抄表-录入 | `/reading/:periodId/meter/:meterId` | ADMIN·READER | POST `/readings`(离线走队列 → `/readings/batch`)、PUT `/readings/{id}` |
| 审核 | `/audit/:periodId` | **ADMIN only** | GET `/readings?periodId=&auditStatus=1`、POST `/readings/{id}/audit`、`/readings/audit/batch` |
| 汇总 | `/report/summary/:periodId` | **ADMIN·VIEWER** | GET `/reports/summary?periodId=` |
| 催单 | `/report/dunning/:periodId` | **ADMIN·VIEWER** | GET `/dunning/{periodId}`、导出 blob |

### 5.2 关键权限约束(源自后端语义,务必遵守)

- **READER 的「我的表」必须走 `/periods/{id}/tasks`,不能用 `/meters` 列表** —— 后端 `/meters` 与 `/companies` **不做 READER 过滤**(返回全组织),只有 tasks / readings 对 READER 按分配公司过滤;READER 无分配 → 空清单。
- **READER 看不到汇总 / 催单**(`/reports/summary`、`/dunning` 是 ADMIN/VIEWER 专属,READER 调用得 403)。导航栏对 READER 隐藏这些入口。
- **VIEWER 不能录入**(`/readings` POST 仅 ADMIN/READER),汇总催单可看可导出。

---

## 6. 各核心页面详设

每页统一含:数据来源 API、关键交互、loading / 空 / 错误三态(用 `ui/Spinner`、`ui/EmptyState`、`ui/ErrorState` 组件,由 react-query 的 `isLoading / isError / data.length===0` 驱动)。

- **登录 `/login`**:POST `/auth/login`;失败统一提示「手机号或密码错误」(后端把两者合并为 2001 防枚举)。成功存 token + 身份跳首页。

- **注册 `/register`**:先 POST `/auth/sms-code`(骨架阶段响应直接回明文 `code`,可自动填充便于自用;`app.sms.enabled=false` 时校验关闭,`smsCode` 可省)→ POST `/auth/register{phone, password, orgName}`。手机号已注册 → 3002 就地报错。**注册者恒为新组织 ADMIN**,成功直接进应用。

- **公司台账 `/ledger/companies`**:GET `/companies` 列表;ADMIN 可增删改(删有表计的公司 → 3002「该公司下有表计」;同名 → 3002)。空态「还没有公司,先建一个」。

- **表计台账 `/ledger/meters`**:GET `/meters?companyId=&type=&status=` 三筛选;表单字段 `companyId, name, type(1电2水), initialReading(≥0), ratio(>0), location, status(1启用0停用)`。列表用 companies map 显示公司名。

- **周期管理 `/periods`**:列表显示状态(1 进行中 / 2 已结算);ADMIN 建周期(可带 `elecPrice / waterPrice`,`endDate < startDate` → 1001);**结算**按钮 → POST settle,前置校验失败 4003 时 toast 说明(有未通过读数 / 有电表读数但没填电价 / 有水表读数但没填水价);**已结算周期锁定编辑与录入**(UI 置灰)。

- **抄表录入 `/reading/:periodId/meter/:meterId`(核心)**:流程 = 选周期 → tasks 清单 → 逐表录入。清单项显示 `done` 状态与 `auditStatus` 徽标。
  - **实时倒退提示(best-effort)**:后端 `TaskItem` **不含 `prevReading`**,`/meters` 只有 `initialReading`。故未抄表计无真实上期读数可比。策略:编辑既有读数时用 `ReadingResponse.prevReading`;否则回退 `meter.initialReading` 作近似下界;`currReading < prev` → 红字「疑似倒退」。**最终异常判定以后端返回的 `isAbnormal` / `abnormalType`(BACKWARD / SPIKE)为准**,提交后展示标记。
  - 每条提交生成 `clientUuid`。已结算周期 3002、范围外公司(READER)2003 → 对应提示。
  - *可选后端增强(nice-to-have,非首版必须)*:给 `TaskItem` 补 `prevReading` 以获得更准的实时提示。

- **审核 `/audit/:periodId`(ADMIN)**:GET `/readings?periodId=&auditStatus=1` 待审列表(用 meters map 显示表名);单条 `/readings/{id}/audit{approved, remark}`;多选批量 `/readings/audit/batch{ids, approved, remark}`(他组织 id 静默忽略,返回 `processed` 计数)。**驳回必填 remark**。

- **汇总 `/report/summary/:periodId`(ADMIN/VIEWER)**:GET `/reports/summary`;每公司每类型一行,`fee === null` 显示「未定价」;顶部显示 `pendingCount`(> 0 提示「仍有 N 条待审,未计入」)。用 companies map 显示公司名。

- **催单 `/report/dunning/:periodId`(ADMIN/VIEWER)**:GET `/dunning/{periodId}` 每公司一行(电量 / 电费 / 水量 / 水费 / 合计);导出按钮走 blob(见 §7)。

---

## 7. 离线抄表、导出与拍照降级

### 7.1 离线抄表与同步(首版:轻量「在线优先 + 失败入队重试」)

首版**不做完整离线优先**。理由:后端已提供 `/readings/batch`(≤500 条,逐条独立事务)+ `clientUuid` 幂等,轻量队列即可复用;「起步自用」场景做完整离线(预缓存全部 tasks、冲突合并 UI)投入产出比低。

机制:
1. 每条读数提交前生成 `clientUuid` → 先在线 POST `/readings`。
2. **仅网络错误**(非业务 code ≠ 0)才写入 IndexedDB `pendingReadings`(idb)→ 顶部显示「待同步 N 条」。
3. 恢复网络自动 / 手动「立即同步」→ 调 `/readings/batch` 排空。`clientUuid` 保证即使上次请求实际成功但响应丢失也不会重复(数据库 `UNIQUE(org_id, client_uuid)` 兜底)。
4. 同步结果按 `BatchResult{successCount, failCount, errors}` 展示失败明细。

二期升级:预缓存 tasks 支持完全离线浏览、冲突可视化。

### 7.2 导出(blob 下载)

`api/export.ts` 用 `responseType: 'blob'` 调 `/export/*`、`/dunning/{}/company/{}/export`;拦截器对 blob 放行,但若响应 `content-type` 含 `json` 则说明是错误 → 解析出 `ApiError`。`utils/download.ts` 从 `Content-Disposition` 取文件名(`filename*=UTF-8''...`)、`URL.createObjectURL` + 触发 `<a download>`。**首版只接催单导出**,台账 / 读数导出二期。

### 7.3 拍照降级(首版:占位 / 不做)

后端**无图片上传端点**,且 `ReadingResponse` **不回传 `photoUrl`**。即使 `ReadingRequest` 接受 `photoUrl` 字符串,也无处上传取 URL、无法回显。首版拍照按钮**留占位并禁用**(或不放),避免误导。二期补「上传端点 + 对象存储 + `ReadingResponse` 回传 `photoUrl`」后再做。

---

## 8. PWA 配置

- **vite-plugin-pwa**,`registerType: 'autoUpdate'`,`scope: '/'`,`base: '/'`。
- **manifest**:`name / short_name`、`display: 'standalone'`、`start_url: '/'`、`theme_color / background_color`、`icons`(192 + 512 + maskable);iOS 另需 `apple-touch-icon` 与 apple meta。
- **Service Worker 缓存策略**:
  - App shell(`index.html`、`/assets/*.js|css`、图标)→ **precache / CacheFirst**。
  - **`/api/**` → NetworkOnly(不缓存)**,保证 tasks / readings / 汇总数据实时正确;离线写靠 §7.1 队列,不靠 SW 缓存写。
  - 导航请求 → SPA fallback 到 `index.html`,但**排除 `/api`**。
- **添加到主屏**:manifest + SW 即可触发 Android `beforeinstallprompt`;iOS Safari 无此事件,需手动「添加到主屏」,文档给操作说明与 iOS 限制备注(存储上限、无安装提示)。

---

## 9. 非功能、边界与二期规划

### 9.1 非功能

- 首屏与列表加载 < 2s(呼应后端 5000 表计目标);移动端单手操作,核心录入 ≤ 3 次点击;错误可恢复(统一 toast + 重试)。
- 兼容性:iOS Safari 14+ / Android Chrome(PWA),桌面浏览器可用。

### 9.2 本期边界(占位或不做)

Excel 导入 / 导出(仅催单导出先接)、成员邀请与角色管理、抄表员公司分配 UI、图表、多组织切换(后端 join 后需重登,首版按**单组织**处理)、拍照上传。

### 9.3 二期规划

- `dataimport` 模板配置 + 导入结果面板;
- `org` 邀请 / 成员、`reader` 公司分配管理页;
- `recharts` 对接 `/reports/charts`(trend 必带 `companyId`,ratio / elec-water 必带 `periodId`);
- 完整离线优先;拍照上传(依赖后端上传端点)。

### 9.4 后端联调阻塞项小结

1. **路径 A 必须改 `SecurityConfig`** 放行静态资源与 SPA fallback,否则整站 401(最易漏)。
2. **路径 A 需新增 `WebMvcConfigurer`** 做 SPA deep-link fallback,且不拦 `/api`、`/actuator`。
3. **路径 B**(nginx `try_files $uri /index.html`)后端零改动 —— 推荐现网。
4. 前端 refresh 触发以 **HTTP 401** 为准(access 过期后端表现为 code 2001 而非 2002)。

### 9.5 变更记录

| 版本 | 日期 | 变更 |
|------|------|------|
| v0.1 | 2026-08-13 | 前端初稿:客户端形态由原生双端改为移动 Web/PWA;确定 React+Vite+TS+Tailwind、同源部署、首版核心闭环范围 |
