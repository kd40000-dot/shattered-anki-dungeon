# AnkiDroid compatibility contract

Shattered Anki Dungeon supports both stock AnkiDroid and the custom Retry build without requiring separate game builds.

## Providers

| Backend | Android package | ContentProvider authority | Permission |
| --- | --- | --- | --- |
| Retry | `com.ichi2.anki.retry` | `com.ichi2.anki.retry.flashcards` | `com.ichi2.anki.retry.permission.READ_WRITE_DATABASE` |
| Stock | `com.ichi2.anki` | `com.ichi2.anki.flashcards` | `com.ichi2.anki.permission.READ_WRITE_DATABASE` |

Provider preference is Retry first, then stock. This is a preference only: normal review gameplay must work against stock AnkiDroid.

## Shared ReviewInfo contract

Both backends expose the AnkiDroid ReviewInfo endpoint at:

`content://<authority>/schedule`

The game uses the shared columns:

- `note_id`
- `ord`
- `button_count`
- `next_review_times`
- `media_files`
- write: `answer_ease`
- write: `time_taken`

The game can query the next due card with `limit=1`, then query the card via:

`content://<authority>/notes/<note_id>/cards/<ord>`

Only capability checks may branch on backend. Gameplay code must talk to a game-owned interface rather than constructing Anki URIs directly.

## Runtime selection

1. Probe Retry authority.
2. If unavailable or unusable, probe stock authority.
3. If both are available, default to Retry.
4. Expose the chosen backend name in settings/status.
5. If neither is available, Anki-powered actions fail gracefully and explain that AnkiDroid access is unavailable.

Do not infer availability only from installed package names. Resolve/probe the provider because provider authorities are the actual integration surface.

## Permissions

Each provider has its own signature/name-spaced read-write permission. Android manifest declarations should include both permissions. At runtime, request only the permission for the selected backend when necessary.

The Retry build may grant access to known game packages through its debug/provider logic, but Shattered Anki Dungeon must not rely on that behavior for stock compatibility.

## Internal abstraction

The Android layer should provide something equivalent to:

```java
interface AnkiReviewBackend {
    Availability availability();
    ReviewCard nextCard();
    boolean answer(ReviewCard card, int ease, long timeTakenMs);
    BackendCapabilities capabilities();
}
```

Core SPD code should consume a platform-neutral study service, not Android `ContentResolver` directly. That keeps Android-specific provider access out of the core game model and leaves desktop/iOS builds compilable.

## Retry-only capabilities

Any future feature that depends on custom Retry behavior must be optional and guarded by an explicit capability flag. It must never silently change the meaning of stock AnkiDroid reviews.

## Typed-answer behavior

Typed-answer checking belongs in Shattered Anki Dungeon so it behaves consistently with either backend.

Initial matching policy follows the existing Andor integration:

- case-insensitive
- accent-sensitive
- punctuation-sensitive
- no extra-word matching by default
- multiple accepted answers may be separated with `|`

We can later make these settings configurable without changing the Anki provider contract.
