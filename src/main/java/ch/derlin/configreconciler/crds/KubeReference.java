package ch.derlin.configreconciler.crds;

import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class KubeReference {
  @NotNull
  String namespace;
  @NotNull
  String name;
}
