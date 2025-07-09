package io.github.springstudent.dekstop.common.command;

/**
 * @author ZhouNing
 * @date 2024/12/11 8:39
 **/
public enum CmdType {
    ReqPing,
    ReqOpen,
    ReqCapture,
    ReqRemoteClipboard,
    ResOpen,
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
