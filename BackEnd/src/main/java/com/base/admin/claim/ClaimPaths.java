package com.base.admin.claim;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ClaimPaths {

    private final ClaimProperties properties;

    public Path dataDocs() {
        return resolve(properties.getDataDocs());
    }

    /**
     * 客诉 Excel 目录。本机从 BackEnd 启动是 ../docs；ECS 工作目录是 /opt/xby-admin，
     * 发布脚本会把表放到 ./docs，这里按实际有没有客诉表来选。
     */
    public Path historyDir() {
        Path configured = resolve(properties.getHistoryDir());
        if (hasHistoryExcel(configured)) {
            return configured;
        }
        for (Path candidate : historyCandidates(configured)) {
            if (hasHistoryExcel(candidate)) {
                return candidate;
            }
        }
        return configured;
    }

    private List<Path> historyCandidates(Path configured) {
        List<Path> list = new ArrayList<>();
        Path cwd = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        list.add(configured);
        list.add(cwd.resolve("docs"));
        list.add(cwd.resolve("..").resolve("docs").normalize());
        list.add(Path.of("/opt/xby-admin/docs"));
        return list;
    }

    private boolean hasHistoryExcel(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return false;
        }
        try (var files = Files.list(dir)) {
            return files.anyMatch(path -> {
                String name = path.getFileName().toString();
                return name.endsWith(".xlsx") && name.contains("客诉") && !name.startsWith(".~");
            });
        } catch (Exception ex) {
            return false;
        }
    }

    private Path resolve(String configured) {
        Path path = Path.of(configured == null || configured.isBlank() ? "." : configured);
        if (!path.isAbsolute()) {
            path = Path.of(System.getProperty("user.dir")).resolve(path);
        }
        return path.normalize();
    }
}
