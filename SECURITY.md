# Security Policy

## Supported Versions

Rainbow Gum is not yet 1.0. Security fixes are released as a patch of the latest minor version only,
so upgrade to the latest release to get them.

| Version              | Supported |
| -------------------- | --------- |
| Latest 0.x minor     | Yes       |
| Older 0.x minors     | No        |

## Reporting a Vulnerability

Please do not open a public issue, discussion or pull request for a vulnerability.

Report it privately through GitHub instead:
[Report a vulnerability](https://github.com/jstachio/rainbowgum/security/advisories/new).

Include what you can of:

* the affected module(s) and version,
* the configuration needed (properties, encoder, output, publisher),
* steps or a small test that reproduces it,
* the impact you expect.

Reports are handled on a best effort basis by a small team. You should get an acknowledgement within
a week. Once a fix is released the advisory is published, crediting you unless you ask otherwise.

## What Counts

Rainbow Gum treats its configuration (properties, `RainbowGumServiceProvider` and other code on the
class path) as trusted, and everything that is logged as untrusted. With that in mind, these are
vulnerabilities:

* logged content (messages, key values, logger and thread names, exceptions) that can forge or
  break the structure of an encoded event, run code, or cost far more CPU or memory than its size;
* credentials from the configuration, such as URI user information, query values or headers, that
  appear in errors, alerts, reports or other diagnostics;
* a network output's endpoint (for example an OTLP collector) that can crash, stall or inject into
  the application through its responses. The endpoint is trusted with the log records it receives,
  not with the application.

Someone who can already change the configuration can point logs anywhere, so that alone is not a
vulnerability.
