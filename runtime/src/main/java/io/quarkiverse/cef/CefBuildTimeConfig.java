package io.quarkiverse.cef;

import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;

@ConfigRoot(name = "cef", phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public class CefBuildTimeConfig {

    /**
     * Where HTML, CSS and Javascript files are stored in application resources. Defaults to /ui.
     */
    @ConfigItem(defaultValue = "/ui")
    public String resourceRoot;
}
