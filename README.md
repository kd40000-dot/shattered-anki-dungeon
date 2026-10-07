# Shattered Anki Dungeon

An Android fork of [Shattered Pixel Dungeon](https://github.com/00-Evan/shattered-pixel-dungeon) that integrates Anki reviews into the game loop.

## Project goals

- Keep Shattered Pixel Dungeon recognizable and playable as SPD.
- Make studying useful to gameplay rather than a separate interruption.
- Support both:
  - stock AnkiDroid (`com.ichi2.anki`, provider authority `com.ichi2.anki.flashcards`)
  - the custom AnkiDroid Retry build (`com.ichi2.anki.retry`, provider authority `com.ichi2.anki.retry.flashcards`)
- Do not require a custom AnkiDroid build for the core study loop.
- Preserve a classic-SPD option for mechanics that study features modify.

## First study mechanic: Conjure Food

Hunger stays intact. Every hero can optionally gain **Conjure Food**, which creates a non-exploitable conjured ration after completing Anki reviews.

The planned per-run cost ramps gradually from 1 review toward a hard cap of 10. Classic mode disables Conjure Food and leaves the normal food struggle untouched.

Initial progression target:

`1, 2, 3, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10...`

The exact curve is intentionally data-driven so it can be tuned after playtesting.

## Anki compatibility

The game should not link itself to one hard-coded AnkiDroid package.

A provider adapter will probe known compatible authorities at runtime and expose one internal interface to the game:

1. Retry provider: `content://com.ichi2.anki.retry.flashcards`
2. Stock provider: `content://com.ichi2.anki.flashcards`

Both use the AnkiDroid ReviewInfo `/schedule` contract for querying due cards and submitting ratings. Retry-specific behavior can be enabled as an optional capability, never as a requirement for ordinary reviews.

See `docs/ANKI_COMPATIBILITY.md` for the integration contract.

## Upstream

Upstream game: `00-Evan/shattered-pixel-dungeon`

This repository starts from upstream SPD and will keep upstream changes separable from Shattered Anki Dungeon changes wherever practical.
