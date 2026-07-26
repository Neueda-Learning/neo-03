# DESIGN.md — how to build screens with this design system

This file travels inside `src/design-system/`. If you have the folder, you have the
spec. It is the answer to "how should this screen look?" — read it before you write
a component, and before you write a single line of CSS.

**The one-line version:** compose the exported components, map your outcome words
onto the five tones, and never write a colour.

---

## 1. Why this exists

Ten teams are building ten modules of one bank. A bank employee moves between them
all day. If each module invents its own table, its own idea of what "failed" looks
like and its own spacing, the product reads as ten student projects — and the
operator has to relearn the screen every time they change tab.

The design system is also the **complexity equaliser**. The plumbing is done. Your
net new work is your business rules and your screens, not a button.

So the deal is:

- The design system owns **how it looks**.
- You own **what it says**.

---

## 2. Install

Already vendored into every repo at `frontend/src/design-system/`. Two lines in
`src/main.jsx`:

```jsx
import './design-system/styles.css';   // 1. the system
import './styles.css';                 // 2. your app's few screen-specific rules
```

Order matters — yours last, so you can override, and so it is obvious in a diff
when you have.

Then import components from the barrel:

```jsx
import { AppShell, TopNav, PageHeader, DataTable, Badge } from './design-system';
```

Never from a component file directly. The paths inside are free to move; the
barrel is not.

**Do not edit anything inside `design-system/`.** It is a vendored copy, verified
byte-identical by `scripts/sync-design-system.sh --check` in CI. An edit will be
reported as drift and lost on the next sync. If you need something the system does
not have, see §8.

---

## 3. Tones — the only thing you must map

The design system does not know a single business word, and must not learn one.
Ten modules speak ten vocabularies over one contract:

| module | outcomes |
| --- | --- |
| verification | `PASSED` · `FAILED` · `REVIEW` |
| policy | `APPROVED` · `REFERRED` · `REJECTED` |
| kyc | `VERIFIED` · `FAILED` · `REVIEW` |
| screening | `CLEAR` · `REVIEW` · `HIT` |
| credit | `APPROVED` · `REFERRED` · `DECLINED` |
| agreement | `SIGNED` · `PENDING` · `DECLINED` · `EXPIRED` |
| account | `OPENED` · `FAILED` |
| card | `ISSUED` · `FAILED` |

If `Badge` knew those words, every team would have to edit the design system to add
their own. Instead every component that carries meaning takes a **tone**, and each
app owns exactly one map:

| tone | means | examples |
| --- | --- | --- |
| `positive` | it worked, it passed, it is clear | PASSED · APPROVED · CLEAR · SIGNED · ISSUED |
| `warning` | a person must look | REVIEW · REFERRED · PENDING · SLA breaching |
| `negative` | a business no, or a failure | FAILED · REJECTED · DECLINED · HIT · EXPIRED |
| `info` | in flight, running now | in-progress · dispatched · awaiting callback |
| `neutral` | nothing has happened yet, or no judgement applies | not started · a count |

Build the map once, in your app, and use it everywhere:

```js
// src/status.js
import { toneMapper, TONES } from './design-system';

export const outcomeTone = toneMapper({
  PASSED: TONES.POSITIVE,
  FAILED: TONES.NEGATIVE,
  REVIEW: TONES.WARNING,
});
```

```jsx
<Badge tone={outcomeTone(record.outcome)}>{record.outcome}</Badge>
```

Matching is case-insensitive and normalises `-` and spaces to `_`, so `in-progress`
and `IN_PROGRESS` are the same key. Anything unmapped falls back to `neutral`
rather than rendering unstyled.

**Colour is never the only signal.** The word is always on screen beside it, and
`StatusDot` — which has no word — requires a `label`.

---

## 4. Theming

Everything visual is a CSS custom property. Components reference those properties
and nothing else: no hex, no font name, no backdrop, no pixel literal in a JSX
`style`.

```
tokens/tokens.css   scales — spacing, type, radii, motion. No colours.
theme/fonts.css     @font-face for the vendored faces. Nothing is fetched.
theme/glass.css     every colour, face and backdrop in the product.
assets/             the two faces and the ground image.
```

The product ships **one theme, `glass`**. To reskin, write a file of variable
values and load it **after** the system:

