package ch.derlin.configreconciler;

import ch.derlin.configreconciler.crds.config.Config;
import ch.derlin.configreconciler.crds.config.ConfigStatus;
import io.fabric8.kubernetes.api.model.Secret;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static java.lang.String.format;

@Slf4j
@RequiredArgsConstructor
public class ConfigReconciler implements Reconciler<Config>,
    Cleaner<Config>,
    ErrorStatusHandler<Config>,
    EventSourceInitializer<Config> {

  private static final String CONFIG_CREDENTIALS_INDEX = "config-secret-index";
  private final ConfigCacher configCacher;

  @Override
  public UpdateControl<Config> reconcile(Config resource, Context<Config> context) {
    log.debug("Reconciling");

    // validate secret reference and db connection
    var secret = context.getSecondaryResource(Secret.class).orElseThrow(() ->
        new ValidationException("Credentials secret not found:" + resource.getSpec().getCredentialsSecretRef()));
    var configEntry = configCacher.getValidConfigEntry(resource,
        CredentialsSecret.getUsername(secret), CredentialsSecret.getPassword(secret));

    configEntry.getRootDbInfo().validate().ifPresent((exception) -> {
      throw new RuntimeException(exception);
    });

    log.debug("success !");
    configCacher.cacheValid(resource, configEntry);

    // no exception thrown: update the status
    if (resource.getStatus() == null || !resource.getStatus().isOk()) {
      return UpdateControl.patchStatus(withStatus(resource, ConfigStatus.builder().ok(true).build()));
    }
    return UpdateControl.noUpdate();
  }

  @Override
  public DeleteControl cleanup(Config resource, Context<Config> context) {
    configCacher.evict(resource);
    return DeleteControl.defaultDelete();
  }


  @Override
  public ErrorStatusUpdateControl<Config> updateErrorStatus(Config resource,
      Context<Config> context, Exception e) {
    configCacher.cacheInvalid(resource);
    var updateControl = ErrorStatusUpdateControl.patchStatus(
        withStatus(resource, ConfigStatus.builder()
            .ok(false)
            .lastError(format("[%s] %s", e.getClass().getSimpleName(), e.getMessage()))
            .build()));

    if (e.getCause() instanceof ValidationException) {
      // do not trigger retry if the error is in the spec, as it is not recoverable without user action
      log.warn("The configuration is invalid, cannot proceed without user input: {}", e.getCause().getMessage());
      return updateControl.withNoRetry();
    }
    log.error("Reconciliation failed {}", e.getMessage(), e);
    return updateControl;
  }

  private Config withStatus(Config resource, ConfigStatus status) {
    resource.setStatus(status);
    return resource;
  }

  @Override
  public Map<String, EventSource> prepareEventSources(EventSourceContext<Config> context) {

    context.getPrimaryCache().addIndexer(CONFIG_CREDENTIALS_INDEX, this::indexKey);

    InformerConfiguration<Secret> informerConfiguration =
        InformerConfiguration.from(Secret.class, context)
            .withSecondaryToPrimaryMapper(secret -> context.getPrimaryCache()
                .byIndex(CONFIG_CREDENTIALS_INDEX, indexKey(secret))
                .stream().map(ResourceID::fromResource).collect(Collectors.toSet()))
            .withPrimaryToSecondaryMapper(this::getSecretResourceId)
            .withNamespacesInheritedFromController(context)
            .build();

    return EventSourceInitializer
        .nameEventSources(new InformerEventSource<>(informerConfiguration, context));
  }

  private List<String> indexKey(Config config) {
    var secretRef = config.getSpec().getCredentialsSecretRef();
    return List.of(secretRef.getNamespace() + "#" + secretRef.getName());
  }

  private String indexKey(Secret secret) {
    return secret.getMetadata().getNamespace() + "#" + secret.getMetadata().getName();
  }

  private Set<ResourceID> getSecretResourceId(Config config) {
    var secretRef = config.getSpec().getCredentialsSecretRef();
    return Set.of(new ResourceID(secretRef.getName(), secretRef.getNamespace()));
  }
}
