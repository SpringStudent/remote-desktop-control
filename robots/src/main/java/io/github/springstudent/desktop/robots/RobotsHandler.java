package io.github.springstudent.desktop.robots;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import io.github.springstudent.dekstop.common.remote.bean.RobotKeyControl;
import io.github.springstudent.dekstop.common.remote.bean.RobotMouseControl;


/**
 * @author ZhouNing
 * @date 2025/9/30 8:40
 **/
public class RobotsHandler {

    public void handleMouseControl(RobotMouseControl message) {
        WinDesktop.INSTANCE.SimulateMouseEventJNA(message.getX(), message.getY(), message.getInfo(), message.getRotations());
    }

    public void handleKeyControl(RobotKeyControl message) {
        WinDesktop.INSTANCE.SimulateKeyEventJNA(message.getKeyCode(), message.getPressed());
    }

    public byte[] captureScreen() throws Exception {
        PointerByReference ptrRef = new PointerByReference();
        IntByReference sizeRef = new IntByReference();

        int result = WinDesktop.INSTANCE.CaptureDesktopToBytesJNA(ptrRef, sizeRef);
        if (result == 1) {
            Pointer dataPtr = ptrRef.getValue();
            int size = sizeRef.getValue();

            byte[] screenBytes = dataPtr.getByteArray(0, size);

            // 释放JNA内存
            WinDesktop.INSTANCE.FreeBytesJNA(dataPtr);
            return screenBytes;
        }
        return null;
    }

}
