# Initial gameplay design

## Conjure Food

The original SPD hunger and food systems remain intact.

When the study feature is enabled for a run, every hero has access to **Conjure Food**. Completing Anki reviews advances the current conjuration. When the required count is reached, the hero receives one conjured ration.

### Cost curve

Initial target:

`1, 2, 3, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10...`

The cost:

- is counted per run;
- increases after each successfully conjured ration;
- never exceeds 10 reviews;
- resets on a new run;
- is stored in the save;
- should be defined in one data/function location so playtesting can tune it easily.

A review counts toward conjuration only when a card is actually resolved/submitted to Anki. Repeated attempts at an unresolved card do not create duplicate progress.

### Classic mode

Conjure Food is a per-run option.

When disabled:

- the ability is unavailable;
- hunger is unchanged;
- normal SPD food generation is unchanged;
- study code must not modify hunger behind the scenes.

The default for new runs can be stored in global settings, but the selected value is copied into the save when the run begins.

### Conjured ration

Use a distinct item rather than silently creating a normal ration.

Initial restrictions:

- satisfies hunger;
- no sale value;
- not an alchemy ingredient;
- should not become an infinite secondary-buff generator;
- visually distinguishable from dungeon-generated food.

Exact satiety value and interactions with food talents will be decided after inspecting current SPD v4.0.1 food/talent code.

## Anki review UI

The first implementation should reuse the successful typed-review flow from the Andor's Trail integration:

1. show card question;
2. player types an answer;
3. reveal/check;
4. show Again / Hard / Good / Easy and scheduling intervals;
5. submit the selected rating to AnkiDroid;
6. count the resolved review toward Conjure Food.

Review UI must use a single Android backend adapter so stock AnkiDroid and Retry behave the same from the player's perspective.

## Separation from upstream

Prefer small, isolated SAD-specific classes and minimal call sites in upstream SPD code. This should make future upstream merges practical.

Suggested boundaries:

- `core`: platform-neutral study state, Conjure Food progress, save data, game UI hooks.
- `android`: AnkiDroid provider discovery, permission handling, ContentResolver implementation.
- other platforms: no-op/unavailable study provider unless implemented later.
