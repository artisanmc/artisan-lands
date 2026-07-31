# Task 3 — Atomic marker-set replacement

## RED

Ajout de quatre tests à `BlueMapMarkerSinkTest` :

- échange complet d'un `MarkerSet` et suppression des marqueurs obsolètes ;
- remplacement vide supprimant les jeux obsolètes de chaque carte active ;
- échec de la conversion de la deuxième forme sans publication du jeu partiel ;
- tentatives de publication sur toutes les cartes et agrégation des erreurs contextualisées.

Commande exécutée :

```powershell
rtk .\gradlew.bat test --tests "fr.iban.lands.integration.bluemap.BlueMapMarkerSinkTest" --no-daemon
```

Résultat RED : échec de compilation attendu, avec quatre erreurs `cannot find symbol` pour `BlueMapMarkerSink.replace(...)`.

## GREEN

Ajout de `ClaimMarkerSink.replace(Collection<ClaimMarkerDescriptor>)` et de son implémentation synchronisée dans `BlueMapMarkerSink`.

- Les `MarkerSet` de remplacement sont construits en mémoire avant toute mutation d'une carte BlueMap.
- Les cartes actives et les cartes résolues sont dédoublonnées, puis publiées ou nettoyées.
- Les échecs de publication sont tous tentés, agrégés et contextualisés avec l'identifiant de carte.
- `clear`, `put`, `remove`, `attach`, `detach` et `close` restent disponibles et conservent leur synchronisation.
- Le double `RecordingSink` de `ClaimMarkerSynchronizerTest` implémente le nouveau contrat pour préserver la compilation durant la transition vers la Task 4.

Commandes exécutées :

```powershell
rtk .\gradlew.bat test --tests "fr.iban.lands.integration.bluemap.BlueMapMarkerSinkTest" --no-daemon
rtk .\gradlew.bat test --no-daemon
rtk git diff --check
```

Résultats : les tests ciblés et la suite complète ont réussi ; `git diff --check` ne signale aucun problème.

## Fichiers modifiés

- `src/main/java/fr/iban/lands/integration/claims/ClaimMarkerSink.java`
- `src/main/java/fr/iban/lands/integration/bluemap/BlueMapMarkerSink.java`
- `src/test/java/fr/iban/lands/integration/bluemap/BlueMapMarkerSinkTest.java`
- `src/test/java/fr/iban/lands/integration/claims/ClaimMarkerSynchronizerTest.java`

## Auto-revue

Relecture indépendante effectuée sur le diff non commité : approuvée, sans point Critical, Important ou Minor. Elle confirme l'alignement avec le brief, l'atomicité de la construction, l'agrégation contextualisée des erreurs et le maintien du cycle de vie existant.

## Commit

Ce rapport est inclus dans le commit conventionnel `feat: replace BlueMap marker sets atomically`.

## Concerns

Aucun concern fonctionnel. Gradle affiche des avertissements JVM liés à l'accès natif et Git des avertissements d'accès au fichier global d'ignore ; ils n'affectent ni les tests ni le worktree.
