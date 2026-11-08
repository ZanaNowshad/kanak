# Plan 007: Centralized Motion Design Tokens
**Severity:** MEDIUM  
**Domain:** `animate` / `improve-animations`  
**Purpose:** Establish cohesive, centralized motion curves, spring configs, and durations matching Emil Kowalski's token table.

---

## 1. Exact File Paths
- `/app/src/main/java/com/example/ui/theme/MotionTokens.kt`

---

## 2. Current Code Excerpt
Currently, each screen specifies ad-hoc durations or defaults (`tween(250)`).

---

## 3. Target Motion Specs
Authoritative curves and durations from the Emil Kowalski design tokens:

```kotlin
package com.example.ui.theme

import androidx.compose.animation.core.*

object MotionCurves {
    // --ease-out: cubic-bezier(0.23, 1, 0.32, 1)
    val EaseOut = CubicBezierEasing(0.23f, 1.0f, 0.32f, 1.0f)

    // --ease-in-out: cubic-bezier(0.77, 0, 0.175, 1)
    val EaseInOut = CubicBezierEasing(0.77f, 0.0f, 0.175f, 1.0f)

    // --ease-drawer: cubic-bezier(0.32, 0.72, 0, 1)
    val EaseDrawer = CubicBezierEasing(0.32f, 0.72f, 0.0f, 1.0f)
}

object MotionDuration {
    const val ButtonPress = 160
    const val Tooltip = 150
    const val Dropdown = 200
    const val Modal = 250
    const val Drawer = 350
    const val TabSwitch = 180
}

object MotionSprings {
    val Gentle = spring<Float>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
    val Pop = spring<Float>(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium)
}
```

---

## 4. Ordered Steps
1. Create `MotionTokens.kt` in `com.example.ui.theme`.
2. Export `MotionCurves`, `MotionDuration`, and `MotionSprings`.
3. Refactor all animations in `InvoiceComposerScreen`, `DashboardScreen`, and `KanakApp` to reference these tokens directly.

---

## 5. Scope Boundaries
- Do NOT invent custom cubic bezier curves outside the defined tokens.
- Keep UI transitions strictly under 300ms (drawers max 350ms).

---

## 6. Feel-Check
- Motion throughout the app feels like it was designed by a single hand—predictable, physical, and restrained.
