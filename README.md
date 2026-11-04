# MiniPen Companion

Carnet de peinture de figurines pour Android.

- **Réalisations** : photo de la figurine terminée (appareil photo ou galerie), nom, notes, et toutes les étapes de peinture.
- **Étapes** : titre (sous-couche, aplats, lavis…), technique **pinceau** ou **aérographe**, pots utilisés, notes (dilution, pression…). Réordonnables.
- **Collection** : les pots que tu possèdes, cochés depuis le catalogue ; pots perso pour les autres marques.
- **Catalogue** : Citadel, Vallejo (Model Color, Game Color, Model Air, Xpress, lavis), The Army Painter (Warpaints, Quickshade, Speedpaint, bombes), Prince August — recherche par nom ou référence, filtres marque/gamme.

Les données restent sur le téléphone (fichier JSON + photos dans l'espace privé de l'app).

## Installer l'APK

1. Ouvrir l'onglet **Releases** du dépôt et télécharger `MiniPenCompanion-x.y.z.apk` sur le téléphone.
2. L'ouvrir et autoriser l'installation depuis cette source si Android le demande.

Chaque nouvelle version s'installe par-dessus la précédente sans perdre les données (même clé de signature).

## Publier une version

- Chaque push sur `main` compile l'APK (artefact téléchargeable dans l'onglet *Actions*).
- Pousser un tag `vX.Y.Z` crée une **Release** GitHub avec l'APK attaché :

```bash
git tag v1.1.0 && git push origin v1.1.0
```

## Développement

Kotlin + Jetpack Compose (Material 3), kotlinx.serialization, Coil, Navigation Compose. `minSdk 26`, `targetSdk 35`.

```bash
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk
```

Le catalogue est dans `app/src/main/java/com/carottejy/minipen/data/Catalog.kt` (une ligne par pot : `référence|nom|#teinte`). Les teintes sont des approximations d'écran.

**Signature** : la clé `keystore/minipen-release.jks` est versionnée pour que la CI produise des APK installables les uns par-dessus les autres. C'est acceptable tant que le dépôt reste privé ; s'il devient public, générer une nouvelle clé et la passer par des secrets GitHub.
