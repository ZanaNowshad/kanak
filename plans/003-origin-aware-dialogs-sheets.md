# Plan 003: Origin-Aware Dialogs & Bottom Sheet Transitions
**Severity:** MEDIUM  
**Domain:** `animate` / `improve-animations`  
**Purpose:** Physicality and spatial origin for confirmation dialogs and bottom sheets (Tens/day).

---

## 1. Exact File Paths
- `/app/src/main/java/com/example/kanak/ui/components/CommonCards.kt`
- `/app/src/main/java/com/example/kanak/ui/products/ProductsScreen.kt`

---

## 2. Current Code Excerpt
```kotlin
// In CommonCards.kt (KanakConfirmDialog)
AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { Text(message) },
    confirmButton = { ... },
    dismissButton = { ... }
)
```

---

## 3. Target Motion Specs
From Emil Kowalski Modal & Drawer Recipes:
- **Modal Dialog:** Centered `scale(0.95)` → `1.0` with `opacity: 0` → `1`.
  - Enter Duration: 220ms.
  - Easing: `--ease-out`: `CubicBezierEasing(0.23f, 1.0f, 0.32f, 1.0f)`.
- **Drawer / Bottom Sheet:** `translateY` from screen bottom.
  - Enter Duration: 350ms.
  - Easing: `--ease-drawer`: `CubicBezierEasing(0.32f, 0.72f, 0f, 1.0f)`.
  - Exit: Same curve in reverse, 250ms.
- **Scrim:** Opacity `0` → `0.5`, 200ms linear.

---

## 4. Ordered Steps
1. In `KanakConfirmDialog`, wrap modal card in explicit scale + fade enter transition.
2. In bottom sheets (such as filter and export drawers), apply `slideInVertically` with `--ease-drawer` curve.
3. Configure velocity-gated swipe-to-dismiss threshold (`0.11` velocity coefficient).

---

## 5. Scope Boundaries
- Do NOT animate dialog content sequentially (no stagger inside the dialog).
- Do NOT scale from `0`; always start from `0.95`.

---

## 6. Feel-Check
- Dialogs must feel like they are placed onto the screen with substance, not popping out of thin air.
- Dismissing by tapping outside or dragging down must feel natural with physical momentum.
