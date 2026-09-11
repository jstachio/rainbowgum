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
 $('#related-package-summary').parent().detach().appendTo('.summary-list');

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
if ($('body').hasClass('module-index-page')) {
  $('#theme-button').insertBefore('.sub-nav .nav-list-search');
  $('#theme-panel').appendTo('body');
}

// Mobile TOC drawer: #toc-toggle/#toc-backdrop only exist (and #toc-toggle
// is only visible) on the overview page below the 800px breakpoint - see
// jstachio.css. Harmless no-op elsewhere since the click targets are absent
// or the CSS makes them non-interactive.
$('#toc-toggle').on('click', function () {
  var open = $('nav.js-toc').toggleClass('toc-open').hasClass('toc-open');
  $('#toc-backdrop').toggleClass('toc-open', open);
  $(this).attr('aria-expanded', open);
});
$('#toc-backdrop').on('click', function () {
  $('nav.js-toc').removeClass('toc-open');
  $(this).removeClass('toc-open');
  $('#toc-toggle').attr('aria-expanded', false);
});
// Tapping a section link should close the drawer so the content underneath
// is actually visible instead of staying covered until a second tap.
$('nav.js-toc').on('click', 'a.toc-link', function () {
  $('nav.js-toc').removeClass('toc-open');
  $('#toc-backdrop').removeClass('toc-open');
  $('#toc-toggle').attr('aria-expanded', false);
});
