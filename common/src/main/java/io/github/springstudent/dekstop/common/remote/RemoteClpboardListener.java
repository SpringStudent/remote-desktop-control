package io.github.springstudent.dekstop.common.remote;

import io.github.springstudent.dekstop.common.remote.bean.SendClipboardRequest;
import io.github.springstudent.dekstop.common.remote.bean.SendClipboardResponse;
import io.github.springstudent.dekstop.common.remote.bean.SetClipboardRequest;
import io.github.springstudent.dekstop.common.remote.bean.SetClipboardResponse;

import java.util.concurrent.CompletableFuture;

/**
 * @author ZhouNing
 * @date 2025/12/17 9:06
 **/
public interface RemoteClpboardListener {
    CompletableFuture<SendClipboardResponse> sendClipboard(SendClipboardRequest request);
    CompletableFuture<SetClipboardResponse> setClipboard(SetClipboardRequest request);
}
