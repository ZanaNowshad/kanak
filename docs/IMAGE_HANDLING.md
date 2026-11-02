# Kanak — Product Image Handling Architecture

Implements Section 10 of the Master Autonomous Android Engineering Directive.

---

## 1. Automatic Image Generation Method (Canonical Default)

The default product image is constructed using the combination of **barcode** and **product name**:

```kotlin
query = barcode.trim() + " " + productName.trim()
encoded = URLEncoder.encode(query, "UTF-8")
url = "https://tse1.mm.bing.net/th?q=$encoded"
```

### Characteristics
- **No Bing Search API key required**: Utilizes the public Bing thumbnail endpoint (`tse1.mm.bing.net/th?q=`).
- **Normalized whitespace**: Consecutive spaces are collapsed into single spaces, encoded as `+`.
- **Leading-zero preservation**: Barcode strings are preserved in full.
- **Asynchronous preview**: Coil loads the candidate image asynchronously in the product form.
- **Non-blocking failure**: If the thumbnail endpoint fails or returns an unusable response, a neutral placeholder is shown, and the user can save the product without hindrance.

---

## 2. Three Supported Image Sources & Precedence

1. **User-Uploaded Image** (Highest priority)
   - Selected using Android's system Photo Picker (`ActivityResultContracts.PickVisualMedia`).
   - Requires zero storage permissions.
   - Stored via Google Drive through the trusted gateway or local device content URI.

2. **User-Pasted Custom URL**
   - The user can paste a direct HTTPS image link.
   - Overrides the automatic thumbnail.

3. **Automatic Bing Thumbnail** (Default fallback)
   - Automatically regenerated when barcode or name is updated, unless a manual override exists.

4. **Neutral Placeholder**
   - Clean stylized icon when no photo is available or the network is offline.

---

## 3. Removing an Override

When editing a product, tapping **Use Automatic Image** removes the manual override and recomputes the candidate Bing thumbnail URL from the current barcode and name.