```jsx
import './design-system/styles.css';
import './theme/ourbank.css';
```

Scope it to `[data-ds-theme='name']` instead of `:root` to switch at runtime.

### What a theme must define

- **All five tone groups.** Each tone carries four roles, and the last two are
  not interchangeable:

  | role | used for | judged by |
  | --- | --- | --- |
  | `surface` / `border` / `text` | the chip itself | text on that fill |
  | `accent` | a **mark** — bar, dot, card edge | contrast with what is beside it |
  | `ink` | the tone used **as text**, e.g. a headline figure | 4.5:1 on the surface |

  `accent` and `ink` exist separately because volt at 1.93:1 and mint-deep at
  2.91:1 are exactly right as marks and unreadable as figures. One variable could
  not serve both.

- **The surface hooks, if panels are anything other than flat.**
  `--ds-surface-fill` · `--ds-surface-backdrop` · `--ds-surface-border` ·
  `--ds-surface-shadow`. These are what let glass be a theme rather than a
  rewrite: every panel reads them, and each falls back to a plain card when unset.

- **`--ds-control-fill`, if the canvas is busy.** Inputs, textareas, code blocks
  and dialogs read this instead of the surface hooks. A payload you have to read
  character by character must never sit on blurred wallpaper.

- **`--ds-ground-image` / `--ds-ground-grain`, or `none`.** The shell paints them;
  a theme that sets neither gets a flat canvas at no cost.

### The check that keeps this true

`scripts/sync-design-system.sh --check` fails the build if any colour appears
outside `theme/`. With a single theme that gate is the *only* thing standing
between the system and a component quietly hardcoding a value — there is no second
theme to reveal it. It runs in CI beside the drift check.

---

## 4a. Inferences from the glass handoff

`handoff/glass/glass.css` arrived without the `design.md` its own header points
at. The tokens are carried over verbatim; the **rules** below were inferred. When
the real design.md turns up, this list is the diff to work through — every item is
a decision someone else may have already made differently.

1. **Tone mapping.** The handoff ships four chips: `approved` · `review` ·
   `declined` · `volt`. Mapped as positive=mint, **warning=review blue**,
   negative=declined red, **info=volt**. Reasoning: the handoff names its blue
   chip `review`, and REVIEW/REFERRED is exactly what the `warning` tone renders;
   that leaves volt for `info`, which is an in-flight step — the "happening now"
   colour. *If design.md intends volt as the attention/warning colour, swap these
   two and nothing else changes.*
2. **A fifth tone was needed.** The handoff has no `neutral`. Translucent white
   with muted ink is the only reading consistent with the rest of the kit.
3. **The ground is `aurora`, and only one ships.** Four were supplied; aurora is
   the only one in the mint family, and `--havn-ground-fallback: #AFF0D6` sits
   inside it. The other three (kasumi pink/gold, papercut red/gold, seigaiha
   brown/gold) fight the palette. All four stay in `handoff/` — swapping is one
   variable.
4. **The ground was recompressed** from 2.56 MB PNG to 96 KB JPEG at full
   1960×1320. It is a smooth gradient under a 30px blur; the original is kept in
   `handoff/`.
5. **Ground is `fixed`, not `absolute`.** The handoff's `.havn-app` is
   `overflow: hidden` — a single non-scrolling screen. These apps scroll, and a
   1960px image repeating down a long board is not what the design intends.
6. **Controls are not glass.** The handoff has no input surface. Inputs,
   textareas, code blocks and the dialog get `--ds-control-fill` (82% white)
   instead: a JSON envelope over blurred pattern is unreadable, and nesting
   `backdrop-filter` inside `backdrop-filter` is where browsers stop agreeing.
7. **The nav is opaque, not glass.** A sticky element with its own
   `backdrop-filter` over a `position: fixed` backdrop is the one combination that
   renders inconsistently across browsers.
8. **Table rows are not glass, the table shell is.** A backdrop-filter per row,
   forty rows deep, is the reliable way to make a board janky.
9. **Team accents are carried over but unused.** The ten desk colours are
   `--ds-team-*` variables, ready for modules 02–10. Neither b00 nor b01 is a desk
   board, so nothing consumes them and no component grew a prop for them.
