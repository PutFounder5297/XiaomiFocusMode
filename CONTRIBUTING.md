# 贡献指南

请先查看 [构建说明](docs/BUILDING.md)。使用 Issue 报告问题，或提交范围明确的 Pull Request。

## 报告问题

提供模块版本、小米设置的版本与 versionCode、设备和 ROM 版本、LSPosed 版本与作用域，以及可复现步骤、预期和实际行为。设备日志只提交已脱敏的相关片段；不要提交系统 APK/JAR、私钥或个人完整日志。

## 修改与验证

- 目标版本不匹配时应继续禁用核心专注 Hook，避免扩大适配范围而没有对应验证。
- 保留原生计时、历史、应用锁和分身验证流程，不添加提前结束专注或绕过验证的入口。
- 更改固定应用名单时，同步维护 `Policy.java`、`AppCatalog.java` 和 Manifest 的 `queries`。
- 不压缩或混淆 Hook 类、动态资源和入口；涉及此类构建变更时，先验证反射和资源查找。
- 提交前运行 `./gradlew :app:testDebugUnitTest :app:verifyIsland :app:lintDebug :app:assembleDebug`。

PR 说明应包含问题、改动后的行为、执行过的检查，以及是否做过真机验证。主机检查通过不能写成真机功能已验证。

项目尚未选定开源许可证；贡献前请留意 [README 中的许可说明](README.md#许可)。
