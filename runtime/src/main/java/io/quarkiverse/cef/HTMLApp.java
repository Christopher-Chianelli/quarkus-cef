package io.quarkiverse.cef;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.CefSettings;
import org.cef.JCefLoader;
import org.cef.OS;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class HTMLApp {

    @Inject
    CefRuntimeConfig cefRuntimeConfig;

    @Inject
    ProjectResourceHashes projectResourceHashes;

    @ConfigProperty(name = "quarkus.application.name", defaultValue = "TEST")
    String applicationName;

    @ConfigProperty(name = "quarkus.cef.resource-root")
    String resourceRoot;

    private static final Logger LOG = Logger.getLogger(HTMLApp.class);

    final String QUARKUS_CEF_MARKER_FILE = ".quarkus-cef-marker-file";
    final String QUARKUS_CEF_RESOURCE_HASHES_FILE = ".quarkus-cef-resource-hashes";
    final String SEPERATOR_CHAR = FileSystems.getDefault().getSeparator();

    Path installPath;
    Path appDataDirectory;
    Path appResourcesDirectory;

    CefApp cefApp;
    CefClient cefClient;

    Lock windowActiveLock;
    CefClientActiveCondition cefClientActiveCondition;
    Condition windowActiveCondition;

    private String sanitizeName(String name) {
        return URLEncoder.encode(name, Charset.defaultCharset());
    }

    private String getSystemDataDirectory() {
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

    /**
     * Return a directory that can be used to store application data. The data persists between separate
     * application run and the location returned is always the same (given the same machine and the
     * same application configuration). The directory is guaranteed to exist and be readable and writable.
     *
     * @return A path to a directory that should be used for persistent app data, never null, always exists.
     */
    public Path getAppDataDirectory() {
        ensureInit();
        return appDataDirectory;
    }

    public HTMLFrame open() {
        ensureInit();
        return createBrowser(cefRuntimeConfig.startPage);
    }

    private void ensureInit() {
        if (shouldInit()) {
            init();
        }
    }

    private boolean shouldInit() {
        return cefApp == null;
    }

    void ensureSafe(File installDirectory) throws IOException {
        Path installPath = installDirectory.toPath();

        if (!installDirectory.exists()) {
            return;
        }

        if (!installDirectory.isDirectory()) {
            throw new IllegalArgumentException("Cannot install application into (" + installPath + ") because " +
                    "(" + installPath + ") exists and is not a directory.");
        }

        List<Path> directoryContents = Files.list(installPath).collect(Collectors.toList());
        if (directoryContents.isEmpty()) {
            return;
        }

        if (!directoryContents.contains(installPath.resolve(QUARKUS_CEF_MARKER_FILE))) {
            throw new IllegalArgumentException("Cannot install application into (" + installPath + ") because " +
                    "(" + installPath + ") exists and is not empty.");
        }
    }

    private void init() {
        try {
            String cefInstallationDirectoryForPlatform = cefRuntimeConfig.installDirectory;
            cefInstallationDirectoryForPlatform = cefInstallationDirectoryForPlatform.replaceAll("/", SEPERATOR_CHAR);
            String installLocation = cefInstallationDirectoryForPlatform.replaceAll("\\$APPDATA", getSystemDataDirectory())
                    .replaceAll("\\$APPNAME", sanitizeName(applicationName));
            File installDirectory = new File(installLocation);
            ensureSafe(installDirectory);

            installPath = installDirectory.toPath();

            Path cefLibs = installPath.resolve("cef-libs");
            appDataDirectory = installPath.resolve("app-data");
            appResourcesDirectory = installPath.resolve("app-resources");

            Files.createDirectories(installPath);
            Files.createDirectories(cefLibs);
            Files.createDirectories(appDataDirectory);
            Files.createDirectories(appResourcesDirectory);
            Files.writeString(installPath.resolve(QUARKUS_CEF_MARKER_FILE), "", StandardOpenOption.TRUNCATE_EXISTING);
            extractResources();

            CefSettings settings = new CefSettings();
            settings.windowless_rendering_enabled = false;
            cefApp = JCefLoader.installAndLoadCef(cefLibs.toFile(), settings);
            cefClient = cefApp.createClient();
            windowActiveLock = new ReentrantLock();
            cefClientActiveCondition = new CefClientActiveCondition(windowActiveLock);
            windowActiveCondition = cefClientActiveCondition.getCondition();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to install CEF.", e);
        }
    }

    private void extractResources() {
        if (!Files.exists(installPath.resolve(QUARKUS_CEF_RESOURCE_HASHES_FILE))) {
            LOG.debug("First run; creating files.");
            for (String resource : projectResourceHashes.getProjectResources()) {
                InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource);
                if (inputStream == null) {
                    throw new IllegalStateException("Classpath resource (" + resource + ") does not exist.");
                }
                Path copyLocation = getResourcePath(resource);
                LOG.debug("Creating (" + copyLocation + ").");
                try {
                    Files.createDirectories(copyLocation.getParent());
                    Files.copy(inputStream, copyLocation, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new IllegalStateException(
                            "Unable to copy classpath resource (" + resource + ") to (" + copyLocation + ").", e);
                }
            }
            writeResourceHashes();
        }
        ProjectResourceHashes oldHashes = readProjectResourceHashes();
        Collection<String> changedFiles = projectResourceHashes.getChangedResources(oldHashes);
        if (changedFiles.isEmpty()) {
            LOG.debug("No changed files detected.");
            return;
        }

        for (String changedFile : changedFiles) {
            Path targetPath = getResourcePath(changedFile);
            try {
                if (Files.exists(targetPath)
                        && !projectResourceHashes.getProjectResourcePathToHashMap().containsKey(changedFile)) {
                    LOG.debug("Deleting (" + targetPath + ") as the file no longer exists.");
                    Files.delete(targetPath);
                } else {
                    LOG.debug("Creating/Replacing (" + targetPath + ") because it has changed since last run.");
                    InputStream resourceInputStream = Thread.currentThread().getContextClassLoader()
                            .getResourceAsStream(changedFile);
                    if (resourceInputStream == null) {
                        throw new IllegalStateException("Unable to find classpath resource (" + changedFile + ").");
                    }
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(resourceInputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Unable to update changed file (" + changedFile + ").", e);
            }
        }
        writeResourceHashes();
    }

    private void writeResourceHashes() {
        String fileData = projectResourceHashes.getResourceToHashStream().map(
                resourceToHashEntry -> resourceToHashEntry.getKey() + "=" + resourceToHashEntry.getValue())
                .collect(Collectors.joining("\n"));

        Path resourceHashes = installPath.resolve(QUARKUS_CEF_RESOURCE_HASHES_FILE);
        try {
            Files.writeString(resourceHashes, fileData);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write file hashes to (" + resourceHashes + ").", e);
        }
    }

    private ProjectResourceHashes readProjectResourceHashes() {
        Path resourceHashesPath = installPath.resolve(QUARKUS_CEF_RESOURCE_HASHES_FILE);
        if (!Files.isRegularFile(resourceHashesPath)) {
            throw new IllegalStateException("Project resource hashes file (" + resourceHashesPath + ") is not a regular file.");
        }
        try {
            List<String> resourceHashesLines = Files.readAllLines(resourceHashesPath);
            Map<String, String> projectResourcePathToHashMap = new HashMap<>();
            for (String resourceHashLine : resourceHashesLines) {
                int pathHashSeperatorIndex = resourceHashLine.lastIndexOf('=');
                if (pathHashSeperatorIndex == -1) {
                    LOG.error("There are errors in (" + resourceHashesPath + "). Assuming all files has changed.");
                    return new ProjectResourceHashes(Map.of());
                }
                String hash = resourceHashLine.substring(pathHashSeperatorIndex + 1);
                String path = resourceHashLine.substring(0, pathHashSeperatorIndex);
                projectResourcePathToHashMap.put(path, hash);
            }
            return new ProjectResourceHashes(projectResourcePathToHashMap);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read project resource hashes file (" + resourceHashesPath + ").", e);
        }
    }

    private Path getResourcePath(String resource) {
        return appResourcesDirectory.resolve(resource.substring(1).replaceAll("/", SEPERATOR_CHAR));
    }

    private HTMLFrame createBrowser(String resource) {
        try {
            String resourceRootPath = resourceRoot;
            if (!resourceRootPath.endsWith("/")) {
                resourceRootPath = resourceRootPath + "/";
            }
            URL url = getResourcePath(resourceRootPath + resource).toUri().toURL();
            return new HTMLFrame(url.toExternalForm(), cefClient, cefClientActiveCondition);
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
    }

    public void waitUntilClosed() {
        if (shouldInit()) {
            throw new IllegalStateException("CEF is not initialized! Call open() first.");
        }
        windowActiveLock.lock();
        windowActiveCondition.awaitUninterruptibly();
    }

}
