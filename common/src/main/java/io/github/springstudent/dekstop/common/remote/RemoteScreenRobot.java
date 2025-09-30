package io.github.springstudent.dekstop.common.remote;

import io.github.springstudent.dekstop.common.remote.bean.RobotKeyControl;
import io.github.springstudent.dekstop.common.remote.bean.RobotMouseControl;

/**
 * @author ZhouNing
 * @date 2024/12/13 23:35
 **/
public interface RemoteScreenRobot {
    void handleMessage(RobotMouseControl message);

    void handleMessage(RobotKeyControl message);

}
