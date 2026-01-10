package io.github.colinzhu.jmeter.webrunner.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the persisted state of uploaded file metadata.
 * This is saved to disk to survive application restarts.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileMetadataState {
    @Builder.Default
    private List<File> files = new ArrayList<>();
}
