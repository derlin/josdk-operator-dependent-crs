package ch.derlin.configreconciler.crds.config;

import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfigSpec {
  @NotNull
  String host;

  @NotNull
  String rootDbName = "postgres";

  String dbPrefix = null;
}
