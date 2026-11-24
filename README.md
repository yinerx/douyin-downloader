# 抖音无水印

个人自用的 Android 应用。从抖音分享里保存**没有抖音角标、没有片尾引流卡**的视频，也可以保存 X（Twitter）公开帖子里的视频和动图，写进手机相册。侧载到自己的手机即可，不打算上架应用商店。

应用名仍是「抖音无水印」。分享面板里看到的也是这个名字，这样不用改已经打开过的分享目标。界面里会写明它同时处理抖音和 X。

只处理你自己能打开的公开分享链接。请只保存你有权留存的内容，不要把别人的作品再发出去。

## 怎么用

应用名是「抖音无水印」。界面是简体中文。

### 从抖音分享

1. 在抖音里打开视频，点「分享」。
2. 在系统分享面板里选「抖音无水印」。
3. 应用会开始下载，界面上能看到进度。成功后提示已保存。

如果分享面板里没有本应用：点「更多」或分享面板的编辑按钮，把「抖音无水印」打开。小米的分享面板有时要滑到底。

### 从 X 分享

1. 在 X 里打开带视频或动图的帖子，点分享，选「抖音无水印」。
2. 链接可以是 `x.com`、`twitter.com`、`mobile.twitter.com`，也可以是 `t.co`、`fxtwitter.com`、`vxtwitter.com`。后面的 `?s=...` 之类参数不用删。
3. 帖子里只有一个视频（动图也算，X 用 MP4 提供）时，直接下载画质最高的那一档。
4. 有多个视频时，会列出每一个的预览图、序号（第 1 个、第 2 个）和时长。勾选要保存的，或点「全选」，再点「下载所选」。什么都不选就不会开始下载。

不需要登录 X。私密账号、已删除的帖子，以及没有视频的帖子，会用中文说明原因。

### 从剪贴板

1. 在抖音里点「复制链接」，或在 X 里复制帖子链接。文案前后可以有别的字。
2. 打开本应用。它会读剪贴板，认出抖音或 X 的链接后问你要不要下载。
3. 点「下载」。点「忽略」则这次不再提示同一条链接。X 的多视频帖子同样会先让你勾选。

Android 12 及以上在应用读取剪贴板时会弹出系统提示，这是系统行为，不是故障。

也可以把分享文案直接贴进输入框，再点「开始下载」。

短链（`v.douyin.com`，包含下划线）、`www.douyin.com/video/数字`、`iesdouyin.com/share/video/数字`、带 `modal_id=` 的链接都可以。认不出来时会写出原因，例如不是抖音链接、作品已删除、或是图文。

### 文件在哪

抖音视频保存到相册的 `DCIM/Douyin`，文件名是「标题_作品编号.mp4」。

X 的视频保存到 `DCIM/X`，文件名是「作者_帖子ID.mp4」。同一条帖子里的第 2 个、第 3 个视频会加上 `_2`、`_3`。动图也存成 MP4。

系统相册和 Google 相册一般会在「视频」里看到它们。小米相册如果没立刻出现，下拉刷新，或到「文件管理 → DCIM」查看对应文件夹。下载记录在应用里，每条前面有「抖音」或「X」。点「打开视频」用系统播放器打开。

## 编译

需要 JDK 17 或更高，以及 Android SDK：`platforms;android-35` 和 `build-tools;35.0.0`。`minSdk` 是 26。

在项目根目录建 `local.properties`（不要提交）：

```
sdk.dir=/你的/Android/Sdk
```

然后：

```bash
./gradlew :parser:test
./gradlew assembleDebug
```

调试 APK 在：

```
app/build/outputs/apk/debug/app-debug.apk
```

解析逻辑在独立的 `:parser` 模块里，不依赖 Android，单元测试只跑这个模块。

## 安装到小米手机

手机要有 Google 服务（用来提供系统 WebView 也行，小米自带 WebView 同样可用）。不需要登录抖音账号。

用数据线：

1. 手机打开「设置 → 我的设备 → 全部参数」，连点「MIUI 版本」打开开发者选项。
2. 「设置 → 更多设置 → 开发者选项」里打开「USB 调试」。
3. 电脑上执行 `adb install -r app-debug.apk`。

