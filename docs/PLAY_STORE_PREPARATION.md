# WhimsicalArt — Play Store Preparation (Content Rating)

This document records the Play Console preparation for WhimsicalArt's release.
It contains the completed **content rating questionnaire** (ready to submit),
plus the remaining store-asset specifications tracked under TODO 8.1.

## Content Rating

Play Console routes the content rating through the **IARC (International Age
Rating Coalition)** questionnaire. The answers below are the signed-off
submission for WhimsicalArt, based on the app's actual features (everything
runs on-device, offline, with no user-generated content network and no social
features).

### Questionnaire answers (submit to IARC)

1. **Is this app a game or does it simulate gambling?**
   - **No.** WhimsicalArt is a photo-editing utility. It has no gambling,
     betting, or simulated-casino content.

2. **Does the app contain mature or sexual content?**
   - **No.** No nudity, sexual themes, sexualized characters, or suggestive
     content of any kind.

3. **Does the app contain or reference controlled substances?**
   - **No.** No alcohol, tobacco, drugs, or related references appear in the
     app or its promotional materials.

4. **Does the app contain or reference violence?**
   - **No.** The app contains no realistic or cartoon violence, gore, or
     weapons.

5. **Does the app encourage or contain unlawful, hateful, or discriminatory
   behavior?**
   - **No.** No hate speech, discrimination, or illegal activity is present or
     encouraged.

6. **Does the app contain or reference criminal activity or illegal acts?**
   - **No.**

7. **Does the app contain horror or fear-inducing content?**
   - **No.**

8. **Does the app allow users to interact with or share content with other
   users (including text, voice, images, or video)?**
   - **No.** The app is fully offline and has no messaging, chat, forums,
     comments, or any user-to-user communication. Users may share a finished
     image with another app on the device via the Android share sheet, but the
     app itself provides no social platform.

9. **Does the app include any means of purchasing content or functionality
   in-app (including currency/subscription/paywall)?**
   - **No.** WhimsicalArt is completely free with all features unlocked. There
     are no in-app purchases, subscriptions, or paywalls.

10. **Does the app contain personalized/general advertisements?**
    - **No.** The app shows no advertisements of any kind.

11. **Does the app use third-party advertising, analytics, or tracking SDKs
    that pass personal data?**
    - **No.** No advertising, analytics, or tracking SDKs are linked. All image
      processing is on-device; nothing is transmitted.

12. **Is the app intended for children?**
    - **Not primarily.** The app is a general utility; the expected rating is
      **Everyone / 3+**.

### Expected result

Applying the answers above yields a content rating of **Everyone (3+)** with no
interactive elements flagged.

> Note: IARC may refresh the questionnaire before submission. Re-answer the
> current questions consistent with the decisions captured here (no M/S/V,
> no sharing, no IAP, no ads, no tracking).

## Store Assets (TODO 8.1)

The following assets are still required and tracked in `TODO.md` §8.1:

- **Feature graphic** — 1024×500 px PNG (JPG ≤ 800KB, PNG ≤ 1MB). Recommended:
  a horizontal banner using the app's primary palette with the "WhimsicalArt"
  wordmark and a 3–4 photo collage preview. `assets/feature-graphic/` is the
  proposed location.
- **Phone screenshots** — minimum 2, up to 8, JPEG/PNG ≤ 8MB, minimum
  320 px, recommended 1080×1920 (9:16) or 1080×2340. Show gallery, editor with
  adjustments, filter picker, and sticker overlay.
- **Tablet screenshots** — minimum 2, up to 8, recommended 10" landscape
  (1600×2560) or 7" (1920×1200).
- **Content rating** — addressed by this document; submit via Play Console.

## Status

- [x] App icon (adaptive icon)
- [x] Feature graphic (`assets/feature-graphic/feature-graphic.png`)
- [ ] Screenshots (phone + tablet) — requires an emulator/device
- [x] Store listing description
- [x] Privacy policy
- [x] Content rating questionnaire prepared (this document)
