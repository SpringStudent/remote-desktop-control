package io.github.springstudent.dekstop.client.jni;

import com.sun.jna.*;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import io.github.springstudent.dekstop.client.RemoteClient;

import java.io.*;
import java.nio.file.Files;

/**
 * 加载调用c++编写的dll解决锁屏无法抓图/无法模拟输入的问题
 *
 * @author ZhouNing
 * @date 2025/9/26 10:53
 **/
public interface WinDesktop extends Library {
    WinDesktop INSTANCE = loadWinDesktop();

    boolean IsCurrentInputDesktopJNA();

//    boolean handleOpenInputDesktopJNA();

    int CaptureDesktopToBytesJNA(PointerByReference data, IntByReference size);

    void FreeBytesJNA(Pointer data);

    void SimulateKeyEventJNA(int keyCode, int pressed);

    void SimulateMouseEventJNA(int x, int y, int info, int rotations);

    static WinDesktop loadWinDesktop() {
        if (RemoteClient.getRemoteClient().getOsId() == 'w') {
            try {
                String dllPath = extractDll("/dll/WinDesktop64.dll");
                return Native.loadLibrary(dllPath, WinDesktop.class);
            } catch (Exception e) {
                return null;
            }
        } else {
            return null;
        }
    }

    static String extractDll(String resourcePath) throws IOException {
        InputStream in = WinDesktop.class.getResourceAsStream(resourcePath);
        if (in == null) throw new FileNotFoundException("DLL not found: " + resourcePath);
        File temp = Files.createTempFile("WinDesktop64", ".dll").toFile();
        temp.deleteOnExit();
        try (OutputStream out = new FileOutputStream(temp)) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
        }
        return temp.getAbsolutePath();
    }

    static boolean isWindowsAndLockScreen() {
        if (RemoteClient.getRemoteClient().getOsId() == 'w' && !WinDesktop.INSTANCE.IsCurrentInputDesktopJNA()) {
            return true;
        } else {
            return false;
        }
    }
}