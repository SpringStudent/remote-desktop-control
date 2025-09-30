package io.github.springstudent.desktop.robots;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import io.github.springstudent.dekstop.common.bean.FileInfo;
import io.github.springstudent.dekstop.common.bean.RemoteClipboard;
import io.github.springstudent.dekstop.common.bean.TransferableFiles;
import io.github.springstudent.dekstop.common.command.CmdResRemoteClipboard;
import io.github.springstudent.dekstop.common.log.Log;
import io.github.springstudent.dekstop.common.remote.RemoteClpboardListener;
import io.github.springstudent.dekstop.common.remote.RemoteScreenRobot;
import io.github.springstudent.dekstop.common.remote.bean.*;
import io.github.springstudent.dekstop.common.utils.EmptyUtils;
import io.github.springstudent.dekstop.common.utils.FileUtilities;
import io.github.springstudent.dekstop.common.utils.RemoteUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.InputEvent;
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
 * @date 2025/9/30 8:40
 **/
public class RemoteRobotsImpl implements RemoteScreenRobot, RemoteClpboardListener, ClipboardOwner {

    private Robot robot;

    private String rootDir;

    private String uploadDir;

    private String downloadDir;

    public RemoteRobotsImpl() {
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
        try {
            robot = new Robot();
            robot.setAutoDelay(1);
        } catch (AWTException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public CompletableFuture<SendClipboardResponse> sendClipboard(SendClipboardRequest request) {
        CompletableFuture result = null;
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        //兼容mac javaFileListFlavor必须放在第一位
        if (clipboard.isDataFlavorAvailable(DataFlavor.javaFileListFlavor)) {
            result = CompletableFuture.supplyAsync(() -> {
                java.util.List<File> files = null;
                try {
                    files = (java.util.List<File>) clipboard.getData(DataFlavor.javaFileListFlavor);
                } catch (Exception e) {
                    Log.error("clipboard.getData(DataFlavor.javaFileListFlavor)", e);
                    return new SendClipboardResponse(CmdResRemoteClipboard.CLIPBOARD_GETDATA_ERROR, "file", null, request.getId());
                }
                if (!files.isEmpty()) {
                    final List<File> finalFiles = files;
                    try {
                        doSendClipboard(request, finalFiles);
                        return new SendClipboardResponse(CmdResRemoteClipboard.OK, "file", request.getDeviceCode(), request.getId());
                    } catch (Exception e) {
                        Log.error("send clipboardFiles error", e);
                        return new SendClipboardResponse(CmdResRemoteClipboard.CLIPBOARD_SENDDATA_ERROR, "file", request.getDeviceCode(), request.getId());
                    }
                } else {
                    return new SendClipboardResponse(CmdResRemoteClipboard.CLIPBOARD_GETDATA_EMPTY, "file", request.getDeviceCode(), request.getId());
                }
            });
        } else if (clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)) {
            result = CompletableFuture.supplyAsync(() -> {
                BufferedImage image = null;
                try {
                    image = (BufferedImage) clipboard.getData(DataFlavor.imageFlavor);
                } catch (Exception e) {
                    Log.error("clipboard.getData(DataFlavor.imageFlavor) error", e);
                    return new SendClipboardResponse(CmdResRemoteClipboard.CLIPBOARD_GETDATA_ERROR, "file", null, request.getId());
                }
                final BufferedImage clipboardImage = image;
                if (image != null) {
                    File outputFile = null;
                    try {
                        outputFile = new File(this.rootDir + File.separator + IdUtil.fastSimpleUUID() + ".png");
                        ImageIO.write(clipboardImage, "png", outputFile);
                        doSendClipboard(request, Arrays.asList(outputFile));
                        return new SendClipboardResponse(CmdResRemoteClipboard.OK, "file", request.getDeviceCode(), request.getId());
                    } catch (Exception e) {
                        Log.error("send clipboardImage error", e);
                        return new SendClipboardResponse(CmdResRemoteClipboard.CLIPBOARD_SENDDATA_ERROR, "file", null, request.getId());
                    } finally {
                        if (outputFile != null) {
                            FileUtil.del(outputFile);
                        }
                    }
                } else {
                    return new SendClipboardResponse(CmdResRemoteClipboard.CLIPBOARD_GETDATA_EMPTY, "file", null, request.getId());
                }
            });
        } else if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
            result = CompletableFuture.supplyAsync(() -> {
                String text = null;
                try {
                    text = (String) clipboard.getData(DataFlavor.stringFlavor);
                } catch (Exception e) {
                    Log.error("clipboard.getData(DataFlavor.stringFlavor) error", e);
                    return new SendClipboardResponse(CmdResRemoteClipboard.CLIPBOARD_GETDATA_ERROR, "text", null, request.getId());
                }
                if (EmptyUtils.isNotEmpty(text)) {
                    final String finalText = text;
                    return new SendClipboardResponse(CmdResRemoteClipboard.OK, "text", finalText, request.getId());
                } else {
                    return new SendClipboardResponse(CmdResRemoteClipboard.CLIPBOARD_GETDATA_EMPTY, "text", null, request.getId());
                }
            });
        } else {
            result = CompletableFuture.supplyAsync(() -> new SendClipboardResponse(CmdResRemoteClipboard.CLIPBOARD_DATA_NOTSUPPORT, "unknown", null, request.getId()));
        }
        return result;
    }