不用电脑：把 APK 拷到手机，用文件管理打开。若被拦住，到「设置 → 隐私保护 → 特殊权限 → 安装未知应用」，给当前用来打开 APK 的应用（文件管理或浏览器）允许安装。

装好后第一次从抖音分享时，如果列表里没有本应用，按上一节把分享目标打开。

## 在线更新

应用里有「检查更新」。默认启动时也会查一次，可以用开关关掉。查到新版本会显示说明，下载 APK 后交给系统安装器。安装前如果系统拦住，按提示给本应用打开「安装未知应用」。

版本号在 `app/build.gradle.kts` 的 `versionCode` / `versionName`。第一个公开版本是 `1.0.0`（versionCode 1）。发下一版时先把这两个数字改大（`versionCode` 必须比手机上的大），合并之后在 **main 的那次提交**上打一个和 `versionName` 一样的 tag。可选前缀 `v`，例如 `1.0.0` 或 `v1.0.0`：

```bash
git checkout main
git pull
git tag 1.0.0
git push origin 1.0.0
```

推送 tag 会跑 `.github/workflows/release.yml`（也可以在 Actions 里手动 `workflow_dispatch`）。工作流用 PKCS12 钥匙签 release APK，挂到这个 tag 的 Release 上，并附带 `latest.json`。说明文字来自仓库里的 `RELEASE_NOTES.md`，正文另有一行 `versionCode:`，应用靠它判断新旧。

### 签名

Release 包只使用 GitHub Actions 里的 PKCS12 钥匙，证书 SHA-256 是 `78:4B:2F:13:27:77:67:02:A6:F7:59:7C:3C:68:8C:2A:EF:14:0F:F0:11:22:67:A5:7C:D9:74:CC:18:91:7C:B7`。钥匙文件和口令不在仓库里。没有这把钥匙时，`assembleRelease` 会失败，不会改用 debug 签名。

四个 Secret（Settings → Secrets and variables → Actions）：

| Secret | 内容 |
| --- | --- |
| `KEYSTORE_BASE64` | PKCS12 钥匙文件的 base64 |
| `KEYSTORE_PASSWORD` | keystore 口令 |
| `KEY_ALIAS` | 密钥别名 |
| `KEY_PASSWORD` | 密钥口令 |

本地如果要自己签 release，把钥匙放到 `signing/release.keystore`（PKCS12），复制 `keystore.properties.example` 为 `keystore.properties` 再填口令。这两个路径都在 `.gitignore` 里。Debug 包仍然用本机自己的 debug 钥匙，和正式发布无关。

### 公开的 Release

仓库已经是公开的。应用不带登录，先读这个附件（会跳转到 `objects.githubusercontent.com`，不走每小时 60 次的 API 限额）：

```
https://github.com/yinerx/douyin-downloader/releases/latest/download/latest.json
```

这个地址打不开时，才退回 `https://api.github.com/repos/yinerx/douyin-downloader/releases/latest`。API 返回 403 或 429 时，界面写「GitHub 限制了当前网络的访问次数」，而不是只给一个 HTTP 状态码。发布附件的下载地址同样公开，手机不用 token。还没有任何 Release 时，应用会写「还没有发布」，不会当成已经是最新版。

默认地址在 `gradle.properties` 的 `updateManifestUrl`。装好之后也可以在「检查更新」卡片里改，存在本机。自定义地址如果是一份 JSON，格式是：

```json
{
  "versionCode": 4,
  "versionName": "1.1.0",
  "changelog": "写给自己看的说明",
  "apkUrl": "https://example.com/douyin-save.apk"
}
```

## 解析原理（2026-11-19 实测）

抖音现在不让服务器直接拿播放地址。下面几条在当天对公开链接请求过，都走不通：

| 做法 | 结果 |
| --- | --- |
| `www.iesdouyin.com/share/video/{id}/` 的 `window._ROUTER_DATA` | 有这个对象，但没有 `videoInfoRes` / `play_addr` |
| `www.iesdouyin.com/web/api/v2/aweme/iteminfo/?item_ids=` | HTTP 200，正文是空的 |
| `www.douyin.com/aweme/v1/web/aweme/detail/?aweme_id=` | `403 Blocked by ArgusSecurityPlugin Uifid Not Found` |

