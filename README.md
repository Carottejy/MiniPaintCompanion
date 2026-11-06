# MiniPaintCompanion

Carnet de peinture de figurines pour Android.

- **Mes projets** : photo de la figurine terminée (appareil photo ou galerie), nom, notes, et toutes les étapes de peinture.
- **Étapes** : titre (sous-couche, aplats, lavis…), technique **pinceau** ou **aérographe**, pots utilisés, notes (dilution, pression…). Réordonnables.
- **Dictée vocale** : dans un projet, le bouton « Dicter » crée une ou plusieurs étapes à partir d'une phrase (« Aplats au pinceau avec Macragge Blue, dilué 1 pour 1, ensuite lavis avec Nuln Oil ») ; le micro d'une étape la complète. Titre, technique, pots (reconnaissance tolérante aux fautes de transcription, références Vallejo comprises) et notes (dilution, pression…) sont remplis automatiquement.
- **Mes peintures** : les pots que tu possèdes, cochés depuis le catalogue ou **identifiés à la caméra** (étiquette ou QR code) ; pots perso pour les autres marques. Un pot presque vide se marque « à racheter ».
- **Achat** : la liste des pots à acheter/racheter (depuis la collection, le catalogue, une réalisation ou l'onglet Couleurs). Cocher un pot acheté — ou le scanner — le fait passer dans la collection. Liste partageable.
- **Théorie** : roue chromatique du peintre (RJB) ou de la lumière (RVB), harmonies (complémentaire, adjacentes, analogue, triade, rectangle, carré, camaïeu) et ombre/lumière ; pour chaque couleur, les pots du catalogue ou de ta collection les plus proches (écart ΔE en L*a*b*).
- **Catalogue** (onglet) : Citadel, Vallejo (Model Color, Game Color, Model Air, Xpress, lavis), The Army Painter (Warpaints, Quickshade, Speedpaint, bombes), Prince August — recherche par nom ou référence, filtres marque/gamme.

- **Sauvegarde / restauration** (menu ⋮) : exporte toutes les réalisations, photos, la collection et les pots perso dans un `.zip` (Drive, Téléchargements…), et le réimporte sur un autre téléphone.

Les données restent sur le téléphone (fichier JSON + photos dans l'espace privé de l'app) ; pense à faire une sauvegarde avant de changer d'appareil.

- **Pipette** : sur une photo, un curseur relève une couleur (moyenne de quelques pixels) ; l'appli propose les pots les plus proches (ΔE L*a*b*), à ajouter à la liste d'achat. Jusqu'à 8 couleurs épinglées pour composer une palette.

## Identifier un pot

Caméra intégrée (CameraX), deux modes :
- **Étiquette** : reconnaissance de texte ML Kit embarquée (hors ligne), une image toutes les 0,7 s ; le texte est rapproché du catalogue (`VoiceParser.candidates` : noms tolérants aux erreurs de lecture, marque et gamme lues sur l'étiquette, références Vallejo). Fonctionne aussi sur une photo de la galerie.
- **QR code** : le contenu (souvent une URL produit) est analysé de la même façon ; le pot choisi est mémorisé (`qr:<contenu>`) et inclus dans la sauvegarde.

## Mise à jour automatique

L'APK GitHub interroge `releases/latest` au lancement (au plus toutes les heures), télécharge la nouvelle version en arrière-plan et l'installe au lancement suivant via `PackageInstaller` (sans confirmation sur Android 12+ grâce à `UPDATE_PACKAGES_WITHOUT_USER_ACTION` ; l'autorisation « installer des applis inconnues » est demandée une fois). Désactivable dans l'onglet Info. Nécessite un dépôt **public** (l'API ne répond pas sans authentification sur un dépôt privé).

## Bugs et idées

L'onglet Info envoie les retours sans compte GitHub : l'appli poste vers la fonction Supabase `minipaint-feedback` (projet NoobHammer40k), qui crée le ticket avec un jeton GitHub gardé côté serveur (secret `GITHUB_TOKEN`, jeton « fine-grained » limité à ce dépôt avec la permission *Issues : lecture et écriture*). Étiquettes `bug` / `enhancement`, limite de 5 envois / 10 min par adresse. Les modèles `.github/ISSUE_TEMPLATE` servent à ceux qui passent par GitHub directement.

## Variantes

- `github` : APK des Releases, avec mise à jour automatique (`./gradlew assembleGithubRelease`).
- `play` : pour le Play Store, sans auto-update ni permission `REQUEST_INSTALL_PACKAGES`, interdites par Google Play (`./gradlew bundlePlayRelease`).

## Installer l'APK

1. Ouvrir l'onglet **Releases** du dépôt et télécharger `MiniPaintCompanion-1.0.N.apk` sur le téléphone.
2. L'ouvrir et autoriser l'installation depuis cette source si Android le demande.

Chaque nouvelle version s'installe par-dessus la précédente sans perdre les données (même clé de signature).

## Publier une version

Les notes de version sont dans `app/src/main/assets/CHANGELOG.md` (affichées dans l'onglet Info). La première section, titrée `## @VERSION@ — @DATE@`, décrit les changements à venir : la CI y inscrit le numéro et la date, et s'en sert comme texte de la release. Avant d'ajouter de nouveaux changements, remplacer `@VERSION@ — @DATE@` de la section précédente par la version publiée, puis ouvrir une nouvelle section `@VERSION@`.

Chaque push sur `main` compile l'APK signé et publie automatiquement une **Release** `v1.0.<numéro de build>` avec l'APK attaché (marquée *latest*).

## Développement

Kotlin + Jetpack Compose (Material 3), kotlinx.serialization, Coil, Navigation Compose. `minSdk 26`, `targetSdk 36`.

```bash
./gradlew assembleGithubRelease   # app/build/outputs/apk/github/release/app-github-release.apk
```

Le catalogue est dans `app/src/main/java/com/carottejy/minipaintcompanion/data/Catalog.kt` (une ligne par pot : `référence|nom|#teinte`). Les teintes sont des approximations d'écran.

**Signature** : la clé `keystore/minipen-release.jks` est versionnée pour que la CI produise des APK installables les uns par-dessus les autres. C'est acceptable tant que le dépôt reste privé ; s'il devient public, générer une nouvelle clé et la passer par des secrets GitHub.
