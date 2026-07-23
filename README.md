[English](README.md) | [中文](README_zh.md)

# Remote Desktop Control

A **Java** / **Netty** based remote desktop control application with real-time screen sharing
and remote input. Built on the client-server-client relay model, with automatic P2P direct
connection for LAN scenarios.

Based on [Dayon](https://github.com/RetGal/Dayon) — a remote desktop assistant.
Re-architected into a client-server-client relay model with P2P direct connection support.

> For higher frame rate requirements, check out my streaming-based remote desktop project:
> [a-da](https://github.com/SpringStudent/a-da)

## Features

| # | Feature | Description |
|---|---------|-------------|
| 1 | **Real-time Control** | Remote desktop with minimal latency |
| 2 | **Customizable Capture** | Adjustable tick interval, color/grayscale mode |
| 3 | **Cross-platform** | Java-based, runs on Windows / macOS / Linux |
| 4 | **Clipboard Sharing** | Bidirectional text & file transfer (HTTP chunked upload) |
| 5 | **Multi-monitor** | Select and switch between different screens |
| 6 | **LAN P2P Direct** | Screen data & input bypass the relay server on LAN; transparent auto-fallback to relay on failure |
| 7 | **Zstd Compression** | Configurable level 1–9; level 1 for LAN, higher for WAN |

## Architecture

```
                         ┌────────────────────────────────┐
                         │ MySQL (Clipboard / File Meta)  │
                         └───────────┬─────────┬──────────┘
                                     │         │
                         ┌────────────────────────────────┐
                         │          Server HTTP API       │
                         │          FileController        │
                         └───────────┬─────────┬──────────┘
                                     │         │
                                HTTP upload/download
                                     │         │
┌──────────────┐                   ┌─┴──────────┴─┐                   ┌──────────────┐
│  Controller  │ ◄───────────────► │    Server    │ ◄───────────────► │  Controlled  │
│              │ signaling + relay │   (Relay)    │ signaling + relay │              │
│              │                   │              │                   │              │
│ • render     │                   │ • register   │                   │ • capture    │
│ • input      │                   │ • route      │                   │ • compress   │
│ • P2P        │                   │ • pair       │                   │ • execute    │
│              │                   │              │                   │ • P2P        │
└──────┬───────┘                   └──────────────┘                   └──────┬───────┘
       │                                                                     │
       └─────────────────────────────────────────────────────────────────────┤
          P2P(LAN)screen / input / clipboard text — auto fallback to relay
                                                                             │ Socket
                                                                             ▼
                                                                       ┌────────────┐
                                                                       │   Robots   │
                                                                       │(lock scrn) │
                                                                       └────────────┘

  Robots is used only on Windows lock screen, called by Controlled via Socket.
  Clipboard: text over Netty (relay or P2P); file over HTTP (upload/download
  chunks via FileController, only the file ID is notified through Netty).
```

## Screenshots

### Launcher

![launcher](z_launcher.png)

### Remote Session

![screen](z_screen.png)  ![monitor](z_monitor.png)

### Settings

![capture settings](z_screen_setting.png)  ![compress settings](z_compress_setting.png)  ![clipboard](z_clipboard.png)

## Environment

- **Java** 8 or higher
- **Maven** for dependency management
- **MySQL** for clipboard & file metadata (Server only)

## Quick Start

### 1. Clone & Build

```bash
git clone https://github.com/SpringStudent/remote-desktop-control
cd remote-desktop-control
mvn clean install
```

### 2. Run Server

Import `remote-desktop-control.sql` into MySQL, then edit `application.properties`:

```properties
# Database
spring.datasource.url=jdbc:mysql://localhost:3306/remote_desktop_control
spring.datasource.username=root
spring.datasource.password=your_password

# Netty
netty.server.host=0.0.0.0
netty.server.port=54321
```

```bash
java -jar server/target/server-1.0.0.jar
```

### 3. Run Client

Via command line arguments or an external config file:

```properties
# config.properties
serverIp=192.168.0.110
serverPort=54321
clipboardServer=http://192.168.0.110:12345/remote-desktop-control
robotPort=55678
# Optional — P2P direct connection; omit to auto-detect LAN address & random port
p2pServerIp=192.168.1.100
p2pServerPort=55432
```

```bash
java -DconfigFile=/path/to/config.properties -jar client/target/RemoteClient.jar
```

## Demo

[![Bilibili Video](https://img.shields.io/badge/Bilibili-Demo-blue)](https://www.bilibili.com/video/BV11qNCeNEoZ/)

## Roadmap

- [x] HTTP-based clipboard transfer
- [x] Multi-monitor switching
- [ ] Internationalization (i18n)

## FAQ

<details>
<summary><b>Why does the controlled machine not respond to input?</b></summary>

Run both the controller and the controlled client **as Administrator**. Some applications
on the controlled side cannot be manipulated without elevated privileges.
</details>

<details>
<summary><b>Why are some keys not working correctly?</b></summary>

Set the input language preference on the controller to **"English (United States)"** for
the best control experience.
</details>

<details>
<summary><b>How stable is this project?</b></summary>

The stability has been verified in production environments and is ready for reliable use.
</details>

<details>
<summary><b>Windows lock screen — no screen capture or input?</b></summary>

`java.awt.Robot` cannot work on the Windows lock screen. Use
[windows-lock-helper](https://github.com/SpringStudent/windows-lock-helper) and the
**Robots** module as a workaround. The Robots service is only needed on Windows and only
for lock screen scenarios.
</details>