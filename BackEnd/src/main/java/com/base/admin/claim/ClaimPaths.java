package com.base.admin.claim;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
@RequiredArgsConstructor
public class ClaimPaths {

    private final ClaimProperties properties;

    public Path dataDocs() {
        return resolve(properties.getDataDocs());
    }

    public Path historyDir() {
        return resolve(properties.getHistoryDir());
    }

    private Path resolve(String configured) {
        Path path = Path.of(configured == null || configured.isBlank() ? "." : configured);
        if (!path.isAbsolute()) {
            path = Path.of(System.getProperty("user.dir")).resolve(path);
        }
        return path.normalize();
    }
}
