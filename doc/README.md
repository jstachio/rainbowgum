# Documentation

`overview.html` in this directory is not just a readme, it is fed straight into
the `maven-javadoc-plugin` as the `overview` page (see the `doc` profile in the
root `pom.xml`). It becomes the front page of the aggregated javadoc, so it has
to be valid javadoc HTML, not just valid HTML.

## Section ids

Headings (`h2`, `h3`, `h4`) in `overview.html` need an `id` attribute, not the
sections wrapped around them. The ids drive the table of contents sidebar
(rendered with tocbot) and give every section a stable, linkable anchor people
can bookmark or copy straight out of the browser bar. Once an id ships treat it
as public API, do not casually rename it.

## Building the aggregated doc

```
bin/doc.sh
```

or the faster parallel variant:

```
bin/fast-doc.sh
```

Both are just `./mvnw -Pdoc clean install -DskipTests` under the hood. The
aggregated javadoc, `overview.html` included, lands in `target/site/apidocs`
at the repo root, open `target/site/apidocs/index.html` to check it.

Publishing a built copy of that aggregate to the public javadoc site is a
separate, release time step, see `release.md`'s "Updating Documentation"
section for that.

## `{@value}` for property names

When documenting a `public static final` field that holds a property name,
prefer `{@value}` over typing the string out by hand so the javadoc cannot
silently drift from the actual value.
