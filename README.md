# sailrsv

Screen scrapes a reservation website to determine whether e.g. a sailboat is reserved on a particular day or set of days; updates reserved dates to a SQL database. Set up to run nightly as cronjob and email changes. Designed for use with sailcal. 

```
git clone https://github.com/mpiech/sailrsv
cd sailrsv

oc project myproj
oc import-image mpiech/s2i-clojure-mail --confirm
# first time build
oc new-build mpiech/s2i-clojure-mail~. --name=sailrsv --env-file=env.cfg
# subsequent rebuilds
oc start-build sailrsv --from-dir=. --follow

# for testing/debugging
# uncomment while's in run.sh and core.lj
oc new-app sailrsv --env-file=env.cfg

# for cronjob
oc create cronjob sailrsv \
--image=image-registry.openshift-image-registry.svc:5000/myproj/sailrsv \
--schedule='05 08 * * *' --restart=Never
```

## License

Copyright © 2014-2022

Distributed under the Eclipse Public License either version 1.0 or later.
