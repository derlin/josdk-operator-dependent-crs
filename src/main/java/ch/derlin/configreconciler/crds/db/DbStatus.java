package ch.derlin.configreconciler.crds.db;

import io.fabric8.kubernetes.model.annotation.PrinterColumn;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DbStatus {

  public enum State {
    UNKNOWN, SUCCESS, FAILURE
  }

  @Builder.Default
  @PrinterColumn
  State state = State.UNKNOWN;

  @PrinterColumn
  String dbName;

  String lastError;

  @Builder.Default
  @PrinterColumn
  String lastUpdate = Instant.now().toString(); // TODO: find a way to use "date" type
}
