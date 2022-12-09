package ch.derlin.configreconciler;


import ch.derlin.configreconciler.crds.config.Config;
import ch.derlin.configreconciler.crds.db.Db;
import ch.derlin.configreconciler.crds.db.DbStatus;
import ch.derlin.configreconciler.crds.db.DbStatus.State;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.ErrorStatusHandler;
import io.javaoperatorsdk.operator.api.reconciler.ErrorStatusUpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfig;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.ValidationException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DbReconciler implements Reconciler<Db>,
    Cleaner<Db>,
    ErrorStatusHandler<Db>,
    EventSourceInitializer<Db> {

  private static final String CONFIG_INFORMER = "dependent_config";
  private static final String CREDENTIALS_SECRET_INFORMER = "dependent_credentials_secret";
  private static final String DB_SECRET_INFORMER = "dependent_db_secret";

  private static final String CREDENTIALS_SECRET_LABEL = "kubernetes.io/used-by=external-db-operator";
  final DbSecret dbSecret = new DbSecret();
  final KubernetesClient kubernetesClient;

  public DbReconciler(KubernetesClient kubernetesClient) {
    this.kubernetesClient = kubernetesClient;
    dbSecret.setKubernetesClient(kubernetesClient); // if not set, a NullPointerException is thrown during reconcile
    // The label is to avoid watching *ALL* secrets, which would be a lot of processing for nothing...
    dbSecret.configureWith(new KubernetesDependentResourceConfig<Secret>().setLabelSelector(DbSecret.DB_SECRET_LABEL));
  }

  @Override
  public UpdateControl<Db> reconcile(Db resource, Context<Db> context) {
    log.info("Reconciling");

    // get the config and the credentials secret
    // NOTE: the credentials secret MUST be in the same namespace AND have the same name as the config + have the label
    // {@see CREDENTIALS_SECRET_LABEL}.
    // The namespace and name is to be able to guess its name from the db directly (vs from the config), and the label is
    // to avoid watching all secrets, which is resource intensive.
    var config = context.getSecondaryResource(Config.class).orElseThrow(() ->
        new ValidationException("Missing config"));
    var credentialsSecret = context.getSecondaryResource(Secret.class, CREDENTIALS_SECRET_INFORMER).orElseThrow(() ->
        new ValidationException("Missing credentials secret"));
    var rootDbInfo = DbInfo.builder()
        .dbName(config.getSpec().getRootDbName())
        .host(config.getSpec().getHost())
        .username(CredentialsSecret.getUsername(credentialsSecret))
        .password(CredentialsSecret.getPassword(credentialsSecret))
        .build();

    log.debug("root db info: {}", rootDbInfo);

    dbSecret.reconcile(resource, context);
    var secret = context.getSecondaryResource(Secret.class, DB_SECRET_INFORMER).orElseThrow();
    var dbInfo = DbSecret.dbInfoFromSecretData(secret.getData());
    log.debug("secret db info: {}", dbInfo);

    // do the provisioning
    provisionDatabase(config, dbInfo);

    var currentStatus = Optional.ofNullable(resource.getStatus())
        .map(DbStatus::getState)
        .orElse(State.UNKNOWN);

    if (currentStatus != State.SUCCESS) {
      log.info("Success ! Patching status.");
      return UpdateControl.patchStatus(withStatus(resource,
          DbStatus.builder()
              .state(State.SUCCESS)
              .dbName(dbInfo.getDbName())
              .build()));
    }
    log.info("Nothing to do.");
    return UpdateControl.noUpdate();
  }

  @SneakyThrows
  @Override
  public DeleteControl cleanup(Db resource, Context<Db> context) {
    try {
      var config = context.getSecondaryResource(Config.class).orElseThrow();
      // if an exception occurs, retry
      deleteDatabase(config, resource);
      return DeleteControl.defaultDelete();

    } catch (Exception e) {
      log.warn("Cannot delete: {}. Rescheduling.", e.getMessage());
      return DeleteControl.noFinalizerRemoval().rescheduleAfter(5000L);
    }
  }

  @Override
  public ErrorStatusUpdateControl<Db> updateErrorStatus(Db resource, Context<Db> context,
      Exception e) {
    return ErrorStatusUpdateControl.patchStatus(
        withStatus(resource, DbStatus.builder()
            .state(State.FAILURE)
            .lastError(e.getMessage())
            .build()));
  }

  private Db withStatus(Db resource, DbStatus status) {
    resource.setStatus(status);
    return resource;
  }

  private void provisionDatabase(Config config, DbInfo dbInfo) {
    dbInfo.validate();
    try {
      // the actual action would take some time to complete
      Thread.sleep(500L);
    } catch (InterruptedException e) {
    }
  }

  private void deleteDatabase(Config config, Db db) {
    // dummy
    log.info("Deleting database using config={}", config);
    try {
      // the actual action would take some time to complete
      Thread.sleep(200L);
    } catch (InterruptedException e) {
    }
  }

  @Override
  public Map<String, EventSource> prepareEventSources(EventSourceContext<Db> context) {
    Map<String, EventSource> eventSources = Map.of(
        CONFIG_INFORMER, createInformer(context, Config.class, CONFIG_INFORMER),
        CREDENTIALS_SECRET_INFORMER, createInformer(context, Secret.class, CREDENTIALS_SECRET_INFORMER, CREDENTIALS_SECRET_LABEL),
        DB_SECRET_INFORMER, dbSecret.initEventSource(context)
    );
    dbSecret.useEventSourceWithName(DB_SECRET_INFORMER);
    return eventSources;
  }

  private <T extends HasMetadata> InformerEventSource<T, Db> createInformer(EventSourceContext<Db> context,
      Class<T> cls, String indexName, String... labels) {

    context.getPrimaryCache().addIndexer(indexName, db -> List.of(indexKey(db)));

    var informerConfiguration = InformerConfiguration.from(cls)
        .withPrimaryToSecondaryMapper((Db db) -> Set.of(new ResourceID(
            db.getSpec().getConfigRef().getName(),
            db.getSpec().getConfigRef().getNamespace())))
        .withSecondaryToPrimaryMapper(
            secondary -> context
                .getPrimaryCache()
                .byIndex(indexName, indexKey(secondary))
                .stream()
                .map(ResourceID::fromResource)
                .collect(Collectors.toSet()));

    for (String label : labels) {
      informerConfiguration.withLabelSelector(label);
    }

    return new InformerEventSource<>(informerConfiguration.build(), context);
  }

  private <T extends HasMetadata> String indexKey(T config) {
    return config.getMetadata().getNamespace() + "#" + config.getMetadata().getName();
  }

  private String indexKey(Db db) {
    return db.getSpec().getConfigRef().getNamespace() + "#" + db.getSpec().getConfigRef().getName();
  }
}
