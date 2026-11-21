# 氢（H browser）

一个用 **Java + Material 3 Expressive** 编写的轻量 Android 浏览器。系统 WebView 内核，无 native 库、无遥测、无私有云依赖。

- 包名：`com.h.browser`
- 版本：0.2.0
- `minSdk 19` / `targetSdk 36` / `compileSdk 36`
- 纯 Java，单一 APK 支持 armeabi-v7a / arm64-v8a / x86 / x86_64

## 功能

| 模块 | 说明 |
|---|---|
| 多标签 | 最多 12 个标签，标签切换面板，无痕标签 |
| 广告拦截 | hosts 规则 + 元素隐藏 + `@@` 例外；默认订阅 EasyList China，可换任意订阅地址 |
| 用户脚本 | 解析 `==UserScript==` 元数据，提供 `GM_setValue/getValue/deleteValue/addStyle/xmlhttpRequest` |
| 阅读模式 | 注入正文提取脚本，独立阅读界面，字号与背景可切换 |
| AI 助手 | OpenAI 兼容 `/chat/completions`，SSE 流式输出，可携带当前页面正文 |
| 翻译 | LibreTranslate 风格 POST 或含 `%s` 的 GET 模板 |
| 朗读 | 系统 TextToSpeech + 前台服务，通知栏可停止 |
| 媒体嗅探 | 捕获页面中的 mp4/m3u8/mp3 等地址，支持复制与打开 |
| 下载 | 系统 DownloadManager，下载列表可查看进度、打开、删除 |
| PDF | `PdfRenderer` 内置逐页渲染，低版本回退系统应用 |
| 扫码 | Camera1 + ZXing core，识别二维码/条形码并打开 |
| 密码库 | AES-GCM + Android Keystore，填充时可用生物识别解锁 |
| WebDAV | 手写 Basic Auth + PUT/GET，备份/合并书签、历史与首页设置 |
| 内部页 | `v://home`、`v://bookmarks`、`v://history`、`v://log`、`v://skins`、`v://about` |
| 外观 | MD3 氢青配色、明暗主题、edge-to-edge |
| 主页皮肤 | 内置三套原创皮肤；兼容 Via 的 HTML + CSS 主题包（JSON 地址或 .zip 导入），`window.open` 自动打开新标签 |
| 皮肤中心 | `v://skins`：外观设置、皮肤切换/删除、在线索引「发现」页（默认 BetterVia 索引，按需下载，不内置第三方资源） |
| 莫奈取色 | Android 12+：跟随壁纸、从当前皮肤预览取色、自定义 `#RRGGBB`、关闭回落氢青；小组件同步刷新 |
| 工具栏 | 后退/前进/主页/刷新/标签/新标签/菜单 自由排序与显隐，菜单固定保留 |
| 快捷交互 | 左右边缘滑动前进/后退，下拉刷新，地址栏一键清空 |
| 页内查找 | 页内高亮、上一处/下一处、命中计数 |
| 网页字号 | 80%–200% 缩放，实时应用并持久化 |
| 数据迁移 | Netscape HTML 书签导出/导入，HTML 历史导出（SAF，无存储权限） |
| 小组件 | 搜索胶囊与「搜索（透明）」两种，点击进入输入；主题变更时刷新 |
| 外部入口 | 处理 http/https VIEW、WEB_SEARCH、自定义 Tab |

## 构建

环境：WSL/Linux，JDK 21，Gradle 8.13，Android SDK Platform 36、Build-Tools 36。

```bash
# sdk.dir 指向本地 Android SDK
echo "sdk.dir=/opt/android-sdk" > local.properties

/opt/gradle-8.13/bin/gradle --no-daemon assembleRelease
```

如果根目录存在 `keystore.properties`，Release 会自动签名：

```properties
storeFile=h-release.jks
storePassword=***
keyAlias=hbrowser
keyPassword=***
```

`keystore.properties` 与 `*.jks` 已在 `.gitignore` 中，不会进入仓库。

