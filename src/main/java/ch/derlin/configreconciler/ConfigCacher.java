package ch.derlin.configreconciler;

import ch.derlin.configreconciler.crds.KubeReference;
import ch.derlin.configreconciler.crds.config.Config;
import ch.derlin.configreconciler.crds.config.ConfigSpec;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.enterprise.context.ApplicationScoped;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class ConfigCacher {

  private final ConcurrentHashMap<String, ConfigEntry> cache = new ConcurrentHashMap<>();

  public ConfigEntry getValidConfigEntry(Config config, String username, String password) {
    var spec = config.getSpec();
    return ConfigEntry.builder()
        .valid(true)
        .spec(spec)
        .rootDbInfo(DbInfo.builder()
            .host(spec.getHost())
            .dbName(spec.getRootDbName())
            .username(username)
            .password(password)
            .build())
        .build();
  }

  public void cacheValid(Config config, ConfigEntry configEntry) {
    log.info("Caching config");
    cache.put(getCacheKey(config), configEntry);
  }

  public void cacheInvalid(Config config) {
    cache.put(getCacheKey(config), ConfigEntry.builder()
        .valid(false)
        .build());
  }

  public void evict(Config config) {
    log.info("Evicting config");
    cache.remove(getCacheKey(config));
  }


  public ConfigEntry getOrThrow(KubeReference reference) throws ConfigMissingException {
    var key = getCacheKey(reference.getNamespace(), reference.getName());
    var config = Optional.ofNullable(cache.get(key)).orElseThrow(() ->
        new ConfigMissingException("Could not find " + reference));
    if (!config.isValid()) {
      throw new ConfigInvalidException("Invalid config " + reference);
    }
    return config;
  }

  private String getCacheKey(Config config) {
    return getCacheKey(config.getMetadata().getNamespace(), config.getMetadata().getName());
  }

  private String getCacheKey(String namespace, String name) {
    return namespace + "#" + name;
  }

  @Data
  @Builder
  public static class ConfigEntry {
    private boolean valid;
    private ConfigSpec spec;

    private DbInfo rootDbInfo;
  }

  // -------------

  public static class ConfigException extends RuntimeException {
    public ConfigException(String message) {
      super(message);
    }
  }

  public static class ConfigMissingException extends ConfigException {
    public ConfigMissingException(String message) {
      super(message);
    }
  }

  public static class ConfigInvalidException extends ConfigException {
    public ConfigInvalidException(String message) {
      super(message);
    }
  }
}
