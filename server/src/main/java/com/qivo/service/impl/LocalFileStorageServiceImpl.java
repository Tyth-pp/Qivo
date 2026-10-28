package com.qivo.service.impl;

import com.qivo.constant.FileMessageConstant;
import com.qivo.exception.BusinessException;
import com.qivo.properties.FileUploadProperties;
import com.qivo.result.ErrorCode;
import com.qivo.service.FileStorageService;
import com.qivo.utils.AvatarFiles;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 本地文件存储实现，保持现有上传校验与路径语义。
 */
@Slf4j
@Service
public class LocalFileStorageServiceImpl implements FileStorageService {

    private final String uploadPath;
    private final String allowTypes;

    public LocalFileStorageServiceImpl(FileUploadProperties properties) {
        this.uploadPath = Objects.requireNonNull(properties.getPath());
        this.allowTypes = Objects.requireNonNull(properties.getAllowTypes());
    }

    /**
     * 上传文件，返回访问路径
     */
    @Override
    public String upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.FILE_UPLOAD_FAILED.getCode(), FileMessageConstant.FILE_EMPTY);
        }

        // 校验文件类型
        String originalName = file.getOriginalFilename();
        if (originalName == null) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        }
        String suffix = originalName.substring(originalName.lastIndexOf(".") + 1).toLowerCase();
        List<String> allowList = Arrays.asList(allowTypes.split(","));
        if (!allowList.contains(suffix)) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED);
        }

        return store(file, "", suffix);
    }

    @Override
    public String uploadAvatar(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > AvatarFiles.MAX_BYTES)
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED);
        try {
            String suffix = AvatarFiles.suffix(file.getOriginalFilename(), file.getBytes());
            return store(file, "avatars" + File.separator, suffix);
        } catch (IOException e) { throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED); }
    }

    private String store(MultipartFile file, String folder, String suffix) {
        // 生成新文件名：yyyy/MM/dd/uuid.suffix
        String datePath = LocalDate.now().toString().replace("-", File.separator);
        String newFileName = folder + datePath + File.separator + UUID.randomUUID() + "." + suffix;

        // 写入磁盘
        File dest = new File(uploadPath + newFileName);
        if (!dest.getParentFile().exists() && !dest.getParentFile().mkdirs()) {
            throw new BusinessException(
                    ErrorCode.FILE_UPLOAD_FAILED.getCode(),
                    FileMessageConstant.DIRECTORY_CREATE_FAILED);
        }
        try {
            file.transferTo(dest);
        } catch (IOException e) {
            log.error("文件写入失败: {}", dest.getAbsolutePath(), e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        }

        return "/upload/" + newFileName.replace(File.separator, "/");
    }
}
