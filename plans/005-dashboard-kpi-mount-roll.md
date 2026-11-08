# Plan 005: Dashboard KPI Card Initial Mount Value Roll
**Severity:** LOW  
**Domain:** `animate` / `animation-performance`  
**Purpose:** Subtle delight and feedback on initial application cold-launch; off main thread.

---

## 1. Exact File Paths
- `/app/src/main/java/com/example/kanak/ui/dashboard/DashboardScreen.kt`

---

## 2. Current Code Excerpt
```kotlin
// In DashboardScreen.kt KPI Cards
Text(
    text = "BHD ${BahrainFinancials.formatBhdPlain(summary.todaySalesTotal)}",
    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, color = KanakPrimaryText)
)
```

---

## 3. Target Motion Specs
- **Tool:** Single-shot `LaunchedEffect` driving an animated `Animatable` float (0.0f → 1.0f).
- **Duration:** 280ms.
- **Easing:** `--ease-out`: `CubicBezierEasing(0.23f, 1.0f, 0.32f, 1.0f)`.
- **Properties:** Opacity (`0` → `1`) + vertical translation (`8dp` → `0dp`).
- **Performance Budget:** 16.7ms frame budget; never triggers recalculations on subsequent recompositions.

```kotlin
var hasMounted by rememberSaveable { mutableStateOf(false) }
val alpha = remember { Animatable(if (hasMounted) 1f else 0f) }
val offsetY = remember { Animatable(if (hasMounted) 0f else 8f) }

LaunchedEffect(Unit) {
    if (!hasMounted) {
        launch { alpha.animateTo(1f, tween(200)) }
        launch { offsetY.animateTo(0f, tween(280, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f))) }
        hasMounted = true
    }
}
```

---

## 4. Ordered Steps
1. Add `rememberSaveable` mount tracking to `DashboardScreen`.
2. Apply `offset` and `alpha` modifiers to the Today's Sales and Invoices KPI cards.
3. Keep number formatting locked to exact 3-decimal BHD scaling without intermediate invalid decimals.

---

## 5. Scope Boundaries
- Do NOT animate numbers during normal typing or background sync.
- Do NOT animate on list scrolling.
- Only run once per session launch.

---

## 6. Feel-Check
- Cards settle softly into place when first entering the dashboard.
- If the driver opens the app in a hurry to sell, data is readable immediately without waiting.
