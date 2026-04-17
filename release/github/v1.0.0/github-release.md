# GitHub Release 发布说明

## 推荐写法

- tag：`v1.0.0`
- release 标题：`Miaochat for Android v1.0.0`
- release 描述：直接粘贴 [release-notes.md](./release-notes.md) 的内容

## 这次建议上传的文件

- 主安装包：`release/github/v1.0.0/assets/miaochat-android-v1.0.0-release.apk`
- APK 校验文件：`release/github/v1.0.0/assets/miaochat-android-v1.0.0-release.apk.sha256`
- 可选归档：`release/github/v1.0.0/assets/miaochat-android-v1.0.0-release.aab`
- 可选归档校验：`release/github/v1.0.0/assets/miaochat-android-v1.0.0-release.aab.sha256`

## 这次已经验证过的内容

- `.\gradlew.bat testDebugUnitTest`
- `.\gradlew.bat assembleRelease`
- `.\gradlew.bat bundleRelease`
- `.\gradlew.bat --% connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.relaychat.RelayChatSmokeTest`
- release APK 已安装到模拟器，并且通过 `com.example.relaychat/com.example.relaychat.MainActivity` 冷启动成功
- release 安装后的进程存活检查通过
- release 包上的 chat tab、settings tab、history rail 展开这轮 smoke 已跑通

## 发布前要先确认的事

- 先把这次 release 对应的代码提交到 Git，并推到远端。不要在代码还只留在本地 worktree 时直接建 GitHub Release，不然 tag 和上传的安装包对不上。
- 这次本地 release 构建复用了现有签名配置，当前签名证书摘要是 `91d4613d43c513d174d30b4beedd3fc1240e2b1b97d1ede1dd762d11ceab4e80`。
- 当前证书主题是 `CN=RelayChat Dev, OU=Local, O=RelayChat, L=Shanghai, ST=Shanghai, C=CN`。如果这是第一次对外发包，而且你不想长期沿用这个 dev 命名的密钥，最好在首次公开发布前就换成正式密钥。已经让用户安装过的签名一旦改掉，后续更新通常会变成需要卸载重装。

## 在 GitHub 网页上怎么发

1. 把包含这次改动和 `release/github/v1.0.0/` 材料目录的提交推到远端分支。
2. 打开仓库的 `Releases` 页面，点 `Draft a new release`。
3. tag 填 `v1.0.0`。
4. 标题填 `Miaochat for Android v1.0.0`。
5. 描述直接粘贴 [release-notes.md](./release-notes.md)。
6. 上传上面列出的资产，至少要传 APK 和 APK 的 `.sha256` 文件。
7. 先存成 draft 再看一遍显示效果，确认后再发布。

## 用 gh 命令怎么发

只有在这次 release 对应提交已经推到远端以后，再运行下面这条命令：

```powershell
gh release create v1.0.0 `
  release/github/v1.0.0/assets/miaochat-android-v1.0.0-release.apk `
  release/github/v1.0.0/assets/miaochat-android-v1.0.0-release.apk.sha256 `
  release/github/v1.0.0/assets/miaochat-android-v1.0.0-release.aab `
  release/github/v1.0.0/assets/miaochat-android-v1.0.0-release.aab.sha256 `
  --draft `
  --title "Miaochat for Android v1.0.0" `
  --notes-file release/github/v1.0.0/release-notes.md
```

如果你只想上传主安装包，也可以把 AAB 两个文件从命令里删掉。

