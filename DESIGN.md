# BidShift Design System And UI/UX Audit

Date: 2026-05-31

## 1. Design Read

BidShift is a JavaFX marketplace and auction operations app. The right direction is not a wild landing page redesign. It should feel like a premium trading floor: confident, fast, readable, and trustworthy.

Taste dials for this project:

- Variance: 5/10. Use light asymmetry in hero and card rhythm, but keep workflows predictable.
- Motion: 3/10. JavaFX should use restrained hover, pressed, toast, and state transitions. Avoid cinematic animation that can hurt demo stability.
- Density: 7/10. This is a repeated-use auction tool, so information density matters more than decorative whitespace.

Recommended design mode: targeted evolution, not full redesign.

## 2. Current Strengths

- Brand identity is clear: BidShift red, dark/light mode, custom logo assets, auction-specific imagery.
- Main user workflows now have structure: home, auction room, account hub, cart, checkout wizard, seller orders, admin.
- Checkout wizard is much stronger than the earlier modal: address confirmation, ATM confirmation, invoice review, and phone visibility are coherent.
- Dark dashboard and dark auction room already move toward a premium trading-floor feel.
- Empty states and runtime degraded states exist for important areas such as cart, seller orders, SMTP/Gemini, and AI chat.

## 3. Current UI/UX Issues To Fix First

### P0 - Manual responsive visual QA is still missing

JavaFX smoke tests prove FXML loads, but they do not prove visual fit at small laptop sizes, projector resolution, or high DPI scaling. Before demo, manually test:

- `1180 x 760` dashboard minimum.
- `1366 x 768` common classroom projector size.
- Maximized desktop.
- Dark mode and light mode.
- Checkout wizard with long Vietnamese address strings.
- Auction room with long item names and no product image.

### P1 - Fallback imagery must never look product-specific

The auction room previously used `iP17` as a product-image fallback. This makes unrelated items look like a phone listing. Fallbacks must be neutral and semantic:

- Mark: `BID`
- Text: `Chưa có ảnh sản phẩm`
- Current status: fixed in the auction room FXML and controller fallback copy.

### P1 - Dark mode needs one consistent shadow family

Pure black shadows make dark UI muddy. Use a tinted dark-red charcoal shadow such as `rgba(12, 2, 4, 0.28-0.48)` across dark dashboard and auction room.

### P1 - Product UI should not adopt landing-page tropes

Avoid Awwwards-style hero excess, giant scroll chapters, GSAP-like motion, and decorative bento patterns inside core workflows. Those patterns are useful for a marketing site, not for auction, checkout, admin, and seller operations.

### P2 - Icon language is inconsistent

The app currently uses text symbols and emoji-like glyphs in several controls. For a future pass, move toward a single icon system. In JavaFX, use one of these paths:

- SVG assets in `src/main/resources/assets/icons`.
- Ikonli with one icon pack.
- Text-only labels where icons are not necessary.

Do not mix random Unicode symbols, emoji-style glyphs, and image icons in the same toolbar.

### P2 - Typography can be more intentional

Current font stack uses Aptos, Segoe UI, and Bahnschrift. That is acceptable on Windows and better than browser defaults. Keep sans-serif only for this product UI.

Recommended hierarchy:

- Display and page titles: Aptos Display or Bahnschrift, weight 800-900.
- Body and form text: Aptos or Segoe UI, weight 400-600.
- Numeric auction values: use tabular-feeling spacing where possible; keep prices visually aligned.

### P2 - Error states should be inline when tied to forms

Toast is good for transient success/failure, but address, phone, ATM, bid amount, and profile validation errors should eventually sit under the related field. This reduces repeated trial-and-error.

Current status: checkout address and ATM confirmation now use inline validation panels. Profile and bidding forms should follow the same pattern in a later pass.

## 4. Color Palette And Roles

Primary palette:

- Canvas Light `#f6f7fb`: default app background.
- Surface White `#ffffff`: cards, panels, form fields.
- Ink `#111318`: primary text.
- Soft Steel `#69707d`: secondary text and hints.
- BidShift Red `#e50914`: primary actions, active state, important status.
- Deep Red `#b70710`: pressed/gradient endpoint.
- Soft Red `#fff0f1`: subtle active and warning surfaces.
- Line `#e5e8ef`: structural borders.

Dark palette:

- OLED Charcoal `#050505`: dark app background.
- Raised Charcoal `#111116`: cards and panels.
- White Ink `#f7f7f7`: primary text.
- Muted White `rgba(255,255,255,0.64)`: secondary text.
- Hot Red `#ff2635`: primary action in dark mode.
- Dark Red Shadow `rgba(12,2,4,0.28-0.48)`: dark elevation.

Rules:

- Use BidShift red as the only dominant accent.
- Do not introduce purple/blue neon gradients.
- Do not use pure `#000000` for large surfaces.
- Shadows should be tinted, not generic black.

