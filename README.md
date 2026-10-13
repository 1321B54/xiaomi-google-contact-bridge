# ContactBridge / 通讯录桥

ContactBridge（中文名：通讯录桥）是一个独立的普通 Android 应用，用于在小米账号通讯录与 Google 账号通讯录之间做桥接。它不要求 root，不包含广告、分析或自建服务器。默认双向同步，可选“小米 -> Google”单向模式。

## English overview

ContactBridge is a standalone, non-root Android app that bridges contacts between a Xiaomi account and a Google account through Android's ContactsProvider. Existing account adapters handle cloud transfer, and iPhone uses native Google contacts sync. It supports two-way or Xiaomi -> Google synchronization, a first-run preview and background jobs. There is no INTERNET permission, analytics or custom backend. Version 0.1.3 is the first public beta; the Chinese sections below are the full reference.

## 目录

- 功能特性
- 系统要求
- 安装与发布产物
- 权限说明
- 账号准备
- iPhone 用户
- 首次同步
- 自动同步与后台
- 同步规则
- 隐私
- 构建
- 项目结构
- 测试
- 已知限制
- 常见问题（FAQ）
- 贡献
- 许可证

## 功能特性（0.1.3）

- 在所选小米账号通讯录与 Google 账号通讯录之间同步联系人。
- 默认双向同步；可在界面中选择“小米 -> Google”单向模式。
- 首次手动复制前提供预览，确认后才写入。
- 完成首次手动同步后，可以手动打开“自动同步”开关。
- 支持以下 10 种 MIME 类型：姓名（name）、电话（phone_v2）、邮箱（email_v2）、照片（photo）、昵称（nickname）、组织（organization）、备注（note）、地址（postal-address_v2）、网站（website）、事件（contact_event）。
- 冲突处理：按选择以一侧为准，覆盖全部受支持字段。
- 后台自动同步：联系人内容触发去抖 5 秒、最长 15 秒，并有至少 15 分钟的周期性回退。
- 账号修订指纹未变化时跳过完整读取。
- 界面按系统合并后的联系人显示总人数和“有号码 / 无号码”人数，实际写入操作另按账号记录统计。
- 主界面设置 FLAG_SECURE，减少截图与最近任务列表中的内容暴露。
- 应用私有文件中不保存联系人字段内容或照片，只保存账号对、原始记录 ID、来源 ID、内容哈希与待处理标记。

## 系统要求

- Android 11（API 30）或更高版本。
- 一个已登录的小米账号，以及一个已登录的 Google 账号。
- 无需 root。

## 安装与发布产物

