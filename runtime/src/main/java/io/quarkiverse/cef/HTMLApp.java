package io.quarkiverse.cef;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.enterprise.context.ApplicationScoped;

import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.CefSettings;
import org.cef.JCefLoader;
import org.cef.OS;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class HTMLApp {

    @ConfigProperty(name = "quarkus.application.name", defaultValue = "TEST")
    String applicationName;

    @ConfigProperty(name = "quarkus.cef.install-directory", defaultValue = "$APPDATA/$APPNAME")
    String cefInstallationDirectory;

    @ConfigProperty(name = "quarkus.cef.start-page", defaultValue = "/META-INF/index.html")
    String startPageResourcePath;

    CefApp cefApp;
    CefClient cefClient;
    Lock windowActiveLock;
    CefClientActiveCondition cefClientActiveCondition;
    Condition windowActiveCondition;

    private String sanitizeName(String name) {
        return URLEncoder.encode(name, Charset.defaultCharset());
    }

    private String getAppDataDirectory() {
        String appDataDirectory;
        if (OS.isWindows()) {
            // Window; use AppData
            appDataDirectory = System.getenv("AppData");
        } else {
            //in either case, we would start in the user's home directory
            String dataHome = System.getenv("XDG_DATA_HOME");
            if (dataHome != null) {
                appDataDirectory = dataHome;
            } else {
                appDataDirectory = System.getProperty("user.home");
                appDataDirectory += "/.local/share";
            }
        }
        return appDataDirectory;
    }

    public HTMLFrame open() {
        ensureInit();
        return createBrowser(startPageResourcePath);
    }

    private void ensureInit() {
        if (shouldInit()) {
            init();
        }
    }

    private boolean shouldInit() {
        return cefApp == null;
    }

    private void init() {
        try {
            String cefInstallationDirectoryForPlatform = cefInstallationDirectory;
            if (OS.isWindows()) {
                // Window; replace "/" with "\"
                cefInstallationDirectoryForPlatform = cefInstallationDirectoryForPlatform.replaceAll("/", "\\\\");
            }
            String installLocation = cefInstallationDirectoryForPlatform.replaceAll("\\$APPDATA", getAppDataDirectory())
                    .replaceAll("\\$APPNAME", sanitizeName(applicationName));
            if (installLocation.equals("/")) {
                throw new IOException("Cannot use / as CEF installation directory: ");
            }
            File installDirectory = new File(installLocation);
            Files.createDirectories(installDirectory.toPath());
            CefSettings settings = new CefSettings();
            settings.windowless_rendering_enabled = false;
            cefApp = JCefLoader.installAndLoadCef(installDirectory, settings);
            cefClient = cefApp.createClient();
            windowActiveLock = new ReentrantLock();
            cefClientActiveCondition = new CefClientActiveCondition(windowActiveLock);
            windowActiveCondition = cefClientActiveCondition.getCondition();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to install CEF", e);
        }
    }

    private HTMLFrame createBrowser(String resource) {
        URL url = Thread.currentThread().getContextClassLoader().getResource(resource);
        if (url == null) {
            throw new IllegalArgumentException("Resource (" + resource + ") was not found.");
        }
        return new HTMLFrame(url.toExternalForm(), cefClient, cefClientActiveCondition);
    }

    public void waitUntilClosed() {
        if (shouldInit()) {
            throw new IllegalStateException("Cef is not initialized! Call open() first.");
        }
        windowActiveLock.lock();
        windowActiveCondition.awaitUninterruptibly();
    }

}
