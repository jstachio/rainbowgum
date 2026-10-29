## Explanation

This project does not use the maven release plugin for a variety of reasons:

  * Creates an environment that can be very different from the normal snapshot deploy process by forking
  * Needlessly edits the pom file twice and checkins it in which **requires every pom file in the project to be changed**.
  * Because it constantly changes the SNAPSHOT pom on each release downstream projects have to remember to update their SNAPSHOT version even for patch releases.

Unlike the maven release plugin we do not constantly change the pom for each patch version.
The release version is stored in a properties file called `version.properties`.
The pom only should change (commited change) if the minor or major version changes. 

For example say the pom is `0.7.0-SNAPSHOT` and our last release is `0.6.0`.

If we now want to make a patch version of `0.6.1` the pom file will not need to be updated (except by the release script for deployment).
However if we are actually wanting to finally release `0.7.0` then the pom must be updated to something like `0.8.0-SNAPSHOT`.

The `vh` script will mostly make sure you do not violate this.

## Directions

### Deploying SNAPSHOTs

If you just want to deploy a snapshot to centrals snapshot repositories run:

```
./mvnw clean -T1 deploy -Pcentral -Ddeploy=snapshot -Duser.timezone=UTC
```

`-Ddeploy=snapshot` also activates the `deploy-snapshot` profile, which forces
`maven.build.cache.enabled=false` regardless of any `-Pcache`/`RAINBOWGUM_CACHE`
opt-in (see `explore/maven-build-cache`) so a stale cached artifact never gets
deployed in place of a real build.

### Deploying Releases

A release is committed and tagged on its own branch, `release/vX.Y.Z`, which is pushed before the deploy.
If Central rejects the deploy, the fix goes on that branch and `main` is untouched.
Once the deploy succeeds, `main` is fast-forwarded to the branch.

1. On an up to date `main` with no changes, run `bin/vh release X.Y.Z`. It:
    1. checks the version (see below);
    1. writes `version.properties`, and for a minor or major release bumps the poms to the next snapshot;
    1. commits that as `Release vX.Y.Z` on `release/vX.Y.Z`, tags it `vX.Y.Z`, and pushes the branch and tag;
    1. sets the poms to the release version. **DO NOT CHECKIN THE ALTERED POMS**
1. Run the deploy it prints, which asks for the GPG passphrase:
   `./mvnw clean deploy -T1 -Duser.timezone=UTC -Ddeploy=release`
1. Once the deploy succeeds, run `bin/vh finish`. It restores the poms and pushes `release/vX.Y.Z` to `main`,
   which only works as a fast-forward. If `main` moved during the release, it stops: merge the release branch
   into `main` yourself or leave it unmerged, since the tag is already pushed.
1. Delete the release branch with the command `bin/vh finish` prints.

The version checks, which `--force` skips except for the snapshot rules:

* The release must be the next patch, minor, or major version after the newest tag.
* A minor or major release must be the version the pom is building, so `0.17.0` when the pom is
  `0.17.0-SNAPSHOT`. Before 1.0 a minor release is also allowed when the pom is `1.0.0-SNAPSHOT`.
* The pom after the release must be the next minor snapshot, or `1.0.0-SNAPSHOT` before 1.0.
  A minor or major release bumps to the next minor snapshot by default, and `--next 1.0.0` picks
  `1.0.0-SNAPSHOT` instead. A new major snapshot after 1.0, for example `--next 2.0.0`, needs `--force`.

#### If Central rejects the deploy

Nothing was published, so the version can be reused. On `release/vX.Y.Z`:

1. Restore the poms with `git checkout .`, then commit the fix.
1. Move the tag to the fix and push both:
   `git tag -f -a -m 'release X.Y.Z' vX.Y.Z && git push origin release/vX.Y.Z && git push -f origin refs/tags/vX.Y.Z`
1. Set the poms to the release version again with `bin/vh set pom` and run the deploy.
1. Run `bin/vh finish`.

### Updating Documentation

All the documentation should be in the aggregated javadoc. While sites like javadoc.io can host singular jars
they cannot really host aggregate javadoc (e.g. mutltimodule).

We host the aggregate javadoc which includes the critical overview.html in this repo: https://github.com/jstachio/jstachio.github.io

This repository will need to be updated after release.

1. Checkout jstachio.github.io
1. cd to `doc/rainbowgum` 
1. Run `build.sh <VERSION>`
1. A new directory with all the javadoc from that version will be created. 
1. Checkin the new content and push

### Reproducing a release

Because we do not alter the pom file reproducing a release build is less trivial but this not a normal use case anyway

```
git checkout SOME_TAG
bin/vh set pom  # no argument means use the version properties
./mvnw -T1 clean install -Duser.timezone=UTC
```

or use `bin/rebuild.sh`