10. **`GlassPanel` is superseded by `Card`.** Under this theme `Card` *is* the
    glass panel — same fill, blur, border and shadow, from the theme. Two ways to
    make a panel is the ambiguity the design system exists to remove.
    `handoff/glass/GlassPanel.jsx` is left untouched as the received artifact.
11. **Titles are display, figures are not.** `PageHeader`, `Card` and `Modal`
    titles take Cormorant Garamond; large figures stay Poppins at weight 300, per
    the handoff's explicit `.havn-num` rule.

## 5. The screen vocabulary

Every screen in the v5 briefs is one of six shapes. Build yours as one of them
before inventing a seventh.

### Board — find a record

`PageHeader` (with a `lede` stating the rules) → `Toolbar` (`SearchInput`,
`ChipGroup`, date `Field`s) → `DataTable` → row click opens the detail.

```jsx
<PageHeader
  title="Verification Board"
  lede="empty until you search · max 10 rows · names fetched live, never stored"
/>
<Toolbar>
  <SearchInput placeholder="Applicant name or application id" grow
               value={query} onChange={(e) => setQuery(e.target.value)} />
  <ChipGroup options={['All', 'PASSED', 'FAILED', 'REVIEW']}
             value={filter} onChange={setFilter} />
</Toolbar>
<DataTable
  columns={columns}
  rows={rows}
  total={matches}
  rowKey={(r) => r.applicationId}
  onRowClick={(r) => open(r.applicationId)}
  footnote="newest first"
  empty={<EmptyState title="Search for an applicant to begin">
           Boards start empty on purpose — nothing is fetched until you ask.
         </EmptyState>}
/>
```

Two platform rules are **built into `DataTable`** so nobody has to remember them:
lists are empty by default (`empty`), and at most 10 rows are shown (`maxRows`,
default 10) with the footnote saying so when `total` is larger. Do not re-implement
either, and do not raise the cap.

> **Scope of the cap — read this before you copy a screen.** The empty-default and
> the 10-row limit are the **module case-board** rule (v5 `DECISIONS.md` §3): your
> board holds `applicationId` and nothing else, so every visible row costs a live
> hydration call to the orchestrator. That is what the cap protects.
>
> The **orchestrator's own** live journey board is exempt — it owns the
> applications, hydrates nothing, and exists to be watched. It therefore loads on
> mount and passes `maxRows={50}` explicitly. If you read
> `neo-00/frontend/src/components/ApplicationsScreen.jsx` as your example,
> that is the one line not to copy: a module board that loads on mount, or shows
> more than 10 rows, fails its Definition of Done.

### Detail — understand one record

`PageHeader` with a `badge` and a `meta` line → `Split` → one toned `Card` per rule
section in the main column, the live applicant `KeyValue` in the sidebar, a
`Caption` under it stating that nothing is stored → actions at the bottom.

### Queue — work a backlog

Oldest first, a `DataTable` on the left and the selected case on the right. The
machine's own decision and its workings are **always visible**; the human makes the
final call, they do not guess.

### Config — change the rules without a deploy

A `DataTable` of current values, a `Button` opening a `Modal` with a prefilled
`FormGrid`, and version history beside it. Configuration is insert-only, so the
history *is* the audit trail — show it.

### Panel — make a dependency misbehave

`Slider`s for latency and failure rate, a `ChipGroup` for mode
(HEALTHY / SLOW / DOWN), and a `Timeline` of recent calls. This is the demo that
lands for an integration module.

### Analytics — a window, a ranking, a total

Date `Field`s and an apply `Button`, a `BarChart` of ranked codes, a `DataTable`
of counts beside it, `MetricTile`s across the top.

---

## 6. Composition rules

- **`AppShell` wraps every screen.** `TopNav` goes in its `nav`, content as
  children.
- **One `PageHeader` per screen.** The `lede` states the screen's rules; the `meta`
  line identifies the record.
- **One `Button variant="primary"` per view.** It is the thing the operator came to
  do. Everything else is `secondary` or `ghost`.
- **`Badge` for a decision, `StatusPill` for a condition.** A rejected application
  is a Badge on a record. An unreachable API is an Alert, and `/health` is a
  StatusPill. A business "no" is never an error.
