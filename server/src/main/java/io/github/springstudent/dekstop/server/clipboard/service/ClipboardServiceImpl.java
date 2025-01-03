package io.github.springstudent.dekstop.server.clipboard.service;

import com.gysoft.jdbc.bean.Criteria;
import io.github.springstudent.dekstop.common.utils.EmptyUtils;
import io.github.springstudent.dekstop.server.clipboard.dao.ClipboardDao;
import io.github.springstudent.dekstop.server.clipboard.pojo.Clipboard;
import io.github.springstudent.dekstop.server.file.service.FileService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author ZhouNing
 * @date 2024/12/31 16:19
 **/
@Service
public class ClipboardServiceImpl implements ClipboardService {

    @Resource
    private ClipboardDao clipboardDao;

    @Resource
    private FileService fileService;

    @Override
    public void clear(String deviceCode) throws Exception {
        List<Clipboard> clipboards = clipboardDao.queryWithCriteria(new Criteria().where(Clipboard::getDeviceCode, deviceCode));
        if (EmptyUtils.isNotEmpty(clipboards)) {
            clipboardDao.deleteWithCriteria(new Criteria().where(Clipboard::getDeviceCode, deviceCode));
            fileService.deleteFile(clipboards.stream().filter(clipboard -> EmptyUtils.isNotEmpty(clipboard.getFileInfoId())).map(Clipboard::getFileInfoId).collect(Collectors.toList()));
        }
    }

    @Override
    public void save(List<Clipboard> clipboards) throws Exception {
        clipboardDao.saveAll(clipboards);
    }

    @Override
    public List<Clipboard> get(String deviceCode) throws Exception {
        return clipboardDao.queryWithCriteria(new Criteria().where(Clipboard::getDeviceCode, deviceCode));
    }
}
