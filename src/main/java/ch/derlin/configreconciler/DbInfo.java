package ch.derlin.configreconciler;

import java.util.Optional;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@EqualsAndHashCode(exclude = "password")
public class DbInfo {
  String host;
  String dbName;
  String username;
  String password;

  public Optional<Exception> validate() {
    try {
      // the actual action would take some time to complete
      Thread.sleep(200L);
    } catch (InterruptedException e) {
    }
    return Optional.empty();
  }
}
