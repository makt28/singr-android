# singr-android

在一台专用安卓设备(闲置手机 / 电视盒子)上跑 **SingR 服务端节点**,把家宽 /
海外宽带无痛变成一个节点。

这**不是** VPN 客户端。App 把真正的 `singr` 二进制作为子进程拉起;这里的
`VpnService` 只是一个**保活锚点**(见下),它不接管本机流量。

## 如何与 SingR 保持同步

App 从不重新编译 SingR,而是直接装 SingR GitHub release 里**预编译**好的
`libsingr.so`(静态 PIE 的 arm64 构建),所以 core / fork / poet 层和发布的二进制
逐字节一致——**零版本漂移**。

构建流程是**手动触发**的:在 Actions 里跑
[`.github/workflows/build.yml`](.github/workflows/build.yml)(Run workflow),它会:

1. 解析要打包的 SingR tag——**留空则取最新 release**(用 GitHub `/releases/latest`
   语义,不受 `gh release list` 语义排序误导),也可在触发时填一个具体 tag 回滚 /
   复现。
2. 从该 tag 下载 `SingR-android-arm64.tar.gz`,校验 sha256,把 `libsingr.so` 放进
   `app/src/main/jniLibs/arm64-v8a/`。
3. 构建 APK,产物在这次 Actions run 的 artifacts 里(`singr-node-apk-<tag>`)。

解析到的 tag 会在构建时写入 `SINGR_VERSION`,让 APK 的 `versionName` 反映所带 core
版本(该文件不提交,只作本地构建的默认值)。所以要出新版,直接手动跑一次 workflow
即可,无需改任何文件。

## 架构

| 组件 | 文件 | 职责 |
|---|---|---|
| 保活 VPN | `SingrVpnService.kt` | 惰性 tun(无真实路由、排除自身)→ Doze/OEM 下难被杀;持有前台通知 |
| 进程管理 | `NativeRunner.kt` | exec `libsingr.so run -c server.json -p panel.json`,看门狗重启 |
| DDNS | `DdnsWorker.kt` / `DdnsProvider.kt` | 把设备全局 IPv6 推到 `AAAA` 记录 |
| 开机自启 | `BootReceiver.kt` | 兜底;真正的自启靠系统 **Always-on VPN** |

### 为什么用 VpnService 保活(以及那个坑)

活跃的 VPN 会话被系统视为用户关键任务,所以 OS/OEM 很少杀它——这正是我们要的
保活。但如果 tun 真的抓流量,就会把节点自己的出站黑洞掉。所以 tun 是**惰性**的:
一个地址、一条废弃路由、`addDisallowedApplication(自身)`(SingR 子进程和 App 同
UID,因此永远绕过 tun)。没有任何真实流量进隧道。

## "死了就重启"(专用机)

1. **系统层**:在设置里开启 *Always-on VPN + Lockdown* → 系统在被杀和开机时都会
   重新拉起 `SingrVpnService`。
2. **服务层**:前台服务 + `START_STICKY`,并加入电池优化白名单。
3. **进程层**:`NativeRunner` 看门狗在 `libsingr.so` 退出时重启(专用机可无限退避
   重试)。

## 配置一个节点

`panel.json`(SSPanel URL + key + node id)是每台设备独有的,需要在设备上提供
(在 `MainActivity` 粘贴,保存到 `filesDir/panel.json`)。`server.json` 的默认值放在
`assets/`(v6 节点:inbound 监听 `::`)。SSPanel 节点串格式见主仓库的 AGENTS.md。

## 可达性(不满足则一切白搭)

- 设备有公网 **IPv6**,且路由器/ISP **没有**对入站 v6 到 anytls(TCP)/
  hysteria2(UDP)端口做防火墙拦截。
- DDNS 在 v6 前缀轮换时保持 `AAAA` 记录最新。

## 本地构建

仓库没有提交 Gradle wrapper jar。先生成一次(或直接用 Android Studio 打开工程,
它会自动生成):

```bash
gradle wrapper --gradle-version 8.11.1
```

然后把匹配的 core 放到位并构建:

```bash
# 按 pin 的 tag 拉取 core(CI 会自动做):
gh release download "$(cat SINGR_VERSION)" -R makt28/SingR \
  -p SingR-android-arm64.tar.gz
tar xzf SingR-android-arm64.tar.gz
cp SingR-android-arm64/libsingr.so app/src/main/jniLibs/arm64-v8a/

./gradlew assembleRelease
```

> 依赖 `android:extractNativeLibs=true` + `useLegacyPackaging=true`(已设好),
> 这样 `libsingr.so` 才会被解压到 `nativeLibraryDir` 并可执行。
