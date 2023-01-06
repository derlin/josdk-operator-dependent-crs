package ch.derlin.configreconciler;

import ch.derlin.configreconciler.crds.db.Db;
import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import java.util.Optional;

public class DbSecretDiscriminator implements ResourceDiscriminator<Secret, Db> {
  @Override
  public Optional<Secret> distinguish(Class<Secret> aClass, Db db, Context<Db> context) {
    InformerEventSource<Secret, Db> ies =
        (InformerEventSource<Secret, Db>) context
            .eventSourceRetriever().getResourceEventSourceFor(Secret.class, DbReconciler.DB_SECRET_INFORMER);

    return ies.get(new ResourceID(db.getMetadata().getName() + DbSecret.SECRET_SUFFIX,
        db.getMetadata().getNamespace()));
  }
}
