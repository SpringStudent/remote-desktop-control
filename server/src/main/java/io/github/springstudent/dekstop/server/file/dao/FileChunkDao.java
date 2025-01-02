package io.github.springstudent.dekstop.server.file.dao;

import com.gysoft.jdbc.dao.EntityDao;
import io.github.springstudent.dekstop.server.file.pojo.FileChunk;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author ZhouNing
 * @date 2024/12/31 9:29
 **/
public interface FileChunkDao extends EntityDao<FileChunk,String> {

    void uploadFile(FileChunk fileChunk, MultipartFile chunk)throws Exception;
}
