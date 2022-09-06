package ch.derlin.configreconciler;

import ch.derlin.configreconciler.Base64Util;
import io.fabric8.kubernetes.api.model.Secret;
import java.util.Map;
import java.util.Optional;
import javax.validation.ValidationException;

import static java.lang.String.format;

public class CredentialsSecret {
  private static final String CREDENTIAL_SECRET_USERNAME_KEY = "username";
  private static final String CREDENTIAL_SECRET_PASSWORD_KEY = "password";

  public static String getUsername(Secret secret) {
    return getDataOrThrow(secret, CREDENTIAL_SECRET_USERNAME_KEY);
  }

  public static String getPassword(Secret secret) {
    return getDataOrThrow(secret, CREDENTIAL_SECRET_PASSWORD_KEY);
  }

  private static String getDataOrThrow(Secret secret, String property) {
    var encodedValue = Optional.ofNullable(secret.getData()).orElse(Map.of()).get(property);
    if (encodedValue == null || encodedValue.isBlank()) {
      throw new ValidationException(format("Secret %s in namespace %s is missing property %s",
          secret.getMetadata().getName(), secret.getMetadata().getNamespace(), property));
    }
    return Base64Util.decodeBase64(encodedValue);
  }
}
