# 构建与验证

## 环境

- JDK 17，建议使用 Android Studio 的兼容 JDK。
- Android SDK Platform 35；Android Gradle Plugin 8.7.3 默认使用 Build Tools 34.0.0。
- Python 3，用于现有超级岛 JSON 检查；构建 APK 和运行 JUnit 不需要 Python。
- Gradle 8.9，由仓库内的 Wrapper 下载，不需要单独安装 Gradle。

依赖从 Google Maven、Maven Central 和 `https://api.xposed.info/` 获取。Xposed API 82 使用 `compileOnly`，由 LSPosed 在运行时提供，不打包进模块。

## Android Studio

打开仓库根目录，选择 JDK 17 并完成 Gradle 同步。在 SDK Manager 安装 API 35。Android Studio 会生成本机 `local.properties`；该文件不应提交。

命令行可通过 `ANDROID_HOME` 指定 Android SDK，或在根目录 `local.properties` 配置：

```properties
sdk.dir=/absolute/path/to/Android/Sdk
```

## 命令行

Linux / macOS：

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:verifyIsland :app:lintDebug
./gradlew :app:assembleRelease
```

Windows 将 `./gradlew` 换为 `gradlew.bat`。若 Python 命令为 `python`，使用：

```text
gradlew.bat :app:verifyIsland -Ppython=python
```

`./gradlew :app:check` 包含 Android 检查、JUnit 和超级岛 JSON 检查，需要 Python 3。JUnit 通过适配类执行原有 7 组独立检查，共 257 项断言；`verifyIsland` 使用测试类路径运行生产 JSON 生成器并核对 14 组数据。报告位于 `app/build/reports/`。

| 产物 | 路径 |
| --- | --- |
| 测试 APK | `app/build/outputs/apk/debug/app-debug.apk` |
| 未签名 Release APK | `app/build/outputs/apk/release/app-release-unsigned.apk` |

CI 构建 Debug、未签名 Release，并运行 JUnit、JSON 和 Android Lint 检查。测试 APK 可从 Actions 的 `debug-apk` 附件下载；它不是正式发布包。

`app/lint.xml` 只对两处宿主 Hook 调用做定向排除：宿主通知权限和隐藏的 `statusbar` 服务名。其余 Lint 检查继续执行，警告保留在报告中。

## 签名

Debug 使用本机 Android 调试签名。Release 默认不配置签名，不使用调试密钥冒充正式发布包。原签名私钥不在备份中，无法从 APK 恢复；新签名不能覆盖旧签名安装包。

找到原密钥后，可用 Android Studio 的 **Generate Signed Bundle / APK** 生成同签名发布包。不要提交私钥、密码或本机签名配置。原 APK 和证书摘要保留在 [归档状态](ARCHIVE_STATUS.md)。

## 可选静态核对

这些脚本依赖用户自行提供的目标系统文件，CI 不下载或分发它们：

```bash
python3 -m venv .venv
. .venv/bin/activate
python3 -m pip install androguard loguru
python3 tests/inspect_apk.py app/build/outputs/apk/debug/app-debug.apk
python3 tests/inspect_apk.py app/build/outputs/apk/debug/app-debug.apk /path/to/misettings.apk
python3 tests/inspect_rom.py /path/to/framework.jar /path/to/services.jar
```

`inspect_apk.py` 核对模块结构；提供第二个 APK 参数时，另行核对指定版本的小米设置。`inspect_rom.py` 核对 ROM API 与清理分支。它们都是主机静态检查，不执行手机功能测试。实际设备还应检查会话复用、应用拦截、分身验证、最近任务和超级岛恢复。

## 从原始归档迁移

原构建入口 `python3 build.py` 已由 Gradle Wrapper 替代。Java、资源、Manifest 和 Xposed 入口迁入 `app/src/main/`；Java 检查迁入 `app/src/test/java/`。版本、SDK 和包名配置集中在 `app/build.gradle`。业务 Java 源码保持原有内容，构建产物不保证与原归档 APK 逐字节相同。
