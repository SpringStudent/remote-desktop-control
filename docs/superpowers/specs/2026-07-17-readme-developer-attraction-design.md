# README Developer Attraction Design

## Goal

Make the project immediately understandable and appealing to programmers interested in remote desktop technology, while giving them an accurate path to run and inspect it.

## Audience

Programmers who want to explore, deploy, or learn from a Java-based remote desktop control implementation.

## Scope

Update `README.md` and `README_zh.md` only. Preserve the existing screenshots, demo video, Dayon attribution, and link to the streaming-media alternative project.

## Information Architecture

1. A concise title and value proposition that establish this as a Java, Netty, and Swing remote desktop project.
2. A screenshot near the top so visitors can see the application before reading detailed setup information.
3. A feature list backed by the source tree: remote screen viewing, keyboard and mouse control, configurable capture and compression, multiple monitors, clipboard synchronization, and file transfer.
4. A short architecture section describing the server, client, common, and robots modules and the client-server-client connection model.
5. A quick-start path: prerequisites, clone and build command, SQL import path, server configuration, and the server and client entry points.
6. A configuration reference for the Netty, HTTP, database, and client connection settings.
7. Runtime notes and limitations: matching server addresses, administrator privileges where needed, the Windows lock-screen constraint, and the optional Windows-only robots service.
8. Demo and acknowledgements at the end.

## Accuracy Requirements

- Do not claim unverified performance figures, security properties, or operating-system coverage.
- Correct the clone command to `git clone`.
- Point users to `server/src/main/resources/sql/remote-desktop-control.sql`.
- Use the real entry points: `RemoteServer`, `RemoteClient`, and, when needed, `RobotsServer`.
- State that the client accepts `-DconfigFile` and `-DrobotPort` properties, rather than requiring source edits.
- Keep the Chinese and English content equivalent in meaning.

## Verification

- Check every command, file path, class name, port setting, and module description against the repository.
- Inspect Markdown headings, code fences, local image links, and external links after editing.
- Review the diff to confirm only the README files and this design document are changed.

## Non-Goals

- No code, build, deployment, screenshot, or feature changes.
- No claims of turnkey security, production support, or universal cross-platform behavior.
