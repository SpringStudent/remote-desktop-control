package io.github.springstudent.dekstop.server.file.controller;

import io.github.springstudent.dekstop.server.file.service.FileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * @author ZhouNing
 * @date 2024/12/31 9:33
 **/
@RestController
@RequestMapping("/file")
@ConditionalOnProperty(prefix = "dekstop.server.file", name = "enabled", havingValue = "true", matchIfMissing = false)
public class FileController {
    private static final Logger log = LoggerFactory.getLogger(FileController.class);

    @Resource
    private FileService fileService;

    @GetMapping("/downloadFile")
    public void download(@RequestParam(value = "fileInfoId") String fileInfoId, HttpServletRequest request, HttpServletResponse response) throws Exception {
        try {
            fileService.download(fileInfoId, request, response);
        } catch (Exception e) {
            log.error("download error fileInfoId={}", fileInfoId, e);
            throw e;
        }
    }

    @GetMapping("/quickUploadFile")
    public String quickUploadFile(@RequestParam(value = "md5") String md5, @RequestParam(value = "fileSize") Long fileSize) throws Exception {
        try {
            return fileService.quickUploadFile(md5, fileSize);
        } catch (Exception e) {
            log.error("quickUploadFile error md5={},fileSize={}", md5, fileSize, e);
            throw e;
        }
    }

    @GetMapping("/checkChunk")
    public boolean checkChunk(@RequestParam(value = "md5") String md5,
                              @RequestParam(value = "chunkNo") Integer chunkNo,
                              @RequestParam(value = "chunkSize") Long chunkSize) throws Exception {
        try {
            return fileService.checkChunk(md5, chunkNo, chunkSize);
        } catch (Exception e) {
            log.error("checkChunk error md5={},chunkNo={},chunkSize={}", md5, chunkNo, chunkSize, e);
            throw e;
        }
    }

    @PostMapping(value = "/uploadFileChunk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String uploadFileChunk(@RequestPart(value = "file") MultipartFile file,
                                  @RequestParam(value = "md5") String md5,
                                  @RequestParam(value = "chunkNo") Integer chunkNo,
                                  @RequestParam(value = "fileName") String fileName) throws Exception {
        try {
            return fileService.uploadFileChunk(file, md5, chunkNo, fileName);
        } catch (Exception e) {
            log.error("uploadFileChunk error md5={},chunkNo={},fileName={}", md5, chunkNo, fileName, e);
            throw e;
        }
    }

    @PostMapping(value = "/deleteFile")
    public void deleteFile(@RequestBody List<String> fileInfoIds) throws Exception {
        try {
            fileService.deleteFile(fileInfoIds);
        } catch (Exception e) {
            log.error("deleteFile error fileInfoIds={}", fileInfoIds, e);
            throw e;
        }
    }

}
