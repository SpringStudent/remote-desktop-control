package io.github.springstudent.dekstop.common.protocol;

import io.github.springstudent.dekstop.common.command.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

import static java.lang.String.format;

/**
 * @author ZhouNing
 * @date 2024/12/10 20:45
 **/
public class NettyDecoder extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf, List<Object> list) throws Exception {
        if (byteBuf.readableBytes() < 6) {
            return;
        }
        byteBuf.markReaderIndex();
        Cmd.decodeMagicNumber(byteBuf);
        CmdType cmdType = Cmd.decodeEnum(byteBuf, CmdType.class);
        int wireSize = Cmd.decodeWireSize(byteBuf);
        if (byteBuf.readableBytes() < wireSize) {
            byteBuf.resetReaderIndex();
            return;
        }
        switch (cmdType) {
            case ReqPing:
                list.add(new CmdReqPing());
                break;
            case ResPong:
                list.add(new CmdResPong());
                break;
            case ReqCapture:
                list.add(CmdReqCapture.decode(byteBuf));
                break;
            case ResCapture:
                list.add(CmdResCapture.decode(byteBuf));
                break;
            case ResCliInfo:
                list.add(CmdResCliInfo.decode(byteBuf));
                break;
            case Capture:
                list.add(CmdCapture.decode(byteBuf));
                break;
            case CaptureConfig:
                list.add(CmdCaptureConf.decode(byteBuf));
                break;
            case CompressorConfig:
                list.add(CmdCompressorConf.decode(byteBuf));
                break;
            case KeyControl:
                list.add(CmdKeyControl.decode(byteBuf));
                break;
            case MouseControl:
                list.add(CmdMouseControl.decode(byteBuf));
                break;
            case ClipboardText:
                list.add(CmdClipboardText.decode(byteBuf));
                break;
            case ReqRemoteClipboard:
                list.add(new CmdReqRemoteClipboard());
                break;
            case ClipboardTransfer:
                list.add(CmdClipboardTransfer.decode(byteBuf));
                break;
            case ResRemoteClipboard:
                list.add(new CmdResRemoteClipboard());
                break;
            case ReqCliInfo:
                list.add(CmdReqCliInfo.decode(byteBuf));
                break;
            case SelectScreen:
                list.add(CmdSelectScreen.decode(byteBuf));
                break;
            case ChangePwd:
                list.add(CmdChangePwd.decode(byteBuf));
                break;
            case ReqOpen:
                list.add(CmdReqOpen.decode(byteBuf));
                break;
            case ResOpen:
                list.add(CmdResOpen.decode(byteBuf));
                break;
            default:
                throw new IllegalArgumentException(format("unknown cmdType=%s", cmdType));
        }
    }
}
