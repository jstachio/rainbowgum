@AGENTS.md

## Claude specific

- Commit trailers: leave out the session link Claude Code adds by default. Add
  `Co-Authored-By` only for novel commits, as the "Agent specific" section of
  `development.md` describes.
- Dashes: do not use em dashes, en dashes or a spaced hyphen to set off a
  clause, in docs, javadoc, comments or commit messages. Use a new sentence,
  a colon, a semicolon or parentheses.
- Existing docs: keep the author's wording, tone and asides. Fix only what is
  wrong.
- New docs: do not add design notes or review write-ups under `doc/` that
  restate the code. Javadoc and commit messages carry that.
