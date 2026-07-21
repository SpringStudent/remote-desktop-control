[English](README.md) | [中文](README_zh.md)

### Remote Desktop Application

This project is a **Java** and **Netty** based remote desktop control application. Through this application, users can
connect and control remote devices in real-time. It is based on the client-server-client version of the core code
from [Dayon GitHub Repository](https://github.com/RetGal/Dayon). Special thanks to the Dayon project author.

If you have a higher frame rate requirement, you can check out my other remote desktop control project based on
streaming media: https://github.com/SpringStudent/a-da

### Features

1. **Real-time Remote Desktop Control**
    * Remotely control another device with minimal latency.

2. **Customizable Settings**
    * Configure screen capture intervals and enable/disable color mode to optimize performance.

3. **Cross-platform Support**
    * Developed using Java, compatible with most operating systems.

4. **Clipboard Support**
    * No speed limit file transfer.

5. **Multiscreen Support**
    * View different screens in real time by selecting them.

6. **LAN P2P Direct Connection**
    * When both peers are on the same LAN, screen capture and input events bypass the relay server
    * Reduces latency and server bandwidth usage
    * Transparent fallback to server relay if direct connection fails or drops
    * Configurable bind address and port via `configFile` properties `p2pServerIp` / `p2pServerPort`

7. **Configurable Zstd Compression Level**
    * Adjustable compression level (1–9) via the compression settings dialog
    * Level 1 (fastest) for LAN, higher levels for bandwidth-constrained WAN

## Screenshots

### Main Control Panel

![remote-desktop-control](z_launcher.png)

### Remote Connection Established

![remote-desktop-control](z_screen.png)

![remote-desktop-control](z_monitor.png)

### Settings Menu

![remote-desktop-control](z_screen_setting.png)
![remote-desktop-control](z_compress_setting.png)
![remote-desktop-control](z_clipboard.png)

### Environment

* Java 8 or higher
* Maven for dependency management

### Build and Run

1. Clone the repository：
   ```bash
   git https://github.com/SpringStudent/remote-desktop-control
   cd remote-desktop-control
   ```

2. Build the project:
   ```bash
   mvn clean install
   ```

3. Run the server: Export remote-desktop-control.sql to mysql's databases,Modify the application.properties configuration file with the database information and the
   netty.server.server and port configurations.
   ```bash
   RemoteServer.java
   ```

4. Run the client: Modify the RemoteClient.java parameters for serverIp and serverPort and clipboardServer addr,
   or use a configFile for externalized configuration:
   ```properties
   # configFile example
   serverIp=192.168.0.110
   serverPort=54321
   clipboardServer=http://192.168.0.110:12345/remote-desktop-control
   robotPort=55678
   # Optional P2P settings — omit to auto-detect LAN addresses and use a random port
   p2pServerIp=192.168.1.100
   p2pServerPort=55432
   ```
   ```bash
   RemoteClient.java -DconfigFile=/path/to/config.properties
   ```
### Demo Video

[Bilibili Video](https://www.bilibili.com/video/BV11qNCeNEoZ/)

### Future Plan

* http-based clipboard transmission (finish)
* multi-screen select support (finish)
* internationalize  

### Q&A

* It is recommended that both the control end and the controlled end run the program with administrator privileges,
  otherwise, some programs on the controlled end may not be controllable due to lack of permissions.
* For the best control experience, it is recommended to set the input language preference on the control end to "
  English (United States)".
* The stability of this project has been verified in production environments and is ready for reliable use.
* In the Windows lock screen scenario, it is not possible to capture screenshots or simulate keyboard and mouse events. Refer to the following project for a solution
  https://github.com/SpringStudent/windows-lock-helper
* The robots project is a service introduced to address the issue of being unable to capture screens in Windows lock screen scenarios. 
  This service is not required for non-Windows systems and does not need to be started. For instructions on using this service, please refer to the windows-lock-helper project.