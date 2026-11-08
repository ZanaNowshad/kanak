# Plan 001: Line Item Counter Tactile Feedback
**Severity:** HIGH  
**Domain:** `animate` / `improve-animations`  
**Purpose:** Physical button press feedback for high-frequency van sales operations (100+/day).

---

## 1. Exact File Paths
- `/app/src/main/java/com/example/kanak/ui/invoices/InvoiceComposerScreen.kt`

---

## 2. Current Code Excerpt
```kotlin
// In InvoiceComposerScreen.kt (around lines 530-580)
IconButton(
    onClick = { onDecrementQty(item.productId) },
    modifier = Modifier.size(32.dp)
) {
    Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = KanakPrimaryText)
}
Text(
    text = item.qty.stripTrailingZeros().toPlainString(),
    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
)
IconButton(
    onClick = { onIncrementQty(item.productId) },
    modifier = Modifier.size(32.dp)
) {
    Icon(Icons.Default.Add, contentDescription = "Increase", tint = KanakPrimaryText)
}
```

---

## 3. Target Motion Specs
From Emil Kowalski Button Press Recipe:
- **Properties:** `transform` (scale) only. No layout shifts.
- **Scale:** `0.97` on press, `1.0` on release.
- **Duration:** 160ms release, 100ms press.
- **Easing:** `--ease-out`: `CubicBezierEasing(0.23f, 1.0f, 0.32f, 1.0f)`
- **Gate:** Bypassed when `LocalReducedMotion.current == true`.

```kotlin
val interactionSource = remember { MutableInteractionSource() }
val isPressed by interactionSource.collectIsPressedAsState()
val scale by animateFloatAsState(
    targetValue = if (isPressed) 0.96f else 1.0f,
    animationSpec = tween(
        durationMillis = if (isPressed) 100 else 160,
        easing = CubicBezierEasing(0.23f, 1.0f, 0.32f, 1.0f)
    ),
    label = "qty_btn_scale"
)
```

---

## 4. Ordered Steps
1. Create a reusable modifier `Modifier.tactilePressFeedback(interactionSource)`.
2. Apply `interactionSource` and scale modifier to quantity `+` and `-` buttons in `InvoiceComposerScreen`.
3. Wrap numeric quantity indicator with subtle vertical slide/fade (80ms) on value change using `AnimatedContent`.
4. Ensure touch target remains 48dp minimum despite the 32dp visual size.

---

## 5. Scope Boundaries
- Do NOT animate line item additions or deletions beyond opacity/fade.
- Do NOT add vibration or audio effects.
- Do NOT introduce delays into calculation or state mutations.

---

## 6. Feel-Check
- Rapid tapping of `+` (5 taps in 1 second) must feel crisp, responsive, and physical.
- The scale-down must never feel sticky or delayed.
- Numbers must change instantly without waiting for any animation to finish.
