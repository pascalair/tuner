# Tuner

Accordeur pour **guitare**, **basse** et **ukulélé**. Épuré, rapide à démarrer,
sans publicité et sans pistage. Une seule permission : le micro.

## Télécharger et installer

👉 **[Télécharger Tuner.apk](https://github.com/pascalair/tuner/releases/latest/download/Tuner.apk)**

1. Ouvrez ce lien depuis votre téléphone Android.
2. Ouvrez le fichier téléchargé. Android demandera l'autorisation d'installer
   des applications « de source inconnue » : acceptez pour ce navigateur.
3. Lancez **Tuner**, autorisez le micro, et accordez.

Compatible Android 8.0 et plus récent. Pour mettre à jour, réinstallez l'APK
par-dessus (même lien, même signature).

## Fonctionnement

- **Mode auto** : jouez une corde, l'accordeur reconnaît laquelle et indique si
  elle est trop grave (♭) ou trop aiguë (♯). Un point se décale en temps réel
  selon la tension, et un **tracé** sous le point montre les dernières secondes.
- **Corde très fausse ?** Tapez sur une corde en bas de l'écran pour imposer la
  cible ; l'accordeur ne se fie plus qu'à elle.
- Un **son** confirme quand la corde est juste et stable.

Accordages : Guitare (E A D G B E), Basse (E A D G), Ukulélé (G C E A).

## Pour les développeurs

App Android native (Kotlin + Jetpack Compose). Détection de hauteur par
l'algorithme **YIN**, avec correction d'octave, filtrage des parasites et
maintien de la note pendant son extinction.

L'APK est recompilé et publié automatiquement par GitHub Actions à chaque mise à
jour de la branche `main` (Release `latest`). Build local éventuel (JDK 17) :

```
./gradlew assembleDebug
```

L'APK est généré dans `app/build/outputs/apk/debug/`.
