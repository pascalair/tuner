# Tuner — accordeur Android (guitare / basse / ukulélé)

Contexte projet chargé automatiquement à chaque conversation. But : pouvoir reprendre
le développement sans tout réexpliquer. Le mainteneur teste l'APK sur son téléphone et
signale bugs / réglages ; on corrige, on pousse, GitHub recompile.

## Vue d'ensemble
App Android native **Kotlin + Jetpack Compose**. Accordeur épuré, démarrage rapide,
sans pub, une seule permission (micro). Détection de hauteur **YIN** sur le micro.
Distribution **100 % via GitHub Actions** → APK publié en Release.

## Architecture — `app/src/main/java/io/github/pascalair/tuner/`
- **AudioEngine.kt** — capture micro (`AudioRecord`), fenêtrage, appelle YIN, renvoie la
  fréquence ~21×/s sur le thread principal. Gate de silence sur le RMS.
- **PitchDetector.kt** — algorithme YIN (différence → moyenne cumulée normalisée → seuil
  → interpolation parabolique).
- **Tunings.kt** — instruments et cordes (MIDI→fréquence), helpers cents / noms de notes.
- **TunerController.kt** — cœur logique. Pipeline par mesure : repli harmonique
  (anti-octave) → verrouillage de note (anti-parasite + acquisition/relock) → médiane →
  lissage → détection « juste » + son → historique du tracé. État exposé = `TunerState`.
- **SuccessSound.kt** — son « ding-ding » ascendant généré (PCM) et joué via `AudioTrack`.
- **TunerScreen.kt** — UI Compose : sélecteur d'instrument, note + cents, indicateur
  (point animé + tracé vertical défilant), pastilles de cordes, écran permission.
- **MainActivity.kt** — permission micro, cycle de vie (start/stop), `KEEP_SCREEN_ON`.

## Paramètres réglables (la plupart des retours = un seul paramètre à bouger)
**TunerController** (`companion object`) :
- `IN_TUNE_CENTS=5` — tolérance « juste »
- `SMOOTH_ALPHA=0.2` — lissage de l'affichage (plus bas = plus lisse / moins réactif)
- `IN_TUNE_HOLD_MS=500` — durée juste avant le son
- `JUMP_LIMIT_CENTS=90` — au-delà, une mesure est jugée suspecte (parasite)
- `ACQUIRE_FRAMES=2` / `RELOCK_FRAMES=3` — mesures cohérentes pour afficher / changer de note
- `GRACE_MS=650` — maintien de la note affichée pendant l'extinction
- `HARMONIC_PENALTY=15` / `HARMONICS=[1,2,3]` — correction d'octave (préférence fondamentale)
- `HISTORY_SIZE` (top-level, ~3 s) — longueur du tracé
**AudioEngine** : `SILENCE_RMS=0.006` (sensibilité micro), `WINDOW_SIZE=4096`, `HOP=2048`.
**PitchDetector** : `threshold=0.15`, plage `minFreq=28` / `maxFreq=1320`.
**TunerScreen** : `CENTS_SCALE=50` (échelle ±50 cents), palette de couleurs en tête de fichier.

## Build & distribution — workflow IMPORTANT
- **Pas de build local** (ni JDK ni SDK Android sur la machine). Tout passe par GitHub Actions.
- `git push` sur **`main`** → `.github/workflows/build.yml` compile l'APK *debug* et le publie
  dans la Release tag **`latest`**.
- Lien stable (installation + partage) :
  `https://github.com/pascalair/tuner/releases/latest/download/Tuner.apk`
- **Surveiller le build via l'API publique** (sans auth) :
  `GET https://api.github.com/repos/pascalair/tuner/actions/runs?per_page=1`
  → `.workflow_runs[0].status` (`in_progress`/`completed`) et `.conclusion` (`success`/`failure`).
- **En cas d'échec** : `.../actions/runs/{id}/jobs` donne les noms d'étapes (public), mais le
  message Gradle exact nécessite une connexion (403 via API). → demander au mainteneur de
  coller l'erreur depuis le navigateur **connecté**, ou raisonner sur le code. (C'est ainsi
  qu'on a trouvé l'erreur `PitchDetector.kt Return type mismatch` au premier build.)

## Conventions / pièges
- Identité de commit **neutre, repo-local** : `pascalair` / `pascalair@users.noreply.github.com`.
  Commits co-signés `Claude`. (Repo public → aucune info perso dans le code/commits/README.)
- Sous **PowerShell**, `git push` affiche un `RemoteException` qui **n'est PAS une erreur**
  (git écrit sa progression sur stderr) — vérifier la ligne `-> main`.
- **NE PAS committer** : `.claude/`, captures d'écran (`image_guitaretuna.jpeg`, `img.jpeg`,
  `Screenshot_*`). Ajouter tout nouveau nom de capture au `.gitignore`.
- `gradle-wrapper.jar` est versionné (récupéré une fois).
- Compiler localement coûterait un gros téléchargement SDK ; on s'en passe tant qu'Actions suffit.

## État actuel (au dernier point)
Fonctionnel, validé au test : détection guitare/basse/uku, repli d'octave, lissage,
maintien de note, son ascendant, tracé défilant. Dernier correctif : anti-octave.
Builds 1→5 verts (sauf #1, corrigé).

## Reprise de session — à remplir par le mainteneur
Cycle : retour (souvent + capture) → ajustement code → commit/push `main` → vérif build API
→ « réinstalle via le lien stable ».

### Bugs / réglages à traiter (prochaine session)
- _(décrire ici les bugs constatés : sur quel instrument/corde, le comportement observé,
  idéalement une capture)_