    private void doSendClipboard(SendClipboardRequest sendClipboardRequest, List<File> files) throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put(REQUEST_URL_KEY, sendClipboardRequest.getClipboardServer());
        RemoteUtils.clearClipboard(sendClipboardRequest.getDeviceCode(), map);
        List<RemoteClipboard> remoteClipboards = new ArrayList<>();
        for (File file : files) {
            processFile(sendClipboardRequest, file, null, remoteClipboards);
        }
        RemoteUtils.saveClipboard(remoteClipboards, map);
    }

    private void processFile(SendClipboardRequest sendClipboardRequest, File file, String filePid, List<RemoteClipboard> remoteClipboards) throws Exception {
        if (file.isFile()) {
            Map<String, Object> map = new HashMap<>();
            map.put(REQUEST_URL_KEY, sendClipboardRequest.getClipboardServer());
            map.put(TMP_PATH_KEY, uploadDir);
            FileInfo fileInfo = RemoteUtils.uploadFile(file, map);
            //添加文件
            RemoteClipboard remoteClipboard = new RemoteClipboard();
            remoteClipboard.setId(IdUtil.fastSimpleUUID());
            remoteClipboard.setIsFile(1);
            remoteClipboard.setFileName(fileInfo.getFileName());
            remoteClipboard.setFilePid(filePid);
            remoteClipboard.setDeviceCode(sendClipboardRequest.getDeviceCode());
            remoteClipboard.setFileInfoId(fileInfo.getFileUuid());
            remoteClipboards.add(remoteClipboard);
        } else {
            RemoteClipboard remoteClipboard = new RemoteClipboard();
            remoteClipboard.setId(IdUtil.fastSimpleUUID());
            remoteClipboard.setIsFile(0);
            remoteClipboard.setFileName(FileUtil.getName(file));
            remoteClipboard.setFilePid(filePid);
            remoteClipboard.setDeviceCode(sendClipboardRequest.getDeviceCode());
            remoteClipboards.add(remoteClipboard);
            File[] filesArray = file.listFiles();
            if (filesArray != null) {
                for (File node : filesArray) {
                    processFile(sendClipboardRequest, node, remoteClipboard.getId(), remoteClipboards);
                }
            }
        }
    }

    @Override
    public CompletableFuture<SetClipboardResponse> setClipboard(SetClipboardRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (request.getClipboardType().equals("text")) {
                    StringSelection stringSelection = new StringSelection((request.getContent()));
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, this);
                } else if (request.getClipboardType().equals("file")) {
                    String deviceCode = request.getContent();
                    Map<String, Object> map = new HashMap<>();
                    map.put(REQUEST_URL_KEY, request.getClipboardServer());
                    List<RemoteClipboard> remoteClipboards = RemoteUtils.getClipboard(deviceCode, map);
                    if (EmptyUtils.isNotEmpty(remoteClipboards)) {
                        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new TransferableFiles(processClipboard(request, remoteClipboards)), this);
                    }
                }
                return new SetClipboardResponse(true, request.getId());
            } catch (Exception e) {
                Log.error("setClipboard error", e);
                return new SetClipboardResponse(false, request.getId());
            }
        });
    }

    private List<File> processClipboard(SetClipboardRequest setClipboardRequest, List<RemoteClipboard> remoteClipboards) throws Exception {
        String fileName = IdUtil.fastSimpleUUID();
        String tmpDir = downloadDir + File.separator + fileName;
        FileUtil.mkdir(tmpDir);
        RemoteClipboard remoteClipboard = new RemoteClipboard();
        remoteClipboard.setFileName(fileName);
        remoteClipboard.setIsFile(0);
        remoteClipboard.setChilds(remoteClipboards);
        downloadClipboardFile(setClipboardRequest, tmpDir, remoteClipboard);
        return FileUtilities.getFiles(tmpDir);
    }

    private void downloadClipboardFile(SetClipboardRequest setClipboardRequest, String tmpDir, RemoteClipboard remoteClipboard) throws Exception {
        List<RemoteClipboard> childs = remoteClipboard.getChilds();
        if (EmptyUtils.isNotEmpty(childs)) {
            final String finalPath = tmpDir;
            childs.stream().forEach(dwp -> dwp.setFileName(finalPath + File.separator + dwp.getFileName()));
            for (int i = 0; i < childs.size(); i++) {
                RemoteClipboard param = childs.get(i);
                if (param.getIsFile() == 0) {
                    FileUtil.mkdir(param.getFileName());
                    downloadClipboardFile(setClipboardRequest, param.getFileName(), param);
                } else {
                    File tmpFile = new File(param.getFileName());
                    Map<String, Object> map = new HashMap<>();
                    map.put(REQUEST_URL_KEY, setClipboardRequest.getClipboardServer());
                    map.put(TMP_PATH_KEY, tmpFile);
                    RemoteUtils.downloadUrlFile(param.getFileInfoId(), map);
                }
            }
        }
    }

    @Override
    public void handleMessage(RobotMouseControl message) {
        if (message.isPressed()) {
            if (message.isButton1()) {
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            } else if (message.isButton2()) {
                robot.mousePress(InputEvent.BUTTON2_DOWN_MASK);
            } else if (message.isButton3()) {
                robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
            }
        } else if (message.isReleased()) {
            if (message.isButton1()) {
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            } else if (message.isButton2()) {
                robot.mouseRelease(InputEvent.BUTTON2_DOWN_MASK);
            } else if (message.isButton3()) {
                robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
            }
        } else if (message.isWheel()) {
            robot.mouseWheel(message.getRotations());
        }
        int x = message.getX();
        int y = message.getY();
        robot.mouseMove(x, y);
    }

    @Override
    public void handleMessage(RobotKeyControl message) {
        if (message.getPressed()) {
            robot.keyPress(message.getKeyCode());
        } else {
            robot.keyRelease(message.getKeyCode());
        }
    }

    @Override
    public void lostOwnership(Clipboard clipboard, Transferable contents) {
        Log.warn("lostOwnership...");
    }
}
