# Plan 006: System-Wide Reduced Motion Accessibility Gate
**Severity:** HIGH  
**Domain:** `animate` / `improve-animations`  
**Purpose:** Honor Android OS "Remove animations" accessibility setting across all custom motion.

---

## 1. Exact File Paths
- `/app/src/main/java/com/example/ui/theme/MotionTokens.kt`
- `/app/src/main/java/com/example/MainActivity.kt`

---

## 2. Current Code Excerpt
```kotlin
// Currently animations use standard tween/spring with no check for system accessibility settings.
```

---

## 3. Target Motion Specs
- **Mechanism:** Query Android `Settings.Global.ANIMATOR_DURATION_SCALE` or `LocalConfiguration`.
- **Composition Local:** Provide `val LocalReducedMotion = staticCompositionLocalOf { false }`.
- **Behavior:** When enabled:
  - All durations clamp to `0ms`.
  - Transitions switch to `EnterTransition.None` / `ExitTransition.None`.
  - Spring animations complete instantly.

```kotlin
val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
fun rememberIsReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        val resolver = context.contentResolver
        val durationScale = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1.0f)
        durationScale == 0.0f
    }
}
```

---

## 4. Ordered Steps
1. Create `/app/src/main/java/com/example/ui/theme/MotionTokens.kt` with `LocalReducedMotion` definition.
2. In `MainActivity.kt`, wrap `KanakApp` with `CompositionLocalProvider(LocalReducedMotion provides isReducedMotion)`.
3. Wrap all animated transitions with `if (LocalReducedMotion.current) instant else animated`.

---

## 5. Scope Boundaries
- Do NOT disable visual state changes (selected colors, badges, text updates).
- Only disable the motion interpolation between states.

---

## 6. Feel-Check
- Enabling "Remove animations" in Android developer settings makes every screen, tab, and dialog snap instantly into place with 0ms transition latency.
