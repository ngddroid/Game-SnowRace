# Google Play listing: Frosty Roll Rally

Package: `com.ngstudio.frostyroll` · Version 1.0.0 (versionCode 1) · Developer: NG Studio
All counts below are checked against Google Play's limits.

## Store listing (Main store listing, en-US)

**App name** (28 / 30)
```
Frosty Roll Rally: Snow Race
```

**Short description** (74 / 80)
```
Snowball race 3D: roll, grow, build ice bridges and race to the x5 finish!
```

**Full description** (about 2,030 / 4,000): copy it from [long_description.txt](long_description.txt).

## Category and tags (Store settings)

| Field | Value |
|---|---|
| App or game | Game |
| Category | **Racing** |
| Tags (up to 5) | Casual, Arcade, Racing, Offline, Single player (pick the closest names in Play's tag list) |
| Contains ads | **No** (release builds show no ads; change this when AdMob is added) |
| In-app purchases | None |

## Release notes for 1.0.0 (356 / 500)
```
Welcome to Frosty Roll Rally! ❄
• 5,000 snowball race levels across 8 winter worlds
• 24 racers, 20 snowballs and 12 trails to collect
• Build ice bridges, bump rivals and climb to the x5 finish
• Boss races against the Avalanche and the Mega Ball
• Daily gifts, Lucky Wheel, missions and 30 trophies
Thanks for playing! Tell us what you think in a review.
```

## Contact details (Store settings > Store listing contact details)

| Field | Value |
|---|---|
| Email (required, public) | `store.ngdtechnolab@gmail.com` |
| Website (optional) | The page where `app/src/main/assets/legal/index.html` is hosted |
| Phone (optional) | +91 90237 85087 (already on your developer profile) |
| Developer | NGENDEV TECHNOLABS LLP, 1213, R K Prime, 12th, Near Nana Mava Circle, 150 Feet Ring Road, Rajkot, Gujarat 360005, India |

The game's privacy policy and terms show the same email, the company name and the address.

## Privacy policy and terms

Play needs a public **privacy policy URL** (App content > Privacy policy, and Store settings).

1. Everything is in **`app/src/main/assets/legal/`**: `index.html`, `privacy_policy.html`, `terms_conditions.html`,
   `legal.css`, the font and both logos. The game shows the same two pages in Settings, so edit them there
   (email, address, effective date) and the app and the website stay identical.
2. Upload the contents of that folder to any static host, for example:
   - **Firebase Hosting** (your project `game-sonwrace3d`): `firebase init hosting` with `app/src/main/assets/legal` as the public folder, then `firebase deploy --only hosting`.
     The URLs become `https://game-sonwrace3d.web.app/privacy_policy.html` and `.../terms_conditions.html`.
   - A small **public** GitHub repo with GitHub Pages turned on (don't use this repo's `docs/`, which holds private notes).
3. Paste the privacy-policy URL into Play Console. The terms URL can go in the full description or on the website.

## App content answers (Policy > App content)

**Data safety**
- Data collected: yes. Data shared with third parties: no (Firebase is a service provider). Encrypted in transit: yes. Users can ask for deletion: yes (by email).
- **App activity > App interactions**: collected. Purpose: Analytics. Optional (players can turn it off in Settings > Share usage & crash data).
- **App info and performance > Crash logs, Diagnostics**: collected. Purposes: Analytics, App functionality. Optional (same switch).
- **Device or other IDs** (Firebase installation ID, push token): collected. Purposes: Analytics, App functionality (push).
- Not collected: location, personal info, contacts, photos, files, financial info, messages, audio, health, web history.

**Other declarations**
- Advertising ID: **No** (the AD_ID permission is removed in the manifest).
- Ads: **No**.
- Target audience: choose the age groups honestly. If you include **under 13**, the Families policy applies (Firebase Analytics must stay in its child-safe settings and ads must be Families-certified). **13+** is the simpler choice.
- Content rating questionnaire: cartoon snowball bumping, no blood, no gambling with real money, no user chat. This usually comes out as Everyone / PEGI 3.

## Ranking tips (ASO)

- The words that count most are in the title (*Snow Race*) and the short description (*snowball race 3D, ice bridges*). The full description repeats *snow race, snowball, ice bridge, racing, offline* naturally. Don't add keyword lists, which Play's policy forbids.
- Screenshots: 6 to 8 portrait shots with a one-line caption each: the ice bridge, the bump, the x5 finish stairs, the shop, the 8 worlds and a boss race. The first two matter most.
- Feature graphic (1024 x 500): Pip pushing a big snowball onto an ice bridge, with the logo.
- Optional: a 15 to 30 second gameplay video on YouTube for the listing.
- Ask for ratings: after 3 won races, use the Google Play In-App Review API. Ratings strongly affect ranking.
- Localize the listing later (Spanish, Portuguese, Hindi, Indonesian, Russian) to get more search traffic.
- Seasonal: once a Santa or holiday outfit exists, add "Christmas" to the short description from Dec 1 to Jan 6.