## 测试

- 模拟器：MuMu 15（Android 15，x86_64），`adb connect 127.0.0.1:16384`。
- 冒烟：冷启动 → 打开站点 → 多标签 → 拦截计数 → 用户脚本 → 阅读模式 → 朗读 → 下载 → PDF → 扫码 → 密码库 → WebDAV → 明暗/旋转。
- 说明：扫码需要摄像头；模拟器无摄像头时会提示并退出。AI/翻译/WebDAV 需要用户自备服务，仓库不包含任何密钥。

## 隐私与权限

- 无统计、无遥测、无设备指纹上报。
- INTERNET、ACCESS_NETWORK_STATE、ACCESS_WIFI_STATE：浏览与网络状态。
- CAMERA：仅扫码时使用。
- POST_NOTIFICATIONS / FOREGROUND_SERVICE*：朗读通知。
- WRITE_EXTERNAL_STORAGE（≤ API 28）：保存下载文件。
- USE_BIOMETRIC / USE_FINGERPRINT：密码库解锁。
- REQUEST_INSTALL_PACKAGES：允许下载 APK 后由系统安装器处理（不静默安装）。

AI Key、翻译 Key、WebDAV 密码只保存在本机 SharedPreferences；密码库使用 Android Keystore 密钥加密。API 19–22 无 Keystore AES 时退化为本地 Base64 存储，界面会提示设备较旧。

## 体积策略

- 使用系统 WebView，不打包浏览器内核。
- 无 Kotlin、无 OkHttp/Gson/RxJava，网络使用 `HttpURLConnection` 与内置 `org.json`。
- UI 全部代码构建，内部页面运行时生成 HTML。
- 无内置字体/图片，图标为 24dp 矢量；启动图标另有 PNG 回退。
- R8 full mode + resource shrinking + 非传递 R 类。

## 主题与皮肤

- 内置皮肤与全部代码均为原创，不打包 Via 或第三方主题文件。
- 「发现」页只读取你配置的索引 JSON（默认 `JiGuroLGC/BetterVia` 的 `themes_new.json`），点击安装时才从主题作者的 CDN 下载；主题版权归原作者，请在遵守其许可的前提下使用。
- 皮肤以 `files/skins/<id>/` 保存，通过 `WebViewAssetLoader` 以 `https://appassets.androidplatform.net/skin/...` 加载，支持相对资源与 localStorage。
- 导入失败或主题包含不支持的 Via 专有能力时，仅保留 HTML/CSS 与 `window.open` 跳转。

## 已知限制

- API 21+ 可区分主框架，hosts 拦截仅作用于子资源；API 19–20 的旧 WebView 回调无法区分，阻断规则对全部请求生效，极少见情况下可能拦截整页（元素隐藏不受影响）。
- 阅读模式为启发式提取，复杂站点可能需要手动选择。
- 用户脚本不支持 `@require` 与完整 GM API。
- 密码库自动填充仅由长按菜单触发，不提供无障碍常驻服务。
- 未在 API 19 真机、ARM 设备上实机验证；代码路径无 native 依赖。
- 系统手势导航会优先接管屏幕边缘滑动，此时请使用工具栏后退/前进。
- 莫奈取色在 Android 12 以下不生效（保持氢青经典配色）。

## 致谢与许可

- 源代码：Apache License 2.0，见 [LICENSE](LICENSE)。
- 默认广告订阅为 [EasyList China](https://easylist-downloads.adblockplus.org/easylistchina.txt)，由社区维护，采用 CC BY-SA 3.0；本仓库不内置其规则文件。
- 图标为自绘 Material 风格矢量，不包含上游浏览器的专有资源。
- 在线皮肤索引默认指向 [BetterVia](https://github.com/JiGuroLGC/BetterVia) 社区目录，仅作为可配置的发现入口；主题文件不在本仓库分发。
- 本项目与 Via 浏览器无关联，仅参考公开的功能形态独立实现。
