# Miaochat 安装说明

## 从 GitHub Releases 安装

1. 打开这个版本的 GitHub Release 页面。
2. 在 `Assets` 里下载 `miaochat-android-v1.0.0-release.apk`。
3. 如果你想先验文件完整性，再一起下载 `miaochat-android-v1.0.0-release.apk.sha256`。
4. 在手机上打开 APK 开始安装。第一次从浏览器或文件管理器安装时，Android 可能会让你先允许“安装未知应用”。
5. 安装完成后，桌面上的应用名会显示为 `Miaochat`。

## 在 Windows 上校验 APK

如果你是先把 APK 下载到电脑上，再传到手机，可以在 PowerShell 里运行：

```powershell
Get-FileHash .\miaochat-android-v1.0.0-release.apk -Algorithm SHA256
```

看到的哈希应该是：

```text
a9f361c50b528c687785bf98d783a64bbafe918d3f8ebbc355bd776067e51d87
```

## 常见情况

- 如果系统提示“应用未安装”，先确认你手机里是不是已经装过一个用不同签名打出来的 `com.example.relaychat`。那种情况通常需要先卸载旧包再装这个 release 包。
- 如果浏览器里点 APK 没反应，可以去下载目录里用系统文件管理器再点一次。
- 这个安装包不是 Google Play 版，后续更新也需要继续从 GitHub Releases 手动下载安装。

