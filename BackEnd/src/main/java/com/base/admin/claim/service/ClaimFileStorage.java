package com.base.admin.claim.service;

import com.base.admin.claim.ClaimPaths;
import com.base.admin.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClaimFileStorage {

    private final ClaimPaths paths;

    public void ensureRoot() {
        try {
            Files.createDirectories(paths.dataDocs());
        } catch (IOException ex) {
            throw new BusinessException("无法创建证据目录 " + paths.dataDocs());
        }
    }

    public String save(Long caseId, String fileName, byte[] bytes) {
        ensureRoot();
        String ext = extension(fileName);
        String relative = caseId + "/" + UUID.randomUUID().toString().replace("-", "") + ext;
        Path target = resolve(relative);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException ex) {
            throw new BusinessException("证据文件保存失败");
        }
        return relative;
    }

    public void deleteCaseDir(Long caseId) {
        if (caseId == null) {
            return;
        }
        Path dir = resolve(String.valueOf(caseId));
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var files = Files.walk(dir)) {
            files.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    throw new BusinessException("证据文件删除失败");
                }
            });
        } catch (IOException ex) {
            throw new BusinessException("证据文件删除失败");
        }
    }

    public Path resolve(String relative) {
        Path root = paths.dataDocs().toAbsolutePath().normalize();
        Path target = root.resolve(relative == null ? "" : relative).normalize();
        if (!target.startsWith(root)) {
            throw new BusinessException("证据路径无效");
        }
        return target;
    }

    private String extension(String fileName) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1 && name.length() - dot <= 8) {
            return name.substring(dot);
        }
        return "";
    }
}
