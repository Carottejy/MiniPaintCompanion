# MiniPaintCompanion

Carnet de peinture de figurines pour Android.

- **Réalisations** : photo de la figurine terminée (appareil photo ou galerie), nom, notes, et toutes les étapes de peinture.
- **Étapes** : titre (sous-couche, aplats, lavis…), technique **pinceau** ou **aérographe**, pots utilisés, notes (dilution, pression…). Réordonnables.
- **Collection** : les pots que tu possèdes, cochés depuis le catalogue ; pots perso pour les autres marques.
- **Catalogue** : Citadel, Vallejo (Model Color, Game Color, Model Air, Xpress, lavis), The Army Painter (Warpaints, Quickshade, Speedpaint, bombes), Prince August — recherche par nom ou référence, filtres marque/gamme.

- **Sauvegarde / restauration** (menu ⋮) : exporte toutes les réalisations, photos, la collection et les pots perso dans un `.zip` (Drive, Téléchargements…), et le réimporte sur un autre téléphone.

Les données restent sur le téléphone (fichier JSON + photos dans l'espace privé de l'app) ; pense à faire une sauvegarde avant de changer d'appareil.

## Installer l'APK

1. Ouvrir l'onglet **Releases** du dépôt et télécharger `MiniPaintCompanion-1.0.N.apk` sur le téléphone.
2. L'ouvrir et autoriser l'installation depuis cette source si Android le demande.

Chaque nouvelle version s'installe par-dessus la précédente sans perdre les données (même clé de signature).

## Publier une version

Chaque push sur `main` compile l'APK signé et publie automatiquement une **Release** `v1.0.<numéro de build>` avec l'APK attaché (marquée *latest*).

## Développement

Kotlin + Jetpack Compose (Material 3), kotlinx.serialization, Coil, Navigation Compose. `minSdk 26`, `targetSdk 35`.

```bash
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk
```

Le catalogue est dans `app/src/main/java/com/carottejy/minipaintcompanion/data/Catalog.kt` (une ligne par pot : `référence|nom|#teinte`). Les teintes sont des approximations d'écran.

**Signature** : la clé `keystore/minipen-release.jks` est versionnée pour que la CI produise des APK installables les uns par-dessus les autres. C'est acceptable tant que le dépôt reste privé ; s'il devient public, générer une nouvelle clé et la passer par des secrets GitHub.
