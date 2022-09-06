package ch.derlin.configreconciler;

import ch.derlin.configreconciler.crds.config.Config;
import ch.derlin.configreconciler.crds.db.Db;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.IndexerResourceCache;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DependentConfig
    extends KubernetesDependentResource<Config, Db>
    implements SecondaryToPrimaryMapper<Config> {

  public static final String CONFIG_RELATION_INDEXER = "db_config_indexer";
  private IndexerResourceCache<Db> cache;

  static final Function<Db, List<String>> indexer =
      resource -> List.of(resource.getSpec().getConfigRef().getNamespace() + "#" + resource.getSpec().getConfigRef().getName());

  public DependentConfig() {
    super(Config.class);
  }

  @Override
  public Set<ResourceID> toPrimaryResourceIDs(Config dependentResource) {
    var indexKey = dependentResource.getMetadata().getNamespace() + "#" + dependentResource.getMetadata().getName();
    return cache.byIndex(CONFIG_RELATION_INDEXER, indexKey)
        .stream()
        .map(ResourceID::fromResource)
        .collect(Collectors.toSet());
  }

  @Override
  public EventSource initEventSource(EventSourceContext<Db> context) {
    cache = context.getPrimaryCache();
    cache.addIndexer(CONFIG_RELATION_INDEXER, indexer);
    return super.initEventSource(context);
  }
}
