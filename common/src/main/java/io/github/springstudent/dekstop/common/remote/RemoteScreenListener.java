package io.github.springstudent.dekstop.common.remote;

import io.github.springstudent.dekstop.common.bean.Listener;

/**
 * @author ZhouNing
 * @date 2024/12/9 8:40
 **/
public interface RemoteScreenListener extends Listener {

    void onMouseMove(int x, int y);

    void onMousePressed(int x, int y, int button);

    void onMouseReleased(int x, int y, int button);

    void onMouseWheeled(int x, int y, int rotations);

    void onKeyPressed(int keyCode, char keyChar);

    void onKeyReleased(int keyCode, char keyChar);
}
