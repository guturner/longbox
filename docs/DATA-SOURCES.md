# Data sources

## Chosen: Metron (free)

- **What:** a community comic database with a REST API. Free account, HTTP Basic auth in the `Authorization` header (never in URLs).
- **UPC lookup:** `GET /api/issue/?upc=<17 digits>`. Metron's filter is labelled *UPC Code (main or variant cover)* and matches case-insensitively (`comicsdb/filters/issue.py` in the Metron source on GitHub).
- **Fields we use:** `store_date` (release date), `cover_date`, `desc` (summary), `image` (Cover A), `variants[]` (name, upc, image), `arcs[]` (events and crossovers), `credits[]` (the credit `id` is the **creator** id, per `CreditReadSerializer`), creator `desc` (bio) and `image`.
- **Weekly releases:** `store_date_range_after/before` plus `publisher_name` (icontains).
- **Limits:** 20 requests/minute burst and 5,000/day sustained (`DEFAULT_THROTTLE_RATES` in Metron's settings). The server paces itself at 18/min and 4,500/day and honours `Retry-After`.
- **Licence:** Metron's site content is **CC BY-SA 4.0**, so every screen that shows its data carries an attribution line (`MetronAttribution`). Cover images belong to their publishers; this is a private, personal collection app, so don't republish them.
- **Cost:** $0. Metron accepts donations through OpenCollective if you want to support it.

### Publisher names

The `publisher_name` parameter is case-insensitive but must match a name in Metron's publisher index. Verify with `/api/publisher/?name=<term>` before adding a new entry to `FOLLOWED_PUBLISHERS`. Known verified names:

| Name in `FOLLOWED_PUBLISHERS` | Metron id | Notes |
|---|---|---|
| `DC Comics` | 2 | Correct. |
| `Marvel` | — | Correct. |
| `Dynamite Entertainment` | — | Correct. |
| `Titan Comics` | 123 | Correct. `"Titan Books"` does **not** exist in Metron and always returns 0 results — verified via `/api/publisher/?name=Titan`. |

### Numbering

Metron tracks each relaunch as a separate series and uses that series' issue numbers. League of Comic Geeks and some other databases display **legacy numbers** that count continuously from the first issue of the original run (e.g., Action Comics #1105 in Metron = #1200 on LOCG, counting from 1938). Both point to the same comic. This is a display difference, not a data error, and it doesn't affect the app.

### Data lag for future releases

Metron is community-maintained. For weeks two or more weeks out, the entry count is typically lower than on retail-feed databases. Data fills in as the community submits issues. The app will show fewer titles for a future week than League of Comic Geeks does for the same week — this is expected.

## Not used, and why

| Source | Why not |
|---|---|
| **League of Comic Geeks** | No public API. Metron's README says LoCG's API is *only available to retailers*. Scraping their site would conflict with their Terms of Use, so it stays a browser bookmark. |
| **Comic Vine** | Free, but *non-commercial use only*, 200 requests/resource/hour, and there's no UPC filter. Fine as a fallback for creator bios if Metron's are thin (Metron stores `cv_id` for cross-reference). |
| **Marvel API** | The developer portal redirects to marvel.com and the gateway was returning errors as of 2026, so it isn't usable. |
| **Grand Comics Database** | Great bibliographic data (CC BY-SA) with barcodes, but its strength is older books; Metron is stronger for current weekly releases. |

## Barcode format

Direct-market comics print a **UPC-A** that identifies the *series* plus a **5-digit add-on** (EAN-5): digits 1–3 = issue number, digit 4 = cover variant (1 = Cover A), digit 5 = printing (Diamond Comic Distributors, *UPC Guide for Comics*, diamondcomics.com).

- That's why the Android scanner uses **ZXing**: it returns the add-on as `UPC_EAN_EXTENSION` metadata. Google ML Kit's barcode format list includes UPC-A and EAN-13 but not add-on codes.
- Older books may have a 2-digit periodical add-on or none. Those parse as series-only, and the ownership check reports them as "not owned" unless the exact code was scanned before.
- Variant numbering in the 4th digit isn't always consistent with the catalog's Cover B/C lettering. The resolver prefers an exact UPC match on the variant and only falls back to catalog position.
