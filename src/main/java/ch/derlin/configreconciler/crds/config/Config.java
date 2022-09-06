package ch.derlin.configreconciler.crds.config;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

@Version("v1")
@Group("example.derlin.ch")
public class Config extends CustomResource<ConfigSpec, ConfigStatus> implements Namespaced {
}
