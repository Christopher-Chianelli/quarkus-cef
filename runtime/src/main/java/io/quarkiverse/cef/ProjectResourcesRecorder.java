package io.quarkiverse.cef;

import java.util.Map;
import java.util.function.Supplier;

import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class ProjectResourcesRecorder {
    public Supplier<ProjectResourceHashes> projectResourceHashesSupplier(Map<String, String> projectResourcePathToHashMap) {
        return () -> new ProjectResourceHashes(projectResourcePathToHashMap);
    }
}
