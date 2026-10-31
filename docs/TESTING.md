# Device smoke-test checklist (v0.1.0)

Runtime verification needs a physical Android phone (build sandbox has no KVM; emulator can't boot).

Setup:
1. Sideload `chat-launcher-debug.apk` (enable install from unknown sources if prompted).
2. When asked, set Chat Launcher as the default home app (or Settings > Apps > Default apps > Home app).

Checks:
- [ ] Home button lands on the chat screen.
- [ ] Type "whatsapp" (or any installed app name) -> suggestion chip appears -> tap -> app opens.
- [ ] "send this message to Avinash: hello" -> app picker appears listing share targets -> pick WhatsApp -> draft text is prefilled -> confirm/cancel there (app must never send by itself).
- [ ] Settings -> create focus profile hiding e.g. Instagram -> "focus <name>" -> Instagram no longer appears in suggestions, exact-name launch, or the draft picker.
- [ ] "focus off" -> hidden apps return.
- [ ] Settings -> add Anthropic API key -> ask an open question -> chat replies; HTML answers render without scripts or remote images.
- [ ] App works after device reboot (still the home app).

Known limitations to verify against expectations:
- System share UI is bypassed by design (custom picker); target app handles the actual send.
- Remote images in HTML responses are blocked by default.
