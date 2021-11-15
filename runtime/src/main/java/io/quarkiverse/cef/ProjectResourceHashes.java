package io.quarkiverse.cef;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public class ProjectResourceHashes {
    Map<String, String> projectResourcePathToHashMap;

    ProjectResourceHashes(Map<String, String> projectResourcePathToHashMap) {
        this.projectResourcePathToHashMap = projectResourcePathToHashMap;
    }

    public Map<String, String> getProjectResourcePathToHashMap() {
        return projectResourcePathToHashMap;
    }

    public Set<String> getProjectResources() {
        return projectResourcePathToHashMap.keySet();
    }

    public Stream<Map.Entry<String, String>> getResourceToHashStream() {
        return projectResourcePathToHashMap.entrySet().stream().sorted(Map.Entry.comparingByKey());
    }

    public Collection<String> getChangedResources(ProjectResourceHashes old) {
        Map<String, String> oldProjectResourcePathToHashMap = old.getProjectResourcePathToHashMap();
        Map<String, String> changedProjectResourcePathToHashMap = new HashMap<>(projectResourcePathToHashMap);
        oldProjectResourcePathToHashMap.forEach((path, hash) -> {
            if (changedProjectResourcePathToHashMap.containsKey(path)) {
                if (changedProjectResourcePathToHashMap.get(path).equals(hash)) {
                    changedProjectResourcePathToHashMap.remove(path);
                }
            } else {
                changedProjectResourcePathToHashMap.put(path, hash);
            }
        });
        return changedProjectResourcePathToHashMap.keySet();
    }

}
