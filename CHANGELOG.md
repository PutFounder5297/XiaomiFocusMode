# 版本记录

## 未发布

- 迁移为 Android Studio / Gradle 单应用项目，增加固定版本 Wrapper。
- 将原有 Java 检查接入 JUnit，保留超级岛 JSON 和 APK / ROM 静态检查。
- APK 检查支持独立核对模块、多 DEX 和 Release 资源路径优化。
- 增加 GitHub Actions 构建检查、使用与开发文档、问题和 PR 模板。
- 将历史恢复与校验记录移至 `docs/ARCHIVE_STATUS.md`。
- 将 Android 10 的强制深色主题属性移至 `values-v29`，保持 Android 9 资源兼容。

本次仓库整理不修改模块版本或业务 Java 逻辑。

## 0.1.23-test

- 使用现代任务编号，精确识别横竖屏倒计时及启动过渡页组件。
- 遍历本应用专注任务，对服务器任务根 Intent 请求排除最近任务并读回核对。
- 覆盖页面生命周期与 `onNewIntent`，补足退后台及复用已有页面时的检查。
- 模块按钮以独立任务打开原生设置入口。

不删除任务、不强制结束页面，不扩大 LSPosed 作用域。原生计时、屏蔽策略、会话、后台临时锁定与超级岛沿用已有实现。原始归档及验证范围见 [归档状态](docs/ARCHIVE_STATUS.md)。
