package ch.derlin.configreconciler;


import ch.derlin.configreconciler.crds.config.Config;
import ch.derlin.configreconciler.crds.db.Db;
import ch.derlin.configreconciler.crds.db.DbStatus;
import ch.derlin.configreconciler.crds.db.DbStatus.State;
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
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import static ch.derlin.configreconciler.DependentConfig.CONFIG_RELATION_INDEXER;

@Slf4j
@RequiredArgsConstructor
public class DbReconciler implements Reconciler<Db>,
    Cleaner<Db>,
    ErrorStatusHandler<Db>,
    EventSourceInitializer<Db> {

  final DbSecret dbSecret = new DbSecret();

  final DependentConfig dependentConfig = new DependentConfig();
  final KubernetesClient kubernetesClient;


  @Override
  public UpdateControl<Db> reconcile(Db resource, Context<Db> context) {
    log.info("Reconciling");

    //dependentConfig.reconcile(resource, context);
    var config = context.getSecondaryResource(Config.class).orElseThrow(() ->
        new ValidationException("Missing config"));

    dbSecret.reconcile(resource, context);
    var secret = context.getSecondaryResource(Secret.class).orElseThrow();
    var dbInfo = DbSecret.dbInfoFromSecretData(secret.getData());

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
    log.info("Provisioning using config={} and dbInfo={}", config, dbInfo);
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
    dbSecret.setKubernetesClient(kubernetesClient); // if not set, a NullPointerException is thrown during reconcile
    dependentConfig.setKubernetesClient(kubernetesClient);

    context.getPrimaryCache().addIndexer(CONFIG_RELATION_INDEXER, DependentConfig.indexer);

    var informerForConfigs =
        InformerConfiguration.from(Config.class)
            .withSecondaryToPrimaryMapper(
                (Config secondaryResource) -> context
                    .getPrimaryCache()
                    .byIndex(
                        CONFIG_RELATION_INDEXER,
                        secondaryResource.getMetadata().getNamespace() + "#" + secondaryResource.getMetadata().getName())
                    .stream()
                    .map(ResourceID::fromResource)
                    .collect(Collectors.toSet()))
            .build();

    return EventSourceInitializer.nameEventSources(
        new InformerEventSource<>(informerForConfigs, context), dbSecret.initEventSource(context));
  }
}
