package ch.derlin.configreconciler.crds.db;

import ch.derlin.configreconciler.crds.KubeReference;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DbSpec {
  @NotNull
  KubeReference configRef;
}
