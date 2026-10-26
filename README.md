# 小米原生专注模式

一个为小米原生专注模式提供固定应用放行名单的 LSPosed 模块。保留原生计时、历史记录和应用拦截，只放行必要应用，不提供自由编辑名单或提前结束专注的入口。

[下载安装](https://github.com/PutFounder5297/XiaomiFocusMode/releases) · [使用说明](docs/USAGE.md) · [构建说明](docs/BUILDING.md) · [反馈问题](https://github.com/PutFounder5297/XiaomiFocusMode/issues)

## 适配范围

| 项目 | 要求 |
| --- | --- |
| 目标应用 | 小米设置 `com.xiaomi.misettings` |
| 目标版本 | `16.4.260829.00` / versionCode `260829400` |
| Android | 最低 Android 9（API 28）；不代表所有 ROM 均兼容 |
| 运行环境 | 已可用的 LSPosed 环境，作用域只选择「小米设置」 |
| 模块版本 | `0.1.23-test` / versionCode `24` |

模块仅定向适配上述小米设置版本。目标版本不匹配时，不安装核心专注 Hook。

## 功能

- 固定放行 57 个精确包名，名单见 [Policy.java](app/src/main/java/dev/lance/focusallowlist/Policy.java)，模块内可只读查看。
- 保留输入法、系统必要组件、微信/QQ 分身及原有密码、指纹验证流程。
- 支持 1–35791 分钟的自定义整数时长；重复进入沿用原生会话和起始时间。
- 提供深浅色模块界面、原生设置入口、超级岛倒计时和独立排障入口。
- 处理原生倒计时最近任务卡片，以及后台监控与临时锁定恢复。

超级岛不带操作按钮；后台保护不能保证抵抗强行停止或所有系统清理方式。主机测试不能替代手机功能验证，已知验证范围见 [归档记录](docs/ARCHIVE_STATUS.md)。

## 快速开始

1. 从 [Releases](https://github.com/PutFounder5297/XiaomiFocusMode/releases) 下载 APK，等待当前专注结束后安装。
2. 在 LSPosed 中启用模块，仅勾选「小米设置」，然后重启手机。
3. 打开模块，进入原生专注设置，设置时长并开始专注。

安装、升级、停用和常见问题见 [使用说明](docs/USAGE.md)。

## 开发

使用 Android Studio 打开仓库根目录，或使用 Gradle Wrapper：

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:verifyIsland
```

需要 JDK 17、Android SDK 35；超级岛 JSON 检查另需 Python 3。Windows 使用 `gradlew.bat`，Python 命令可通过 `-Ppython=python` 指定。完整配置和签名说明见 [构建说明](docs/BUILDING.md)。

```text
app/
  src/main/       Android Manifest、Java 源码、资源和 Xposed 入口
  src/test/java/  Java 主机测试
docs/             使用、构建与历史归档文档
tests/            超级岛 JSON 检查及可选 APK / ROM 静态核对
gradle/wrapper/   固定版本的 Gradle Wrapper
.github/          持续集成和反馈模板
```

欢迎通过 Issue 提交可复现问题，通过 Pull Request 提交改动。开发约束和验证要求见 [贡献指南](CONTRIBUTING.md)，版本记录见 [CHANGELOG](CHANGELOG.md)。

## 许可

本项目尚未选定开源许可证；公开源码不代表已授予开源许可。所用依赖遵循各自许可证。仓库不包含系统 APK/JAR、反编译产物、签名私钥或个人诊断原始日志。