- 发布路径由构建脚本定义：`outputs/apk/release/contact-bridge-0.1.3-release.apk`。
- 0.1.3 是首个公开测试版。前往 [GitHub Releases](https://github.com/1321B54/xiaomi-google-contact-bridge/releases) 下载 APK、SHA-256 校验文件和签名说明；版本详情见 [发布说明](docs/releases/v0.1.3.md)。
- 调试产物仅用于本地开发，位于 `app/build/outputs/apk/debug/contact-bridge-0.1.3-debug.apk`，不适合公开分发。

## 权限说明

- `READ_CONTACTS`：运行时权限，读取所选账号的联系人。
- `WRITE_CONTACTS`：运行时权限，写入所选账号。
- `READ_SYNC_SETTINGS`：读取账号同步适配器设置。
- `RECEIVE_BOOT_COMPLETED`：开机后重新安排后台任务。
- 应用不申请 `INTERNET`、位置、存储、通话记录、短信等权限。拒绝联系人权限会阻止同步。

## 账号准备

1. 在系统设置中登录并同步一个小米账号和一个 Google 账号。
2. 打开 ContactBridge，选择要桥接的两个账号。
3. 选择同步方向（双向或小米 -> Google）。
4. 建议为应用开启自启动 / 后台不受限（不同 OEM 名称不同）。
5. 在首次手动同步前先查看预览。

## iPhone 用户

本项目不提供 iOS 应用。iPhone 上使用系统自带的 Google 通讯录同步即可与同一个 Google 账号互通；无需在本应用中做额外设置。

## 首次同步

1. 选择账号与方向。
2. 查看预览，确认将要写入的联系人。
3. 确认同步预览中的联系人，查看结果，再按需打开“自动同步”。
4. 若预览后源/目标 provider 发生变化，断言会中止本次写入，需要重新预览。

## 自动同步与后台

- 联系人内容变化触发去抖 5 秒 / 最长 15 秒的调度，另有至少 15 分钟的周期性回退。
- 不承诺精确延迟或电池表现。
- 系统或 OEM 的强制停止、任务清理行为各不相同；重新打开应用会重新安排任务。
- 删除、缺失或身份替换会暂停对应联系人的配对；其他有效配对可以继续同步。0.1.3 还没有手动重新配对界面。
- 账号修订指纹未变化时会跳过完整读取。

## 同步规则

- 应用会故意把联系人写入两个所选账号本；随后由系统里已有的 Google/小米适配器上传，并下载云端变更。
- 本应用支持 10 种 MIME 类型；更新时保留目标记录上本应用不桥接的自定义字段。不同云服务可能另有字段限制。
- 首次配对依据电话号码或邮箱；同号多人、只有同名且没有可靠号码或邮箱时，暂停自动配对并保留记录。
- 冲突选择会覆盖全部受支持字段，包括目标端独有的字段。
- 小米端隐藏的无电话联系人仍会被桥接。
- 超过约 600 KB 的联系人负载会被跳过。
- 源/目标 provider 与本地配对文件无法组成单一分布式事务。

## 隐私

- 处理、存储、共享、删除、权限、安全屏幕与无网络范围的完整说明见 [PRIVACY.md](PRIVACY.md)。
- 应用私有文件中只有账号对、原始 ID、来源 ID、内容哈希与待处理标记；哈希是内容摘要，不是加密或匿名化。
- 没有自建服务器或分析 SDK；云传输由平台账号适配器完成。
- 已设置 `allowBackup="false"`；这不等于消除所有 OEM 换机迁移或 root 访问。

## 构建

直接脚本路径：

```powershell
# 调试构建（本地随机调试密钥，仅本地使用）
pwsh -File scripts/build.ps1 -SdkRoot '<SDK目录>' -JdkRoot '<JDK目录>' -BuildRoot 'C:\contact-bridge-build'

# 发布构建：必须提供外部发布密钥库、别名与密码环境变量
# 脚本不会生成发布密钥、不会回退到调试密钥、不会上传密钥
# 通过本机凭据工具给当前进程设置 CONTACT_BRIDGE_STORE_PASSWORD。
# 不要把实际密码写进脚本、聊天或命令历史。
pwsh -File scripts/build.ps1 -BuildType Release -SdkRoot '<SDK目录>' -JdkRoot '<JDK目录>' -BuildRoot 'C:\contact-bridge-build' -KeystorePath '<外部密钥库路径>' -KeyAlias '<别名>'
```

- 发布路径不会传 `--debug-mode`，会在打包清单中显式设置 `debuggable=false` 与 `testOnly=false`，并在缺少密钥 / 别名 / 密码时、创建任何构建产物前失败。
- 密码以 `env:VAR` 形式传给 apksigner，脚本只插值变量名，不回显或把密码写进命令行字符串。
- 调试构建使用本地随机生成的调试密钥库（`work/signing/debug.keystore`）与常规开发密码，仅用于本地。
- 已知构建基线：JDK 21（以 Java 17 编译）、SDK platform `android-37.0`、build-tools `37.0.0`、AGP `9.1.0`。
- Windows SDK 的 aapt2 对中文路径兼容不佳，建议为 BuildRoot 指定纯 ASCII 路径。
- 直接脚本构建 / 测试路径已在本地独立验证；Gradle 路径未验证，且没有 Gradle wrapper。

## 项目结构

```
app/src/main/java/local/contactbridge/   应用源码（模型、仓库、存储、计划器、任务等）
app/src/main/res/drawable/ic_bridge.xml  应用图标
app/src/main/AndroidManifest.xml         清单与权限
tests/                                   合成 Java 检查
scripts/build.ps1                        直接构建（Debug / Release）
scripts/test-core.ps1                    运行合成检查
app/build.gradle                         模块配置（0.1.3 / versionCode 4）
PRIVACY.md / SECURITY.md / LICENSE       隐私说明、安全策略与许可证
docs/releases/v0.1.3.md                  公开发布说明
```

## 测试

- 合成 Java 检查位于 `tests/`（CoreTests、EdgeTests、JournalTests、CountsTests），由 `scripts/test-core.ps1` 运行。
- 36 项合成 Java 检查覆盖新增、修改、冲突、缺失、模糊配对、并发编辑与联系人计数。它们不代替完整 Android 集成或长期耗电测试。
- 运行方式：`pwsh -File scripts/test-core.ps1 -JdkRoot '<JDK目录>'`，无需 Android SDK。
- 测试全部使用合成数据，不含真实联系人。

## 已知限制

- 不传播删除，不做字段级冲突合并。
- 不承诺全面的重复联系人清理。
- 不是即时云同步；不承诺保留原始分辨率照片。
- 超过约 600 KB 的联系人负载会被跳过。
- 删除 / 缺失 / 身份替换会暂停对应联系人的配对，暂无手动重新配对界面；不要通过清除应用数据修复此类问题。
- 不保证兼容所有 ROM/OEM；仅在有限设备测试中覆盖 Android 17 / HyperOS 4 环境。
- 无 iOS 应用；无通话记录同步。
- 应用沙箱内不承诺“零泄漏”；设备 root、设备被攻陷与云服务隐私策略不在保证范围内。

## 常见问题（FAQ）

**会删除联系人吗？**
不会自动传播整个人的删除。删除 / 缺失 / 身份替换会暂停对应配对；但正常更新和人工冲突处理会覆盖支持字段，也可能移除目标端独有的字段。

**为什么截图是黑的？**
主界面设置了 FLAG_SECURE，用于减少截图和最近任务列表暴露；这属于降低风险，不覆盖所有 OEM 投屏 / 拍摄方式。

**数据会上传到开发者服务器吗？**
不会。应用没有 INTERNET 权限，也没有自建后端；云同步由系统账号适配器完成。

**支持通话记录吗？**
不支持。本项目只同步通讯录，不同步通话记录。

**iPhone 能用吗？**
没有 iOS 应用。iPhone 使用系统自带的 Google 通讯录同步即可。

**为什么后台没有立刻同步？**
后台由联系人变化触发，并有周期性兜底检查；系统 / OEM 省电策略可能延后。建议允许后台自启动。设置里的“强行停止”会阻止任务，重新打开应用后才恢复；划掉最近任务后的行为由系统决定。

**为什么第一次要手动同步？**
首次复制会往另一账号写入联系人，所以先预览、确认，再由你决定是否打开自动同步。

**为什么有些联系人没有同步？**
可能是冲突、同号多人、只读或过大联系人，或者对应配对因删除 / 合并 / 身份变化而暂停。应用只桥接支持的字段；云服务何时上传和保留哪些字段，由原有同步服务决定。

## 贡献

- 欢迎提交 issue 与 pull request。
- 请勿在 issue、PR、日志或测试夹具中提交真实联系人数据、账号名、设备标识、密钥、令牌或密码；测试请使用合成数据。
- 提交前请使用 JDK 运行 `scripts/test-core.ps1`，并说明任何同步规则变更；构建 APK 才需要 Android SDK。
- 安全相关问题请按 [SECURITY.md](SECURITY.md) 私密报告，不要公开粘贴数据。

## 许可证

本项目使用 MIT 许可证，版权归 2026 ContactBridge contributors 所有，详见 [LICENSE](LICENSE)。
