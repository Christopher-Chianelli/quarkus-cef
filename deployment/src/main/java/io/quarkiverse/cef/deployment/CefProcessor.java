package io.quarkiverse.cef.deployment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.commons.codec.digest.DigestUtils;

import io.quarkiverse.cef.CefBuildTimeConfig;
import io.quarkiverse.cef.ProjectResourceHashes;
import io.quarkiverse.cef.ProjectResourcesRecorder;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.runtime.util.ClassPathUtils;

class CefProcessor {

    private static final String FEATURE = "cef";

    @Inject
    CefBuildTimeConfig cefBuildTimeConfig;

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void recordProjectResourcesHashes(
            ProjectResourcesRecorder recorder,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeans) throws IOException {
        Map<String, String> projectResourcePathToHashMap = new HashMap<>();
        computeHashOfResources(cefBuildTimeConfig.resourceRoot, projectResourcePathToHashMap);

        syntheticBeans.produce(SyntheticBeanBuildItem.configure(ProjectResourceHashes.class)
                .scope(ApplicationScoped.class)
                .supplier(recorder.projectResourceHashesSupplier(projectResourcePathToHashMap))
                .done());
    }

    private String getJavaResourcePath(String javaRoot, Path resourceRootPath, Path resourcePath) {
        StringBuilder out = new StringBuilder(javaRoot);
        for (Path part : resourceRootPath.relativize(resourcePath)) {
            out.append('/');
            out.append(part.getFileName());
        }
        return out.toString();
    }

    private void computeHashOfResources(String basePath, Map<String, String> projectResourcePathToHashMap) throws IOException {
        ClassPathUtils.consumeAsPaths(basePath, path -> {
            if (Files.isDirectory(path)) {
                try (Stream<Path> files = Files.walk(path)) {
                    files.forEach(file -> {
                        if (Files.isRegularFile(file)) {
                            projectResourcePathToHashMap.put(getJavaResourcePath(basePath,
                                    path,
                                    file), calculateHash(file));
                        }
                    });
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        });
    }

    private String calculateHash(Path path) {
        try {
            byte[] fileBytes = Files.readAllBytes(path);
            return DigestUtils.sha512Hex(fileBytes);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to calculate hash of file (" + path + ").", e);
        }
    }
}
