package io.github.springstudent.dekstop.common.remote;

import io.github.springstudent.dekstop.common.command.Cmd;

import java.util.concurrent.CompletableFuture;

/**
 * @author ZhouNing
 * @date 2025/9/30 9:06
 **/
public interface RemoteClpboardListener {
    CompletableFuture<Byte> sendClipboard();
    CompletableFuture setClipboard(Cmd cmd);
}
