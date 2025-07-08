package io.github.springstudent.dekstop.common.command;

/**
 * @author ZhouNing
 * @date 2024/12/11 8:39
 **/
public enum CmdType {
    ReqPing,
    ReqCapture,
    ReqRemoteClipboard,
    ResCliInfo,
    ResCapture,
    ResPong,
    ResRemoteClipboard,
    Capture,
    CompressorConfig,
    CaptureConfig,
    KeyControl,
    MouseControl,
    ClipboardText,
    ClipboardTransfer,
    ReqCliInfo,
    SelectScreen,
    ChangePwd,
}
