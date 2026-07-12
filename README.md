# singr-android

用一台闲置安卓设备(旧手机、电视盒子)跑 SingR 节点,把家里或海外的宽带变成一个落地/出口节点。

App 直接跑 SingR 官方发布的服务端二进制,自己不改内核。里面的 VpnService 只是用来保活,不接管手机流量。

## 用法

1. 到 Actions 里手动跑 **Build APK**(Run workflow)。tag 留空就打包最新的 SingR。
2. 跑完会在本仓库 Releases 生成一个和 SingR 版本同号的 release(例如 `v0.5.3`),里面有 apk。用节点设备的浏览器打开直接下载安装(需要允许「未知来源」)。
3. 打开 App，顶部有三个标签页：
   - **节点配置**：点「添加节点」，填名称、选类型(AnyTLS / Hysteria2)、填 API 地址 / Key / Node ID，再从文件管理器选证书和私钥。可加多个节点，每个用开关单独启用/禁用。点「启动」把已启用的节点一起跑起来（改动配置后要先停止再启动）。
   - **端口跳跃**：需要 root。填 UDP 端口范围和目标端口，一键下发 NAT 规则。没 root 会提示不可用。
   - **DDNS**：填 Cloudflare Token / Zone ID / Record ID / 域名，开启后每 15 分钟把本机 IPv6 更新到 AAAA 记录。
4. 到系统设置里开启 **Always-on VPN + Lockdown**，并把 App 加进电池优化白名单，这样它被杀掉或重启后会自己起来。

### Cloudflare DDNS 参数

DDNS 页面里的 Key 指的是 **Cloudflare API Token**，不是 Global API Key。各参数按下面的方法获取：

1. 先在 Cloudflare 控制台进入对应域名的 **DNS → Records**，创建一条要让本 App 更新的 `AAAA` 记录，例如 `node.example.com`。代理状态选 **DNS only**；初始 IPv6 可以先填设备当前的公网 IPv6。
2. **API Token**：进入 **My Profile → API Tokens → Create Token**，使用 **Edit Zone DNS** 模板。权限保留 `Zone / DNS / Edit`（新版界面可能显示为 `DNS Write`），Zone Resources 只选择要更新的域名。Token 只显示一次，创建后立即复制到 App 的 `API Token` 输入框。
3. **Zone ID**：进入该域名的 **Overview** 页面，在页面底部的 **API** 区域复制 `Zone ID`。
4. **Record ID**：Cloudflare 控制台目前不直接显示它。用上面的 Token 和 Zone ID 调用查询接口：

   ```bash
   curl -s "https://api.cloudflare.com/client/v4/zones/<ZONE_ID>/dns_records?type=AAAA&name=node.example.com" \
     -H "Authorization: Bearer <API_TOKEN>"
   ```

   返回 JSON 中 `result` 里对应记录的 `id` 就是 `Record ID`。把完整记录名（示例中的 `node.example.com`）同时填入 App 的“域名”一栏。

Token 相当于密码，不要提交到仓库、截图分享或使用权限更大的 Global API Key。

## 前提

- arm64 安卓设备,单独当节点用。
- 有公网 IPv6,且路由器/运营商没挡入站 v6。IP 变了靠 App 里的 DDNS 更新 AAAA 记录。

## 本地构建

没提交 gradle wrapper,先生成一次(或用 Android Studio 打开):

```bash
gradle wrapper --gradle-version 8.11.1
gh release download "$(cat SINGR_VERSION)" -R makt28/SingR -p SingR-android-arm64.tar.gz
tar xzf SingR-android-arm64.tar.gz
cp SingR-android-arm64/libsingr.so app/src/main/jniLibs/arm64-v8a/
./gradlew assembleRelease
```
