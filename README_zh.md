[English](README.md) | [中文](README_zh.md)

### 远程桌面应用程序

该项目是一个使用 **Java** 和 **Netty**
开发的远程桌面控制应用程序。通过该应用程序，用户可以实时连接并控制远程设备。是基于https://github.com/RetGal/Dayon
核心代码实现的client-server-client版本，在这里再次感谢Dayon作者的项目。

如果对帧率要求比较高，可以查看我的另一个基于流媒体的远程桌面控制项目：
https://github.com/SpringStudent/a-da

### 功能

1. **实时远程桌面控制**
    * 以最低的延迟远程控制另一台设备。

2. **可定制设置**
    * 配置屏幕捕获间隔，启用/禁用彩色模式以优化性能。

3. **跨平台支持**
    * 使用 Java 开发，可兼容大多数操作系统。

4. **粘贴板支持**
    * 支持粘贴板文本和文件传输。
5. **多屏幕支持**
    * 选择不同屏幕实时查看。

6. **局域网 P2P 直连**
    * 两端同处一个局域网时，屏幕画面和键鼠事件不经服务器中继，直接点对点传输
    * 降低延迟、节省服务器带宽
    * 直连失败或断开时自动透明降级为服务器中继
    * 支持通过 `configFile` 配置 `p2pServerIp` / `p2pServerPort` 指定绑定地址和端口

7. **可配置 Zstd 压缩级别**
    * 通过压缩设置对话框可调整 Zstd 压缩级别（1–9）
    * 级别 1 最快适合局域网，高级别适合低带宽广域网

## 截图

### 主控制面板

![remote-desktop-control](z_launcher.png)

### 远程连接已建立

![remote-desktop-control](z_screen.png)

![remote-desktop-control](z_monitor.png)

### 设置菜单

![remote-desktop-control](z_screen_setting.png)
![remote-desktop-control](z_compress_setting.png)
![remote-desktop-control](z_clipboard.png)

### 运行环境

* Java 8 或更高版本
* 用于依赖管理的 Maven

### 构建与运行

1. 克隆代码库：
   ```bash
   git https://github.com/SpringStudent/remote-desktop-control
   cd remote-desktop-control
   ```

2. 构建项目：
   ```bash
   mvn clean install
   ```

3. 运行服务端：将remote-desktop-control.sql导入mysql数据库，修改application.properties配置文件数据库信息和netty.server.server和port配置
   ```bash
   RemoteServer.java
   ```

4. 运行客户端：修改RemoteClient.java参数中的serverIp和serverPort和clipboardServer，
   或通过 configFile 外部化配置：
   ```properties
   # configFile 示例
   serverIp=192.168.0.110
   serverPort=54321
   clipboardServer=http://192.168.0.110:12345/remote-desktop-control
   robotPort=55678
   # P2P 可选配置 — 不设则自动检测局域网地址并使用随机端口
   p2pServerIp=192.168.1.100
   p2pServerPort=55432
   ```
   ```bash
   RemoteClient.java -DconfigFile=/path/to/config.properties
   ```
### 视频演示

[Bilibili Video](https://www.bilibili.com/video/BV11qNCeNEoZ/)


### 未来规划

* 基于http的粘贴板传输 (已完成)
* 多屏幕切换支持 (已完成)
* 国际化

### Q&A

* 控制端和被控制端最好以管理员权限运行程序，否则会出现被控制端部分程序无权限操控
* 在控制端最好切换输入法语言首选项为"英语(美国)"以获得最佳控制体验
* 本项目稳定性经过生产验证，可放心食用
* windows锁屏场景下无法抓图和模拟键盘鼠标事件，参考下面项目解决
  https://github.com/SpringStudent/windows-lock-helper
* robots项目是用于解决windows锁屏场景下无法抓图问题引入的服务，非windows系统无需
  启动该服务，该服务使用说明请参考windows-lock-helper项目

