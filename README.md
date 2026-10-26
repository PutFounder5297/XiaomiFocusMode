# 小米原生专注模式 LSPosed 模块

模块显示名：**专注模式**，包名：`dev.lance.focusallowlist`。
当前归档版本：`0.1.23-test`，versionCode `24`。

保留小米原生专注计时、历史与应用拦截，只放行代码中固定的必要应用。不提供自由编辑白名单、提前结束或绕过应用锁的入口。

## 适配与作用域

仅适配 `com.xiaomi.misettings`（小米设置）版本 `16.4.260829.00`，versionCode `260829400`。版本不匹配时不安装核心专注 Hook。

在 LSPosed 中只选择「小米设置」。无需勾选桌面、SystemUI 或系统框架。

- minSdk 28，targetSdk 35；需要已可用的 LSPosed 环境。
- 固定名单以 `src/dev/lance/focusallowlist/Policy.java` 为准，目前为 57 个精确包名；应用显示目录和 Manifest queries 与之同步。
- 保留输入法、系统必要组件、拨号短信、微信/QQ 分身和原有密码/指纹验证流程，不绕过应用锁。
- 自定义整数时长范围为 1–35791 分钟。重复进入应返回同一原生会话，保留原起始时间。
- 模块界面包含正式首页、只读应用名单、说明、关于和独立排障入口，支持深浅色；图标使用冥想插画。

## 安装与使用

1. 从本仓库 Releases 下载 APK。
2. 等当前专注结束后安装，在 LSPosed 中启用模块，仅勾选「小米设置」，然后重启手机。
3. 打开模块进入原生专注设置，设置时长并开始专注。
4. 通过模块入口或通知返回已有专注，检查剩余时间未重置。
5. 正常停用时先等专注结束，再关闭模块并重启；无需清除小米设置数据。

模块首页可显示在最近任务中。原生倒计时任务会请求排除最近任务；切回桌面、打开微信后再查看，可检查后台卡片是否残留。用户已反馈 0.1.23 此项行为正常，该反馈不代表全部功能均已重新实测。

## 0.1.23 改动

- 使用现代任务编号，并精确识别横竖屏倒计时和启动过渡页组件。
- 遍历本应用专注任务，对服务器任务根 Intent 请求排除最近任务并读回核对。
- 覆盖页面生命周期与 `onNewIntent`，补足退后台和复用已有页面时的检查。
- 模块按钮以独立任务打开原生设置入口。

不删除任务，不强制结束页面，不扩大 LSPosed 作用域。原生计时、屏蔽策略、会话、后台临时锁定与超级岛实现保持现有版本。

超级岛沿用 `buttons-free-v2` 模板，不发送按钮、actionInfo、miui.focus.actions 或厂商 timerInfo。后台保护不能保证抵抗强行停止或所有系统清理方式。

## 构建与检查

这是 Java 项目，不使用 Gradle。Linux 下需要 Python 3、Java 17 或以上运行时与 keytool：

```bash
python3 build.py
```

脚本使用 Android 35 平台、build-tools 35、ECJ 3.38.0 和 Xposed API 82，依次编译、运行测试、打包并签名。支持 `BUILD_TOOLS`、`ANDROID_JAR`、`ECJ_JAR`、`XPOSED_JAR` 环境变量指定已有工具。

依赖下载使用 `dl.google.com`、`repo.maven.apache.org` 和 `api.xposed.info`。源码仓库不保存工具链或系统 APK/JAR。

**签名私钥不在备份中。** 缺少 `test-signing.p12` 时，构建脚本会生成新测试密钥；新签名 APK 无法覆盖当前 Releases 中的原签名版本。后续同签名升级需要先找回原密钥，不要将私钥提交到 GitHub。

现有主机检查包括 257 项 Java 检查和 14 组超级岛 JSON 检查。模块与原生设置 APK 的进一步静态核对可使用：

```bash
python3 -m pip install androguard loguru
python3 tests/inspect_apk.py <模块APK> <用户自有的小米设置APK>
```

`tests/inspect_rom.py` 用于核对用户自有的系统 JAR。主机检查不等于手机功能验证；本次执行范围及原 APK 校验信息见 [归档状态](ARCHIVE_STATUS.md)。

## 备份范围

此仓库来自找回的原始源码，代码、测试、资源和构建脚本保持原样。公开版本排除个人诊断材料，安装说明经过整理。发布附件包括原 APK、公开源码 ZIP、安装说明与 SHA256 校验清单。

原始完整备份另行保留，未公开上传；反编译产物、签名私钥、系统 APK/JAR 和个人原始日志均不在仓库中。许可证尚未选定。
