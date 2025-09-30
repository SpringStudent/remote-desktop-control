package io.github.springstudent.desktop.robots;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

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

    WinDesktop NULL_INSTANCE = new WinDesktop() {
        @Override
        public boolean IsCurrentInputDesktopJNA() {
            return false;
        }

        @Override
        public int CaptureDesktopToBytesJNA(PointerByReference data, IntByReference size) {
            return 0;
        }

        @Override
        public void FreeBytesJNA(Pointer data) {

        }

        @Override
        public void SimulateKeyEventJNA(int keyCode, int pressed) {

        }

        @Override
        public void SimulateMouseEventJNA(int x, int y, int info, int rotations) {

        }
    };

    boolean IsCurrentInputDesktopJNA();

//    boolean handleOpenInputDesktopJNA();

    int CaptureDesktopToBytesJNA(PointerByReference data, IntByReference size);

    void FreeBytesJNA(Pointer data);

    void SimulateKeyEventJNA(int keyCode, int pressed);

    void SimulateMouseEventJNA(int x, int y, int info, int rotations);

    static WinDesktop loadWinDesktop() {
        if (RobotsServer.getOsId() == 'w') {
            try {
                String dllPath = extractDll("/dll/WinDesktop64.dll");
                return Native.loadLibrary(dllPath, WinDesktop.class);
            } catch (Exception e) {
                return NULL_INSTANCE;
            }
        } else {
            return NULL_INSTANCE;
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

}