还能用的做法是让一个真实的手机浏览器打开分享页，读它自己填好的数据：

1. 从分享文案里取出链接（`:parser` 的 `ShareTextParser`）。短链可以夹在任意文字里，短码允许下划线。
2. 跟随 `v.douyin.com` 的跳转，从最终 URL 取出作品编号（`AwemeIds`）。删除的作品常跳到 `jingxuan` 或带 `web_video_404`，这时直接报错，避免下到别的推荐视频。
3. 先用手机 UA 请求 `https://www.iesdouyin.com/share/video/{id}/`。如果 HTML 里的 `_ROUTER_DATA` 已经带了视频（以后若恢复服务端渲染），就不再开 WebView。
4. 否则用隐藏的 WebView、同一套手机 UA 打开 `https://m.douyin.com/share/video/{id}/`，轮询直到 `window._ROUTER_DATA` 里出现该作品的 `play_addr`。2026-11-19 这条路径对两条公开视频都在几秒内成功。
5. `VideoInfoParser` 在 JSON 里找 `aweme_id` 与 `video.play_addr.uri`，不写死 `loaderData` 的路径。
6. `NoWatermarkUrls` 只用 `/aweme/v1/play/`（没有 `wm`）拼出 1080p、720p 和 `ratio=default&watermark=0`。下载前用 `Range` 看文件大小和 `ftyp` 头，选最大的一个。请求带 `Referer: https://m.douyin.com/`。

不要把 `playwm` 放进候选。它有时比无水印文件更大，因为多了角标和片尾，按体积挑选会被选中。

当天核对过的两条公开链接：

- `https://v.douyin.com/J4IQiholWbo/` → 作品 `7624728488368732900`，uri `v0d00fg10000d787jq7og65p3g4j12r0`。`play` 1080p 约 26.8 MB，`play` 720p 约 15.5 MB，`playwm` 720p 约 17.4 MB。都是 `ftypisom`。
- `https://v.douyin.com/GOPRuJTWTe4/` → 作品 `7662968410262736166`，uri `v0200fg10000d9c57nnog65ohseq72n0`。`play` 1080p 为 1080×1440、约 8.6 秒、有音轨、画面没有抖音角标。`playwm` 720p 更长（约 11.6 秒），画面左上角有抖音 logo 和抖音号，结尾是「来抖音 发现更多创作者」卡片。

作者自己烧在画面里的字幕、台标不会去掉。应用去掉的是抖音加上的角标和片尾。

## X 的解析（2026-11-24 实测）

公开帖子不登录、也不使用用户 token。按这个顺序试，前一个成功就不再请求后面的：

1. `https://cdn.syndication.twimg.com/tweet-result?id={帖子编号}&lang=en&token={token}`。这是 X 嵌入页用的公开接口。`token` 与嵌入脚本相同：把帖子编号按 JavaScript 的 `Number` 转成浮点，乘 `π / 1e15`，用 36 进制表示，再去掉 `0` 和小数点。不带 token 时返回空对象 `{}`。
2. `https://api.fxtwitter.com/status/{帖子编号}`
3. `https://api.vxtwitter.com/status/{帖子编号}`

两个镜像的 JSON 形状一样。只在嵌入接口被限流、返回 5xx，或正文无法解析时才用。帖子已删除、受保护，或确实没有视频时，不再换接口。

视频地址在 `mediaDetails[].video_info.variants`（镜像则在 `tweet.media.videos[].variants`）。只留 `video/mp4`，丢掉 `.m3u8`，取得码率最高的一条。动图的类型是 `animated_gif`，通常只有一条码率为 0 的 MP4，地址在 `video.twimg.com/tweet_video/`。一条帖子里的多段视频都在 `mediaDetails` 里；顶层的 `video` 往往只有第一段，而且不带码率，所以有 `mediaDetails` 时以它为准。

当天从这台环境直接请求并核对过：

