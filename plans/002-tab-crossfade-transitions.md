# Plan 002: Primary Navigation Tab Crossfade Transitions
**Severity:** MEDIUM  
**Domain:** `animate` / `improve-animations`  
**Purpose:** Spatial consistency and state indication when switching between the 5 primary tabs (Tens/day).

---

## 1. Exact File Paths
- `/app/src/main/java/com/example/kanak/ui/navigation/KanakApp.kt`

---

## 2. Current Code Excerpt
```kotlin
// In KanakApp.kt (lines 180-210)
when (selectedTab) {
    PrimaryTab.HOME -> DashboardScreen(...)
    PrimaryTab.PRODUCTS -> ProductsScreen(...)
    PrimaryTab.CUSTOMERS -> CustomersScreen(...)
    PrimaryTab.INVOICES -> InvoicesHistoryScreen(...)
    PrimaryTab.STOCK -> StockScreen(...)
}
```

---

## 3. Target Motion Specs
- **Tool:** Compose `AnimatedContent`
- **Properties:** `opacity` + subtle scale (`0.99` → `1.0`). No sliding across screen edges (avoids spatial disorientation between non-linear tabs).
- **Duration:** 180ms.
- **Easing:** `--ease-out`: `CubicBezierEasing(0.23f, 1.0f, 0.32f, 1.0f)`
- **Exit:** Fade out over 120ms (`CubicBezierEasing(0.77f, 0.0f, 0.175f, 1.0f)`).
- **Reduced Motion:** Cut (0ms).

```kotlin
AnimatedContent(
    targetState = selectedTab,
    transitionSpec = {
        if (isReducedMotion) {
            EnterTransition.None togetherWith ExitTransition.None
        } else {
            (fadeIn(animationSpec = tween(180, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f))) +
             scaleIn(initialScale = 0.99f, animationSpec = tween(180, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f))))
                .togetherWith(
                    fadeOut(animationSpec = tween(120, easing = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)))
                )
        }
    },
    label = "tab_switch_transition"
) { tab ->
    when (tab) { ... }
}
```

---

## 4. Ordered Steps
1. Replace static `when(selectedTab)` in `AuthenticatedApp` with `AnimatedContent`.
2. Apply the enter/exit specs above.
3. Add `scale(0.96)` tactile press feedback to the NavigationBar items.

---

## 5. Scope Boundaries
- Do NOT use horizontal slide transitions between tabs (tabs are not a linear pager).
- Do NOT defer heavy ViewModel data loads inside the transition.

---

## 6. Feel-Check
- Rapidly alternating between "Home" and "Products" tabs must instantly interrupt the previous transition without stutter or blank frames.
- Content appears anchored, never drifting horizontally.
