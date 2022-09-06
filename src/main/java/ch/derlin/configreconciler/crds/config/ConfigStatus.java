package ch.derlin.configreconciler.crds.config;

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
public class ConfigStatus {

  @PrinterColumn
  boolean ok;

  @PrinterColumn
  String dbName;

  String lastError;

  @Builder.Default
  @PrinterColumn
  String lastUpdate = Instant.now().toString(); // TODO: find a way to use "date" type
}
