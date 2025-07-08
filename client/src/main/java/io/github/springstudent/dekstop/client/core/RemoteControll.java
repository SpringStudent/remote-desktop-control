package io.github.springstudent.dekstop.client.core;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import io.github.springstudent.dekstop.client.RemoteClient;
import io.github.springstudent.dekstop.client.bean.TransferableFiles;
import io.github.springstudent.dekstop.client.utils.FileUtilities;
import io.github.springstudent.dekstop.common.bean.FileInfo;
import io.github.springstudent.dekstop.common.bean.RemoteClipboard;
import io.github.springstudent.dekstop.common.command.*;
import io.github.springstudent.dekstop.common.log.Log;
import io.github.springstudent.dekstop.common.utils.EmptyUtils;
import io.github.springstudent.dekstop.common.utils.NettyUtils;
import io.github.springstudent.dekstop.common.utils.RemoteUtils;
import io.netty.channel.Channel;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static io.github.springstudent.dekstop.common.utils.RemoteUtils.REQUEST_URL_KEY;
import static io.github.springstudent.dekstop.common.utils.RemoteUtils.TMP_PATH_KEY;
import static java.lang.System.getProperty;

/**
 * @author ZhouNing
 * @date 2024/12/10 14:20
 **/
public abstract class RemoteControll implements ClipboardOwner {

    protected Channel channel;

    private String rootDir;

    private String uploadDir;

    private String downloadDir;

    public RemoteControll() {
        this.rootDir = getProperty("java.io.tmpdir") + File.separator + "remoteDeskopControll";
        if (FileUtil.exist(rootDir)) {
            FileUtil.clean(rootDir);
        } else {
            FileUtil.mkdir(rootDir);
        }
        this.uploadDir = rootDir + File.separator + "upload";
        if (!FileUtil.exist(uploadDir)) {
            FileUtil.mkdir(uploadDir);
        }
        this.downloadDir = rootDir + File.separator + "download";
        if (!FileUtil.exist(downloadDir)) {
            FileUtil.mkdir(downloadDir);
        }
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public void fireCmd(Cmd cmd) {
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(cmd);
        } else {
            Log.error("client fireCmd error,please check network connect");
        }
    }

    protected void showMessageDialog(Object msg, int messageType) {
        SwingUtilities.invokeLater(() -> RemoteClient.getRemoteClient().showMessageDialog(msg, messageType));
    }

