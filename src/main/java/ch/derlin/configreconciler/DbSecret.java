package ch.derlin.configreconciler;

import ch.derlin.configreconciler.ConfigCacher;
import ch.derlin.configreconciler.DbInfo;
import ch.derlin.configreconciler.crds.db.Db;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.Matcher.Result;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import static ch.derlin.configreconciler.Base64Util.decodeBase64;
import static ch.derlin.configreconciler.Base64Util.encodeMap;


@Slf4j
@ApplicationScoped
public class DbSecret extends CRUDKubernetesDependentResource<Secret, Db> implements SecondaryToPrimaryMapper<Secret> {

  public static final String DB_NAME = "DB_NAME";
  public static final String DB_HOST = "DB_HOST";
  public static final String DB_USER = "DB_USER";
  public static final String DB_PASSWORD = "DB_PASSWORD";
  public static final String SECRET_SUFFIX = "-db";

  @Inject
  ConfigCacher configCacher;

  public DbSecret() {
    super(Secret.class);
  }

  @Override
  protected Secret desired(Db db, Context<Db> context) {
    log.info("Creating secret");
    return createSecret(db, getExpectedDbInfo(db));
  }

  @Override
  public Result<Secret> match(Secret actualResource, Db db, Context<Db> context) {
    log.debug("Matching secret...");
    var actualDbInfo = dbInfoFromSecretData(actualResource.getData());
    var desiredDbInfo = getExpectedDbInfo(db);
    var equals = !actualDbInfo.getPassword().isBlank() && actualDbInfo.equals(desiredDbInfo);

    if (equals) {
      log.debug("Secret match.");
      return Result.nonComputed(true);
    }

    // try to recover the password
    var actualPassword = actualDbInfo.getPassword();
    if (!actualPassword.isBlank()) {
      log.debug("Secrets don't match. Preserving old password.");
      desiredDbInfo.setPassword(actualPassword);
    }

    log.debug("Secrets don't match. Regenerating.");
    return Result.computed(false, createSecret(db, desiredDbInfo));
  }

  private DbInfo getExpectedDbInfo(Db db) {
    var spec = configCacher.getOrThrow(db.getSpec().getConfigRef()).getSpec();
    var dbPrefix = Optional.ofNullable(spec.getDbPrefix()).orElse(db.getMetadata().getNamespace());
    var dbName = (dbPrefix + "_" + db.getMetadata().getName()).replaceAll("\\W", "_");

    return DbInfo.builder()
        .host(spec.getHost())
        .dbName(dbName)
        .username(dbName)
        .password(UUID.randomUUID().toString().substring(0, 15))
        .build();
  }

  private Secret createSecret(Db db, DbInfo dbInfo) {
    return new SecretBuilder()
        .withMetadata(new ObjectMetaBuilder()
            .withName(db.getMetadata().getName() + SECRET_SUFFIX)
            .withNamespace(db.getMetadata().getNamespace())
            .build())
        .withData(secretDataFromDbInfo(dbInfo))
        .build();
  }

  @Override
  public Set<ResourceID> toPrimaryResourceIDs(Secret dependentResource) {
    var name = dependentResource.getMetadata().getName();
    if (name.contains(SECRET_SUFFIX)) {
      return Set.of(new ResourceID(name.substring(0, name.length() - SECRET_SUFFIX.length()),
          dependentResource.getMetadata().getNamespace()));
    }
    return Set.of();
  }

  public static DbInfo dbInfoFromSecretData(Map<String, String> data) {
    return DbInfo.builder()
        .dbName(decodeBase64(data.getOrDefault(DB_NAME, "")))
        .host(decodeBase64(data.getOrDefault(DB_HOST, "")))
        .username(decodeBase64(data.getOrDefault(DB_USER, "")))
        .password(decodeBase64(data.getOrDefault(DB_PASSWORD, "")))
        .build();
  }

  public static Map<String, String> secretDataFromDbInfo(DbInfo dbInfo) {
    return encodeMap(Map.of(
        DB_NAME, dbInfo.getDbName(),
        DB_HOST, dbInfo.getHost(),
        DB_USER, dbInfo.getDbName(),
        DB_PASSWORD, dbInfo.getPassword()
    ));
  }
}
