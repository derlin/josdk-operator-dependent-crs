# JoSDK Operator Example with three layers dependent resources

This is a reproducible example of https://github.com/java-operator-sdk/java-operator-sdk/issues/1437.

Check the branches, as there may be multiple tests / proposed implementations:
* `config-cacher` branch: first try, using two reconcilers (one for config and one for db). The config is put in an application
  cache. Problem: the cache is not always up-to-date, as db can be reconciled before configs.
* `primary-indexer`: a test based on the primary-indexer examples on JOSDK, but this doesn't work.
* `main`: previously called `primary-to-secondary`, the implementation that is for now working the best.

Comments may be added as issues to this repo.

## Samples

You can find example of CRs / secrets in `samples/` directory.
There is also some helm charts that can be used to create many CRs.

Create a default config (**IMPORTANT** you need to change the namespace on the samples first !!!):
```bash
# create a credentials secret and a default config once
kubectl apply -f samples/credentials-secret.yaml
kubectl apply -f samples/config.v1.yaml
```

Deploy or scale db CRs:
```bash
# create one db
helm install dbs helm-test/dbs --set num=1
# scale it to 10
helm upgrade dbs helm-test/dbs --set num=10
# scale it down to 3
helm upgrade dbs helm-test/dbs --set num=3
# ...
# delete
helm uninstall dbs helm-test/dbs
```

The same can be done with the `helm-test/configs` helm Chart (which uses the `credentials-secret`) by default.
