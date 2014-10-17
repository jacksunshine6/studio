package com.intellij.updater;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class DiffCalculator {
  public static Result calculate(Map<String, Long> oldChecksums, Map<String, Long> newChecksums) {
    Result result = new Result();
    result.filesToDelete = withAllRemoved(oldChecksums, newChecksums);
    result.filesToCreate = withAllRemoved(newChecksums, oldChecksums).keySet();
    result.filesToUpdate = collect(oldChecksums, newChecksums, false);
    result.commonFiles = collect(oldChecksums, newChecksums, true);
    return result;
  }

  private static Map<String, Long> withAllRemoved(Map<String, Long> from, Map<String, Long> toRemove) {
    Map<String, Long> result = new LinkedHashMap<String, Long>(from);
    for (String each : toRemove.keySet()) {
      result.remove(each);
    }
    return result;
  }

  private static Map<String, Long> collect(Map<String, Long> older, Map<String, Long> newer, boolean equal) {
    Map<String, Long> result = new LinkedHashMap<String, Long>();
    for (Map.Entry<String, Long> each : newer.entrySet()) {
      String file = each.getKey();
      Long oldChecksum = older.get(file);
      Long newChecksum = newer.get(file);
      if (oldChecksum != null && newChecksum != null && oldChecksum.equals(newChecksum) == equal) {
        result.put(file, oldChecksum);
      }
    }
    return result;
  }

  public static class Result {
    public Map<String, Long> filesToDelete;
    public Set<String> filesToCreate;
    public Map<String, Long> filesToUpdate;
    public Map<String, Long> commonFiles;
  }
}