## 5. Layout Principles

- Keep operational screens predictable: top navigation for marketplace, left account sidebar for profile/cart/seller workflows, split auction room for bidding and live history.
- Use cards only when they group a real object: auction item, cart item, seller order, notification, admin table panel.
- Keep radius rules consistent:
  - Main panels: 14-18px.
  - Buttons: pill radius.
  - Image frames: 11-15px.
- Keep forms in two columns on desktop when the task is data entry; collapse only if a future responsive pass adds narrow-window support.
- Do not use nested cards inside cards unless the inner unit is an actual repeated item.

## 6. Component Rules

### Buttons

- Primary: red fill, white text, pressed state with `translate-y: 1` and slight scale.
- Secondary: light surface or dark translucent surface, red hover border.
- Ghost: low emphasis, used for close/back/edit.
- Danger: reserved for destructive admin actions.

Every button must have readable contrast in light and dark mode.

### Forms

- Label above field.
- Prompt text should be specific, not generic.
- Focus ring should use BidShift red.
- Field validation should eventually be inline below the field.

### Cards and tables

- Auction cards should prioritize image, item name, status, current price, time.
- Cart and seller order rows should prioritize product, buyer/seller, payment status, delivery status, address, phone.
- Admin tables should stay dense and utilitarian; do not add marketing-style decoration.

### Empty states

Every empty state should answer two questions:

- What happened?
- What should the user do next?

Good examples:

- `Giỏ hàng đang trống. Sản phẩm bạn thắng đấu giá sẽ xuất hiện ở đây.`
- `Chưa có đơn bán. Khi người thắng đấu giá checkout, đơn cần giao sẽ xuất hiện tại đây.`

## 7. Motion And Interaction

JavaFX motion should be restrained:

- Pressed feedback on buttons.
- Hover states on cards and toolbar buttons.
- Toasts for transient events.
- No large scroll choreography in core workflows.
- Avoid animation of layout properties that could cause jitter.

Recommended future polish:

- Add subtle fade-in for overlay panels.
- Add skeleton rows for auction list, cart, seller orders, and admin tables.
- Add explicit disabled state copy for AI chat and SMTP-dependent OTP flows.

## 8. Recommended Next UI Updates

### Must do before demo

1. Run manual visual QA across light/dark dashboard, auction room, account hub, checkout wizard, and admin.
2. Check long Vietnamese names, addresses, and item titles for wrapping.
3. Verify projector resolution `1366 x 768`.
4. Confirm no product-specific fallback imagery appears for unrelated items.
5. Keep `.idea` metadata out of the submission commit unless intentionally required.

Current snapshot support:

- Run `mvn "-Dtest=JavaFxVisualSnapshotTest" "-Dauction.ui.snapshots=true" test`.
- Output goes to `target/ui-snapshots`.
- Current coverage: login, register, dashboard light/dark, authenticated account profile, account cart, notifications, seller orders, add-item modal, checkout address, checkout ATM, checkout invoice, auction room light/dark, auction room with long title/no image, admin panel at `1366 x 768`.
- Snapshot QA found and fixed: top navigation truncation at projector width, English admin table placeholders, unclear checkout active step state, truncated payment method copy, cramped cart thumbnail fallback, compressed auction-room image fallback, raw auction status text.

### High value after demo

1. Extend JavaFX visual snapshots with dark authenticated account states and narrow-width stress states.
2. Extend inline validation labels from checkout to profile, listing, and bid forms.
3. Create a single icon strategy and replace mixed text symbols.
4. Add skeleton loading states for account hub tabs and auction list.
5. Improve keyboard focus order in checkout, login, registration, and auction bid form.

### Larger redesign later

1. Build a true responsive narrow-window layout for dashboard and checkout.
2. Separate marketplace browsing design from operational account/admin design.
3. Create a public marketing landing page only if the project needs presentation polish outside the JavaFX app.
4. Add a design token registry instead of repeated CSS variables across theme files.

## 9. Anti-Patterns Banned For BidShift

- No generic placeholder product names in fallback UI.
- No purple/blue neon AI gradients.
- No random icon families.
- No unreadable red-on-red or white-on-white buttons.
- No huge decorative animation in bidding or checkout flows.
- No modal for simple inline edits unless it prevents a destructive mistake.
- No placeholder copy such as `Lorem ipsum`, `Acme`, `John Doe`, or fake perfect stats.
- No raw stack traces or developer messages in user-facing UI.

## 10. Completion Criteria For A Future Full UI Pass

A UI pass is complete only when:

- `mvn test` passes, including JavaFX smoke tests.
- JavaFX snapshot test is run and reviewed for projector-size layout.
- Every main screen is manually checked in light and dark mode.
- Checkout wizard is tested with COD and ATM.
- Auction room is tested with no product image, long product name, guest user, seller, and bidder.
- Admin panel loads without server data crashing the screen.
- README or docs include screenshots or a short demo checklist.
