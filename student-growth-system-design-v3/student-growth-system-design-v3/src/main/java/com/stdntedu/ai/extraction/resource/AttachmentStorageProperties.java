package com.stdntedu.ai.extraction.resource;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ai.extraction")
public class AttachmentStorageProperties {
    private Path storageRoot = Path.of(System.getProperty("user.home"), ".stdntedu", "attachments");

    public Path getStorageRoot() { return storageRoot; }
    public void setStorageRoot(Path storageRoot) { this.storageRoot = storageRoot; }
}