- **`Tag` for machine tokens** — reason codes, product codes, ids. Monospaced,
  never coloured: they are facts, not judgements. Reason codes are displayed,
  never parsed.
- **`Caption` for the rules a screen obeys** — "fetched on open, never stored".
  These read like decoration and are not: they are how a screen tells an auditor
  what it does. Keep them.
- **Every `Field` gets a label.** Placeholders are not labels.
- **`EmptyState` names the next action.** "Pick a row to see the evidence trail",
  never "No data".
- **Spacing comes from the scale.** `Stack gap={4}`, `Grid gap={3}` — not a margin
  you invented.

---

## 7. Content

- **Sentence case** in prose, buttons and empty states. The 11px labels — table
  headers, field labels, section titles — are uppercased **by the stylesheet**, so
  write them in sentence case too and let the CSS shout.
- **Outcome words stay as the contract spells them:** `PASSED`, `VER_MISSING_FIELD`.
  Never prettify a code.
- **Facts are separated by `·`**, prose by commas: `Priya Raman · CREDIT_CARD_STUDENT ·
  submitted 2026-07-21 18:02 · decided with ProductConfig v3`.
- **Numbers carry the message**, not adjectives. "192 in window", not "great
  coverage".
- **No emoji.** Not in the product, not in a commit, not in a comment.
- **Money** is `£3,000`; dates are ISO in operator surfaces (`2026-07-21 18:02`);
  timestamps are UTC.

---

## 8. When the system does not have what you need

In order:

1. **Compose what is there.** Most "missing" components are a `Card` with a
   `KeyValue` in it.
2. **Use the class layer directly.** The CSS is the real system; the JSX is a
   convenience. `<div className="ds-card">` is legitimate.
3. **Build it in your own `src/components/`,** styled with `--ds-*` variables only.
   It is your screen; it just has to speak the same language.
4. **Ask the instructor.** If three teams need the same thing, it belongs in the
   system and gets added centrally, then re-synced to everyone.

What you must not do: edit `design-system/`, add a UI dependency (no Tailwind, no
Radix, no icon package — the stack is React + Vite and `docker compose up` runs
offline), or write a hex colour anywhere in your app.

---

## 9. Accessibility — the four that are not negotiable

- **Focus is visible.** The system draws it. Never `outline: none`.
- **Colour is never alone.** The word is always present; `StatusDot` requires a
  `label`.
- **Controls have names.** `Field` wires label, hint and error to the input by id.
  An icon-only button needs `aria-label`.
- **Tables have real headers.** Use `DataTable`; it emits `<th scope="col">`.

---

## 10. Component index

| group | components |
| --- | --- |
| layout | `AppShell` `TopNav` `PageHeader` `Toolbar` `Split` `Grid` `Stack` `Section` |
| core | `Button` `Badge` `Chip` `ChipGroup` `Tag` `Card` `Divider` `Caption` |
| data | `DataTable` `KeyValue` `MetricTile` `BarChart` `Timeline` `StepTrail` |
| forms | `Field` `TextInput` `Textarea` `Select` `SearchInput` `Checkbox` `Slider` `FormGrid` `FormActions` |
| feedback | `EmptyState` `Alert` `StatusPill` `StatusDot` `Spinner` `CodeBlock` |
| overlay | `Modal` |
| tones | `TONES` `toneMapper` `toTone` |

Every component ships a `.d.ts` beside it documenting every prop. Read that before
guessing — your editor will show it on hover.

---

## 11. Provenance

The theme is the **Havn Glass Console** handoff (received 2026-07-26), kept in each
frontend at `handoff/glass/`. Its raw tokens are carried over verbatim into
`theme/glass.css`; the rules around them are inferred and listed in §4a.

The **component set** has a different origin and is unchanged by the reskin: it was
derived from the v5 wireframe renderer,
`project-requirements/v5/src/generator/wf.py` — its primitives (`button` `chip`
`badge` `table` `card` `kv` `barchart` `input` `modal`) and the screen archetypes in
`wireframes/generic.py` (board · detail · queue · config · panel · chart). That the
look could be replaced wholesale without touching a component is the system working
as intended.

Two earlier explorations are superseded and kept only as prior art: `havn/` at the
repo root (greige/volt, no semantic palette) and the `neo` theme derived from the
v5 wireframes, removed when glass became the single theme.
