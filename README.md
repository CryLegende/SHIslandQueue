<div align="center">

<img src="assets/logo.png" alt="SkyHand" width="140">

# SHIslandQueue

*Gère une file d'attente pour la création d'îles SuperiorSkyBlock2.*

<img src="https://img.shields.io/badge/Paper-26.3-f2a900?style=for-the-badge" alt="Paper 26.3"> <img src="https://img.shields.io/badge/Java-25-e76f00?style=for-the-badge" alt="Java 25"> <img src="https://img.shields.io/badge/SkyHand-Opale-3aa655?style=for-the-badge" alt="SkyHand Opale">

</div>

---

## Présentation

Gère une file d'attente pour la création d'îles SuperiorSkyBlock2 : quand plusieurs joueurs créent leur île en même temps, les créations sont étalées, une par créneau (`creation-interval-seconds`). Un joueur qui arrive pendant un créneau occupé est mis en file et son île est créée à son tour. La file n'est pas une commande joueur.

Utilisé sur : **Opale**.

## Dépendances

- Obligatoires : `SuperiorSkyblock2`

## Commandes

| Commande | Alias | Description | Permission |
|---|---|---|---|
| `/shislandqueue reload` | — | Recharge `config.yml` et `messages.yml` sans toucher à la file ni au créneau en cours. | `shislandqueue.admin.reload` |
| `/shislandqueue list` | — | Affiche la file d'attente, l'intervalle réglé et le temps avant le prochain créneau. | `shislandqueue.admin.list` |

Les deux sous-commandes marchent depuis la console.

## Permissions

| Permission | Description | Par défaut |
|---|---|---|
| `shislandqueue.admin` | Toutes les commandes d'administration (parent des deux suivantes) | `op` |
| `shislandqueue.admin.reload` | Recharger `config.yml` et `messages.yml` | `false` |
| `shislandqueue.admin.list` | Voir la file d'attente | `false` |

## Configuration

Fichiers par défaut repris du serveur (secrets remplacés par `CHANGE_ME`). Cliquez sur un fichier pour l'ouvrir :

<pre>
plugins/SHIslandQueue/
├── <a href="src/main/resources/config.yml">config.yml</a>
└── <a href="src/main/resources/messages.yml">messages.yml</a>
</pre>

Au premier démarrage avec `messages.yml`, le fichier est créé à partir de la section `messages` de l'ancien `config.yml` (textes déjà en place), complétée par ceux du jar ; cette section n'est plus lue ensuite.

## Compilation

Java 25 et Maven :

```bash
mvn clean package
```

Le jar est généré dans `target/`.

---

<div align="center">

<sub>Développé par <b>Cry_Legende</b> pour le serveur <b>SkyHand</b> · Tous droits réservés</sub>

</div>
