package ch.derlin.configreconciler;

import java.util.HashMap;
import java.util.Map;
import lombok.NonNull;

public class Base64Util {

  public static Map<String, String> encodeMap(Map<String, String> decoded) {
    var encoded = new HashMap<>(decoded);
    encoded.replaceAll((k, v) -> encodeBase64(v));
    return encoded;
  }

  public static Map<String, String> decodeMap(Map<String, String> decoded) {
    var encoded = new HashMap<>(decoded);
    encoded.replaceAll((k, v) -> decodeBase64(v));
    return encoded;
  }

  public static String encodeBase64(@NonNull String value) {
    return java.util.Base64.getEncoder().encodeToString(value.getBytes());
  }

  public static String decodeBase64(@NonNull String value) {
    return new String(java.util.Base64.getDecoder().decode(value));
  }
}