| 帖子 | 结果 |
| --- | --- |
| `https://x.com/SpaceX/status/2121715216597827756` | 嵌入接口返回 5 档 MP4。最高 3840×2160、码率 25128000。下载文件 110014999 字节，`ftypisom`，约 92.8 秒，H.264 + AAC |
| `https://twitter.com/UltimaShadowX/status/1577719286659006464` | 4 段视频，每段都能选出最高码率 MP4。其中一段 1280×720 的成片约 4.3 秒，可播放 |
| `https://x.com/ChromiumDev/status/2100630863679529243` | 动图，`video.twimg.com/tweet_video/HSbkx59W0AENuVq.mp4`，960×540，约 3.4 秒，无音轨 |
| 已删除的帖子 | `__typename` 为 `TweetTombstone`，正文类似 “This Post was deleted by the Post author” |
| `https://t.co/YJzmGUZb2B` | 301 到 `twitter.com/SpaceX/status/2121715216597827756/video/1`，编号仍能取出 |

访客 token 的 GraphQL `TweetResultByRestId` 也能读公开帖，但查询编号经常变，这次没有用。镜像在数据中心 IP 上有时会被挡住；手机上作为备用仍然保留。

## 失效了怎么改

改动集中在 `parser/src/main/kotlin/app/douyin/save/parser/`。改完跑 `./gradlew :parser:test`。

1. 手机连上电脑，复现一次失败，看日志：`adb logcat -s DouyinSave:I`。里面有跳转后的 URL、走的是 `ssr` 还是 `webview`、以及 `play` 地址（问号前面的部分）。调试包也可以用桌面 Chrome 打开 `chrome://inspect` 看隐藏 WebView。
2. 用手机 UA 打开 `https://m.douyin.com/share/video/作品编号/`，在控制台读 `window._ROUTER_DATA`。
3. 字段还叫 `aweme_id` 和 `video.play_addr.uri`、只是外层路径变了：`VideoInfoParser` 会自己找，通常不用改。路径和字段都变了，就改 `consider()`。
4. 播放域名或参数变了：改 `NoWatermarkUrls`。用 `curl -H "Referer: https://m.douyin.com/" -H "Range: bytes=0-31" -A "手机UA"` 确认响应是 `video/mp4`，前几个字节里有 `ftyp`，并且地址路径是 `/play/` 不是 `/playwm/`。
5. 短链不再 302：看 `app` 模块的 `LinkResolver`。作品编号的新位置：改 `AwemeIds`。
6. 分享页必须登录或出验证码：这版没有登录态，只能提示失败。不要把账号 cookie 写进仓库。

X 这边改 `parser/src/main/kotlin/app/douyin/save/parser/XLinks.kt` 和 `XPostParser.kt`。链接认不出：改 `XLinks.statusId` 和 `ShareTextParser`。嵌入接口的字段变了：改 `XPostParser`。用 `curl` 打开 `cdn.syndication.twimg.com/tweet-result?id=编号&lang=en&token=算出来的token`，确认 `mediaDetails` 里还有 `video/mp4`。下载被拒时看 `app` 模块的 `XClient`，不要把账号 cookie 写进仓库。

## 隐私

抖音链接只发给抖音自己的域名。X 的链接先发给 `cdn.syndication.twimg.com`，视频文件从 `video.twimg.com` 下载，预览图来自 `pbs.twimg.com`。只有嵌入接口失败时，才会把帖子编号发给 `api.fxtwitter.com`，仍失败再试 `api.vxtwitter.com`。应用不带登录。下载记录存在应用私有目录的 `history.json`。

## 限制

- 抖音只下载视频。图文、合集链接会提示不支持。X 只下载视频和动图，纯文字或只有图片的帖子会提示没有视频。
- 一条 X 帖子有多个视频时，必须勾选后才下载。
- 私密、已删除、或被风控拦住的作品会失败，并在界面上说明。X 被限流时也会说明，并先尝试备用接口。
- 播放地址会过期，所以每次下载都重新解析，不把地址存下来。
- 下载过程中请留在应用里。滑掉应用会中断这一次。
- 隐藏的 WebView 要等第一帧画完再创建。Android 12 及以上如果在第一帧里就创建 WebView，系统启动画面不会退出，窗口透明度会停在 0，看起来就是一直白屏，进程却还活着。