    protected CompletableFuture<Byte> sendClipboard() {
        CompletableFuture result = null;
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        //兼容mac javaFileListFlavor必须放在第一位
        if (clipboard.isDataFlavorAvailable(DataFlavor.javaFileListFlavor)) {
            result = CompletableFuture.supplyAsync(() -> {
                List<File> files = null;
                try {
                    files = (List<File>) clipboard.getData(DataFlavor.javaFileListFlavor);
                } catch (Exception e) {
                    Log.error("clipboard.getData(DataFlavor.javaFileListFlavor)", e);
                    return CmdResRemoteClipboard.CLIPBOARD_GETDATA_ERROR;
                }
                if (!files.isEmpty()) {
                    final List<File> finalFiles = files;
                    try {
                        doSendClipboard(finalFiles);
                        return CmdResRemoteClipboard.OK;
                    } catch (Exception e) {
                        Log.error("send clipboardFiles error", e);
                        return CmdResRemoteClipboard.CLIPBOARD_SENDDATA_ERROR;
                    }
                } else {
                    return CmdResRemoteClipboard.CLIPBOARD_GETDATA_EMPTY;
                }
            });
        } else if (clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)) {
            result = CompletableFuture.supplyAsync(() -> {
                BufferedImage image = null;
                try {
                    image = (BufferedImage) clipboard.getData(DataFlavor.imageFlavor);
                } catch (Exception e) {
                    Log.error("clipboard.getData(DataFlavor.imageFlavor) error", e);
                    return CmdResRemoteClipboard.CLIPBOARD_GETDATA_ERROR;
                }
                final BufferedImage clipboardImage = image;
                if (image != null) {
                    File outputFile = null;
                    try {
                        outputFile = new File(this.rootDir + File.separator + IdUtil.fastSimpleUUID() + ".png");
                        ImageIO.write(clipboardImage, "png", outputFile);
                        doSendClipboard(Arrays.asList(outputFile));
                        return CmdResRemoteClipboard.OK;
                    } catch (Exception e) {
                        Log.error("send clipboardImage error", e);
                        return CmdResRemoteClipboard.CLIPBOARD_SENDDATA_ERROR;
                    } finally {
                        if (outputFile != null) {
                            FileUtil.del(outputFile);
                        }
                    }
                } else {
                    return CmdResRemoteClipboard.CLIPBOARD_GETDATA_EMPTY;
                }
            });
        } else if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
            result = CompletableFuture.supplyAsync(() -> {
                String text = null;
                try {
                    text = (String) clipboard.getData(DataFlavor.stringFlavor);
                } catch (Exception e) {
                    Log.error("clipboard.getData(DataFlavor.stringFlavor) error", e);
                    return CmdResRemoteClipboard.CLIPBOARD_GETDATA_ERROR;
                }
                if (EmptyUtils.isNotEmpty(text)) {
                    final String finalText = text;
                    fireCmd(new CmdClipboardText(finalText, getType()));
                    return CmdResRemoteClipboard.OK;
                } else {
                    return CmdResRemoteClipboard.CLIPBOARD_GETDATA_EMPTY;
                }
            });
        } else {
            result = CompletableFuture.supplyAsync(() -> CmdResRemoteClipboard.CLIPBOARD_DATA_NOTSUPPORT);
        }
        return result;
    }

    private void doSendClipboard(List<File> files) throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put(REQUEST_URL_KEY, RemoteClient.getRemoteClient().getClipboardServer());
        RemoteUtils.clearClipboard(getDeviceCode(), map);
        List<RemoteClipboard> remoteClipboards = new ArrayList<>();
        for (File file : files) {
            processFile(file, null, remoteClipboards);
        }
        RemoteUtils.saveClipboard(remoteClipboards, map);
        fireCmd(new CmdClipboardTransfer(getDeviceCode(), getType()));
    }

    private void processFile(File file, String filePid, List<RemoteClipboard> remoteClipboards) throws Exception {
        if (file.isFile()) {
            Map<String, Object> map = new HashMap<>();
            map.put(REQUEST_URL_KEY, RemoteClient.getRemoteClient().getClipboardServer());
            map.put(TMP_PATH_KEY, uploadDir);
            FileInfo fileInfo = RemoteUtils.uploadFile(file, map);
            //添加文件
            RemoteClipboard remoteClipboard = new RemoteClipboard();
            remoteClipboard.setId(IdUtil.fastSimpleUUID());
            remoteClipboard.setIsFile(1);
            remoteClipboard.setFileName(fileInfo.getFileName());
            remoteClipboard.setFilePid(filePid);
            remoteClipboard.setDeviceCode(getDeviceCode());
            remoteClipboard.setFileInfoId(fileInfo.getFileUuid());
            remoteClipboards.add(remoteClipboard);
        } else {
            RemoteClipboard remoteClipboard = new RemoteClipboard();
            remoteClipboard.setId(IdUtil.fastSimpleUUID());
            remoteClipboard.setIsFile(0);
            remoteClipboard.setFileName(FileUtil.getName(file));
            remoteClipboard.setFilePid(filePid);
            remoteClipboard.setDeviceCode(getDeviceCode());
            remoteClipboards.add(remoteClipboard);
            File[] filesArray = file.listFiles();
            if (filesArray != null) {
                for (File node : filesArray) {
                    processFile(node, remoteClipboard.getId(), remoteClipboards);
                }
            }
        }
    }

    private String getDeviceCode() {
        if (channel != null) {
            String deviceCode = NettyUtils.getDeviceCode(this.channel);
            if (EmptyUtils.isEmpty(deviceCode)) {
                throw new IllegalStateException("cannot get device code,please check client connect status");
            } else {
                return deviceCode;
            }
        } else {
            throw new IllegalStateException("cannot get device code,please check client connect status");
        }
    }

    protected CompletableFuture setClipboard(Cmd cmd) {
        return CompletableFuture.runAsync(() -> {
            try {
                if (cmd.getType().equals(CmdType.ClipboardText)) {
                    StringSelection stringSelection = new StringSelection(((CmdClipboardText) cmd).getPayload());
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, this);
                } else if (cmd.getType().equals(CmdType.ClipboardTransfer)) {
                    String deviceCode = ((CmdClipboardTransfer) cmd).getDeviceCode();
                    Map<String, Object> map = new HashMap<>();
                    map.put(REQUEST_URL_KEY, RemoteClient.getRemoteClient().getClipboardServer());
                    List<RemoteClipboard> remoteClipboards = RemoteUtils.getClipboard(deviceCode, map);
                    if (EmptyUtils.isNotEmpty(remoteClipboards)) {
                        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new TransferableFiles(processClipboard(remoteClipboards)), this);
                    }
                }
            } catch (Exception e) {
                Log.error("setClipboard error", e);
            }
        });
    }

    protected boolean needSetClipboard(Cmd cmd) {
        if (cmd.getType().equals(CmdType.ClipboardText)) {
            return !((CmdClipboardText) cmd).getControlType().equals(getType());
        } else if (cmd.getType().equals(CmdType.ClipboardTransfer)) {
            return !((CmdClipboardTransfer) cmd).getControlType().equals(getType());
        }
        return false;
    }

    private List<File> processClipboard(List<RemoteClipboard> remoteClipboards) throws Exception {
        String fileName = IdUtil.fastSimpleUUID();
        String tmpDir = downloadDir + File.separator + fileName;
        FileUtil.mkdir(tmpDir);
        RemoteClipboard remoteClipboard = new RemoteClipboard();
        remoteClipboard.setFileName(fileName);
        remoteClipboard.setIsFile(0);
        remoteClipboard.setChilds(remoteClipboards);
        downloadClipboardFile(tmpDir, remoteClipboard);
        return FileUtilities.getFiles(tmpDir);
    }

    private void downloadClipboardFile(String tmpDir, RemoteClipboard remoteClipboard) throws Exception {
        List<RemoteClipboard> childs = remoteClipboard.getChilds();
        if (EmptyUtils.isNotEmpty(childs)) {
            final String finalPath = tmpDir;
            childs.stream().forEach(dwp -> dwp.setFileName(finalPath + File.separator + dwp.getFileName()));
            for (int i = 0; i < childs.size(); i++) {
                RemoteClipboard param = childs.get(i);
                if (param.getIsFile() == 0) {
                    FileUtil.mkdir(param.getFileName());
                    downloadClipboardFile(param.getFileName(), param);
                } else {
                    File tmpFile = new File(param.getFileName());
                    Map<String, Object> map = new HashMap<>();
                    map.put(REQUEST_URL_KEY, RemoteClient.getRemoteClient().getClipboardServer());
                    map.put(TMP_PATH_KEY, tmpFile);
                    RemoteUtils.downloadUrlFile(param.getFileInfoId(), map);
                }
            }
        }
    }


    public abstract void handleCmd(Cmd cmd);

    public abstract String getType();

    public void start() {
    }

    public void stop() {
    }

    @Override
    public void lostOwnership(Clipboard clipboard, Transferable contents) {
        Log.info("lostOwnership ....");
    }
}
