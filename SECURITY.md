# 安全策略（ContactBridge）

## 支持的版本

仅 0.1.x 公开测试版（当前 0.1.3）接受安全报告。

## 如何报告

- 优先使用本仓库 GitHub 的私密漏洞报告（Private vulnerability reporting）功能提交。
- 报告入口：[GitHub 私密漏洞报告](https://github.com/1321B54/xiaomi-google-contact-bridge/security/advisories/new)。若入口暂不可用，可以在公开 issue 中只请求私密联系方式，勿披露漏洞细节、真实联系人、账号、设备标识或日志。
- 不要在公开渠道发布可复现的隐私数据或凭据。

## 范围

- 应用自身代码与清单 / 构建脚本中可被远程或本地未授权利用的问题。
- 发布 APK 必须不可调试，签名私钥必须保持私有；不要将开发者密钥或密码上传到 Git。已授权 ADB、root 或设备已被攻陷的场景不属于新增的普通应用攻击面。
- 云服务提供方、OEM ROM、设备被 root 或已授权调试等场景，不在应用沙箱保证范围内。

## 我们不承诺

- 不提供漏洞赏金，不承诺修复时限或 SLA。
- 不发布或要求任何私密邮箱、令牌或密钥；维护者不会索要你的云账号密码。

## 加固基线（0.1.3）

- release 构建不允许 `--debug-mode`，显式设置 `debuggable=false` 与 `testOnly=false`。
- 发布密钥与密码由外部提供；脚本不生成发布密钥、不回退到调试密钥、不上传密钥。
- 应用无 INTERNET 权限，主界面 FLAG_SECURE，`allowBackup="false"`。
