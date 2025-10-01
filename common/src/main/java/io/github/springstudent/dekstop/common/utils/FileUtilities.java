package io.github.springstudent.dekstop.common.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * @author ZhouNing
 * @date 2024/11/30 9:06
 **/
public final class FileUtilities {

    public static List<File> getFiles(String dirPath) {
        List<File> fileList = new ArrayList<>();
        File dir = new File(dirPath);
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    fileList.add(file);
                }
            }
        }
        return fileList;
    }

}
