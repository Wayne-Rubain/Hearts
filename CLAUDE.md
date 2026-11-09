# Hearts_2

Java Swing Hearts ("Omnibus Hearts"). Active work: AI player personalities. `main` is the working branch.

**Read all of `HANDOFF_cloud.md` before any other work** (state, build and measure commands, working rules), then `TODO.md`. Do not rely on memory or summaries instead of it.

`docs/` holds the dated reports and decision write-ups (measurements, recommendations, and what is
waiting on Wayne). Read `docs/project_review_2026-11.md` first: it is the overall picture and the
recommended next steps. The others: `lineup_2026-11.md` (round robin of the draft ten),
`decisions_2026-11.md` (lead guard, Jack Hunter vs Queen-phobic, Bonehead), `signature_options_2026-11.md`
(the new QUEEN_PHOBIA and JACK_CHASE options), `settings_audit_2026-11.md` (which roster settings do
nothing). Put new reports there too, dated in the file name.

Essentials:
- Build and test: see section 2 of `HANDOFF_cloud.md`. `AllTests` must report 0 failed.
- Measure tactics with whole games (`tools/measure`), never with proxies. Differences under 2 points are noise.
- Do not change Wayne's rules or default options without his approval; report measurements instead.
- Work on a topic branch and merge into `main`; never force-push `main`.
- Never build the Shark / moon memory until Wayne says its design is final.
