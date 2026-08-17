# JDK javadoc doclet bugs encountered

Bugs hit while writing this project's javadoc, kept here so they can be filed against
the JDK bug tracker (https://bugreport.java.com / https://bugs.java.com). Each entry has
a minimal repro so it is easy to turn into a standalone bug report.

## `{@snippet class="..."}` in a `module-info.java` doc comment crashes module page generation

- **JDK versions confirmed on:** `javadoc 21.0.12` (Temurin `21.0.12-tem`) **and**
  `javadoc 26.0.2` (Temurin `26.0.2-tem`) - identical NPE on both, so this is not fixed as
  of 26.
- **Command:** `mvn org.apache.maven.plugins:maven-javadoc-plugin:3.12.0:jar` (also
  reproduces under the `:aggregate` goal) and with plain `javadoc` invoked directly
  (see minimal repro below) - nothing Maven-specific.
- **Symptom:** `An internal exception has occurred. (java.lang.NullPointerException:
  Cannot invoke "Object.toString()" because "o" is null)`, thrown from inside the
  `javadoc` tool itself while generating the module description page.
- **Trigger:** A `{@snippet}` tag that references an **external file** via the
  `class`/`file` attribute (e.g. `{@snippet class="some.Example" region="x" }`), placed
  directly in the javadoc comment on the `module` declaration in `module-info.java`.
  Narrowed down via testing:
  - A **fully inline** `{@snippet : int x = 1; }` (no external file) in the same spot
    does **not** trigger it.
  - Plain `{@link}` / `{@value}` tags in the same module comment are fine.
  - So the bug specifically requires the doclet to resolve an *external* snippet
    source's location while the enclosing element being documented is the `module`
    declaration itself.
- **Full stack trace:**
  ```
  java.lang.NullPointerException: Cannot invoke "Object.toString()" because "o" is null
      at jdk.compiler/com.sun.tools.javac.model.JavacElements.cast(JavacElements.java:857)
      at jdk.compiler/com.sun.tools.javac.model.JavacElements.getModuleOf(JavacElements.java:455)
      at jdk.javadoc/jdk.javadoc.internal.doclets.toolkit.util.Utils.getLocationForPackage(Utils.java:196)
      at jdk.javadoc/jdk.javadoc.internal.doclets.toolkit.taglets.SnippetTaglet.generateContent(SnippetTaglet.java:221)
      at jdk.javadoc/jdk.javadoc.internal.doclets.toolkit.taglets.SnippetTaglet.getInlineTagOutput(SnippetTaglet.java:114)
      at jdk.javadoc/jdk.javadoc.internal.doclets.toolkit.taglets.TagletWriter.getInlineTagOutput(TagletWriter.java:358)
      at jdk.javadoc/jdk.javadoc.internal.doclets.formats.html.HtmlDocletWriter.getInlineTagOutput(HtmlDocletWriter.java:380)
      at jdk.javadoc/jdk.javadoc.internal.doclets.formats.html.HtmlDocletWriter$2.defaultAction(HtmlDocletWriter.java:1493)
      at jdk.javadoc/jdk.javadoc.internal.doclets.formats.html.HtmlDocletWriter$2.defaultAction(HtmlDocletWriter.java:1266)
      at jdk.compiler/com.sun.source.util.SimpleDocTreeVisitor.visitSnippet(SimpleDocTreeVisitor.java:479)
      at jdk.compiler/com.sun.tools.javac.tree.DCTree$DCSnippet.accept(DCTree.java:1103)
      at jdk.compiler/com.sun.source.util.SimpleDocTreeVisitor.visit(SimpleDocTreeVisitor.java:79)
      at jdk.javadoc/jdk.javadoc.internal.doclets.formats.html.HtmlDocletWriter.commentTagsToContent(HtmlDocletWriter.java:1502)
      at jdk.javadoc/jdk.javadoc.internal.doclets.formats.html.HtmlDocletWriter.commentTagsToContent(HtmlDocletWriter.java:1210)
      at jdk.javadoc/jdk.javadoc.internal.doclets.formats.html.HtmlDocletWriter.addCommentTags(HtmlDocletWriter.java:1122)
      at jdk.javadoc/jdk.javadoc.internal.doclets.formats.html.HtmlDocletWriter.addInlineComment(HtmlDocletWriter.java:1103)
      at jdk.javadoc/jdk.javadoc.internal.doclets.formats.html.ModuleWriterImpl.addModuleDescription(ModuleWriterImpl.java:797)
      at jdk.javadoc/jdk.javadoc.internal.doclets.toolkit.builders.ModuleSummaryBuilder.buildModuleDescription(ModuleSummaryBuilder.java:178)
      at jdk.javadoc/jdk.javadoc.internal.doclets.toolkit.builders.ModuleSummaryBuilder.buildContent(ModuleSummaryBuilder.java:120)
      at jdk.javadoc/jdk.javadoc.internal.doclets.toolkit.builders.ModuleSummaryBuilder.buildModuleDoc(ModuleSummaryBuilder.java:103)
      at jdk.javadoc/jdk.javadoc.internal.doclets.toolkit.builders.ModuleSummaryBuilder.build(ModuleSummaryBuilder.java:92)
      at jdk.javadoc/jdk.javadoc.internal.doclets.formats.html.HtmlDoclet.generateModuleFiles(HtmlDoclet.java:398)
      at jdk.javadoc/jdk.javadoc.internal.doclets.toolkit.AbstractDoclet.startGeneration(AbstractDoclet.java:211)
      at jdk.javadoc/jdk.javadoc.internal.doclets.toolkit.AbstractDoclet.run(AbstractDoclet.java:110)
      at jdk.javadoc/jdk.javadoc.doclet.StandardDoclet.run(StandardDoclet.java:104)
      at jdk.javadoc/jdk.javadoc.internal.tool.Start.parseAndExecute(Start.java:575)
      at jdk.javadoc/jdk.javadoc.internal.tool.Start.begin(Start.java:398)
      at jdk.javadoc/jdk.javadoc.internal.tool.Start.begin(Start.java:347)
      at jdk.javadoc/jdk.javadoc.internal.tool.Main.execute(Main.java:57)
      at jdk.javadoc/jdk.javadoc.internal.tool.Main.main(Main.java:46)
  ```
- **Minimal repro** (directory layout: `src/example/module-info.java`,
  `src/example/com/example/Foo.java`):
  ```java
  // src/example/module-info.java
  /**
   * Some module.
   * {@snippet class="com.example.Foo" region="bar" }
   */
  module example {
      exports com.example;
  }
  ```
  ```java
  // src/example/com/example/Foo.java
  package com.example;

  public class Foo {

      // @start region="bar"
      public void bar() {
          int x = 1;
      }
      // @end

  }
  ```
  Run `javadoc --module example --module-source-path src -d out` and it crashes with the
  NPE above while building the module's summary/description page. (An inline-only
  `{@snippet : int x = 1; }` in the same spot, with no external file, does **not**
  crash - confirmed this is specific to the external-file form.)
- **Root cause (guess from the trace):** `SnippetTaglet.generateContent` calls
  `Utils.getLocationForPackage`, which calls `JavacElements.getModuleOf` on the
  *enclosing element of the tag* to figure out where to resolve the snippet from. When
  the enclosing element is the `module` declaration itself (not a package or type), that
  lookup appears to return something `JavacElements.cast` cannot handle, and it NPEs
  instead of resolving relative to the module's own location.
- **Workaround used in this repo:** moved the snippet-bearing prose out of
  `rainbowgum-pattern/src/main/java/module-info.java` into
  `rainbowgum-pattern/src/main/java/io/jstach/rainbowgum/pattern/format/package-info.java`
  instead (a package doc comment, not a module doc comment, does not hit this path).
  `module-info.java` now just links to the package for the full guide. See commit adding
  the Pattern Module documentation.
