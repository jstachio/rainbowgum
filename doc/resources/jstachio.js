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
