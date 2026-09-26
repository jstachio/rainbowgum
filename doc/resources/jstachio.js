tocbot.init({
  // Where to render the table of contents.
  tocSelector: '.js-toc',
  // Where to grab the headings to build the table of contents.
  contentSelector: '.js-toc-content',
  // Which headings to grab inside of the contentSelector element.
  headingSelector: 'h1, h2, h3, h4, h5',
  // For headings inside relative or absolute positioned containers within content.
  hasInnerContainers: true,
  collapseDepth: 6,
  headingsOffset: 30,
  scrollSmoothOffset: -30
});

// No jQuery: JDK 27's javadoc doclet stopped bundling jQuery/jQuery UI in generated
// output (present through JDK 26), so every "$(...)" call below would throw
// "ReferenceError: $ is not defined" the instant this script ran, aborting before ever
// reaching the #toc-toggle click handler further down, which is why the mobile
// hamburger menu did nothing. Plain DOM APIs only from here on.
var relatedPackageSummary = document.getElementById('related-package-summary');
if (relatedPackageSummary && relatedPackageSummary.parentElement) {
  var summaryList = document.querySelector('.summary-list');
  if (summaryList) {
    // appendChild on a node already in the document moves it, detaching it from its
    // old parent first: no separate "detach" step needed.
    summaryList.appendChild(relatedPackageSummary.parentElement);
  }
}

anchors.add();

// #navbar-top (and everything in it, including the theme button javadoc's own
// script.js already wires up a working click handler for) is hidden on the overview
// page - see "module-index-page #navbar-top" in jstachio.css, which drops the
// redundant Overview/Tree/Index/Help row since this page has its own custom header.
// The theme button is still wanted though, so move it out to the one part of the
// navbar that stays visible here: the sub-nav's search box row, just to the left of
// the search box. #theme-panel (the light/dark/system dropdown) is position:fixed in
// javadoc's own stylesheet, so its new DOM parent doesn't affect where it renders -
// moved to <body> directly, clear of any risk of an ancestor with a CSS transform
// (which would otherwise change what position:fixed is relative to). Harmless no-op
// on every other page: #navbar-top is never hidden there, so this block never runs.
if (document.body.classList.contains('module-index-page')) {
  var themeButton = document.getElementById('theme-button');
  var navListSearch = document.querySelector('.sub-nav .nav-list-search');
  if (themeButton && navListSearch && navListSearch.parentElement) {
    navListSearch.parentElement.insertBefore(themeButton, navListSearch);
  }
  var themePanel = document.getElementById('theme-panel');
  if (themePanel) {
    document.body.appendChild(themePanel);
  }
}

// Mobile TOC drawer: #toc-toggle/#toc-backdrop only exist (and #toc-toggle
// is only visible) on the overview page below the 800px breakpoint - see
// jstachio.css. Harmless no-op elsewhere since the click targets are absent
// or the CSS makes them non-interactive.
var tocToggle = document.getElementById('toc-toggle');
var tocBackdrop = document.getElementById('toc-backdrop');
var jsToc = document.querySelector('nav.js-toc');
if (tocToggle) {
  tocToggle.addEventListener('click', function () {
    var open = jsToc ? jsToc.classList.toggle('toc-open') : false;
    if (tocBackdrop) {
      tocBackdrop.classList.toggle('toc-open', open);
    }
    tocToggle.setAttribute('aria-expanded', open);
  });
}
if (tocBackdrop) {
  tocBackdrop.addEventListener('click', function () {
    if (jsToc) {
      jsToc.classList.remove('toc-open');
    }
    tocBackdrop.classList.remove('toc-open');
    if (tocToggle) {
      tocToggle.setAttribute('aria-expanded', false);
    }
  });
}
// Tapping a section link should close the drawer so the content underneath
// is actually visible instead of staying covered until a second tap.
if (jsToc) {
  jsToc.addEventListener('click', function (event) {
    if (!event.target.closest('a.toc-link')) {
      return;
    }
    jsToc.classList.remove('toc-open');
    if (tocBackdrop) {
      tocBackdrop.classList.remove('toc-open');
    }
    if (tocToggle) {
      tocToggle.setAttribute('aria-expanded', false);
    }
  });
}
