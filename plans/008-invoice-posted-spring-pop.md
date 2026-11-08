# Plan 008: Invoice Posted Success Badge Spring Pop
**Severity:** LOW  
**Domain:** `animate` / `improve-animations`  
**Purpose:** Physical feedback and closure confirming invoice posting finality (Tens/day).

---

## 1. Exact File Paths
- `/app/src/main/java/com/example/kanak/ui/invoices/InvoiceDetailScreen.kt`

---

## 2. Current Code Excerpt
```kotlin
// In InvoiceDetailScreen.kt around lines 100-140
Surface(
    shape = RoundedCornerShape(12.dp),
    color = KanakSuccessBackground,
    modifier = Modifier.fillMaxWidth()
) {
    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = KanakSuccess)
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text("Invoice Posted Successfully", fontWeight = FontWeight.Bold, color = KanakSuccess)
            Text("Stock has been updated.", style = MaterialTheme.typography.bodySmall, color = KanakPrimaryText)
        }
    }
}
```

---

## 3. Target Motion Specs
From Emil Kowalski Pop-In Recipe:
- **Properties:** `scale(0.92)` → `1.0` + `opacity: 0` → `1`.
- **Tool:** Spring physics animation (`dampingRatio = 0.7f`, `stiffness = Spring.StiffnessMedium`).
- **Duration equivalent:** ~250ms settling time.
- **Trigger:** Runs exactly once when an invoice is freshly saved and viewed.

```kotlin
val scale = remember { Animatable(0.92f) }
val alpha = remember { Animatable(0f) }

LaunchedEffect(Unit) {
    launch { alpha.animateTo(1f, tween(120)) }
    launch {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.7f,
                stiffness = Spring.StiffnessMedium
            )
        )
    }
}
```

---

## 4. Ordered Steps
1. Add `scale` and `alpha` modifiers to the success banner in `InvoiceDetailScreen`.
2. Connect to `LaunchedEffect(invoice.invoiceId)` so it triggers on entry.
3. Gate with `LocalReducedMotion`.

---

## 5. Scope Boundaries
- Do NOT add confetti or multi-bounce loops.
- Do NOT delay receipt generation or printing actions.

---

## 6. Feel-Check
- The green success card arrives with an authoritative physical stamp, giving the van driver visual confidence that the invoice is saved and stock is reconciled.
