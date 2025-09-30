package io.github.springstudent.dekstop.common.remote;

import io.github.springstudent.dekstop.common.command.CmdKeyControl;
import io.github.springstudent.dekstop.common.command.CmdMouseControl;

/**
 * @author ZhouNing
 * @date 2024/12/13 23:35
 **/
public interface RemoteScreenRobot {
    void handleMessage(CmdMouseControl message);
    void handleMessage(CmdKeyControl message);

}
