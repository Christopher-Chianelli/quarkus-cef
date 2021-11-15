package io.quarkiverse.cef;

import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;

@ConfigRoot(name = "cef", phase = ConfigPhase.RUN_TIME)
public class CefRuntimeConfig {

    /**
     * Where to store application resources, data files, and CEF libraries.
     * Defaults to "$APPDATA/quarkus-apps/$APPNAME", where APPDATA is the OS specific directory
     * to store user application data, $APPNAME is ${quarkus.application.name}.
     * $APPNAME and $APPDATA can be used in the expression and will be substituted at runtime.
     */
    @ConfigItem(defaultValue = "$APPDATA/quarkus-apps/$APPNAME")
    public String installDirectory;

    /**
     * What page to open on application start, relative to ${quarkus.cef.resource-root}.
     * Defaults to index.html.
     */
    @ConfigItem(defaultValue = "/index.html")
    public String startPage;

}
