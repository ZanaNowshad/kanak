# Plan 004: Interruptible Thermal Receipt Reveal
**Severity:** LOW  
**Domain:** `animate` / `animation-performance`  
**Purpose:** Preventing jarring visual snap when ESC/POS thermal receipt preview mounts.

---

## 1. Exact File Paths
- `/app/src/main/java/com/example/kanak/ui/invoices/InvoiceDetailScreen.kt`

---

## 2. Current Code Excerpt
```kotlin
// In InvoiceDetailScreen.kt around lines 450-500 (Print Receipt SubView)
Card(
    shape = RoundedCornerShape(8.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, KanakDivider),
    modifier = Modifier.fillMaxWidth().padding(16.dp)
) {
    Text(
        text = receiptPlainText,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        modifier = Modifier.padding(16.dp)
    )
}
```

---

## 3. Target Motion Specs
- **Tool:** Compose `AnimatedVisibility` with `clipToBounds()`
- **Properties:** Vertical reveal (`clip-path` / `expandVertically(expandFrom = Alignment.Top)`) + `opacity: 0` → `1`.
- **Duration:** 250ms.
- **Easing:** `--ease-out`: `CubicBezierEasing(0.23f, 1.0f, 0.32f, 1.0f)`.
- **Interruption:** Immediate snap-to-exit if user taps "Print" or "Back" before animation finishes.

```kotlin
AnimatedVisibility(
    visible = receiptPlainText.isNotBlank(),
    enter = fadeIn(animationSpec = tween(180)) +
            expandVertically(
                animationSpec = tween(250, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)),
                expandFrom = Alignment.Top
            ),
    exit = fadeOut(animationSpec = tween(100))
) {
    // Thermal receipt preview card
}
```

---

## 4. Ordered Steps
1. Wrap the thermal paper receipt preview container in `AnimatedVisibility`.
2. Anchor `expandFrom` to `Alignment.Top` to simulate receipt paper feeding from a physical printer slit.
3. Ensure the printer status indicator pulse does not re-render the receipt text.

---

## 5. Scope Boundaries
- Do NOT animate the receipt text line by line (causes severe jank and delay).
- Do NOT animate if returning to an already rendered receipt.

---

## 6. Feel-Check
- Paper preview expands downward smoothly, like paper emerging from the van printer.
- Tapping "Print" while expanding immediately triggers printing without waiting for the visual reveal to complete.
