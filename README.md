# NoProbleme — Backend

API Spring Boot 3.3 (Java 21) pour la plateforme SaaS.

## Refonte Écosystème (niche)

Flux métier : formulaire client → bot DeepSeek → agent (démo) → validation client → paiement Stripe → planificateur → publications simulées.

- Tables : `niche_requests` (refonte complète), `platform_config`, `scheduled_configs`, liaisons `scheduled_posts` / `chat_messages`.
- **Attention migration** : `V14__refonte_niche_requests.sql` exécute `DROP TABLE niche_requests CASCADE`. Sur une base déjà migrée, sauvegarder les données si nécessaire avant déploiement.
- **Scheduler** : une seule propriété `scheduling.enabled` (`true` par défaut en prod). Les tests utilisent `scheduling.enabled: false` (`application-test.yml`). Ancienne `scheduling.publication-job.enabled` n’est plus utilisée.
- **Planning niche** : `scheduled_configs` utilise `publication_slots` (JSONB : liste `{ dayOfWeek, time }`). Migration **`V20__scheduled_configs_publication_slots.sql`**. L’API `GET/PUT /api/scheduler/config/{requestId}` attend `publicationSlots` (voir exemple ci‑dessous) ; le nombre de créneaux doit égaler `nbPostsPerWeek` sur la demande.

Exemple `PUT /api/scheduler/config/{requestId}` (pour **N** publications / semaine, le tableau contient **exactement N** entrées ; plusieurs heures le même jour sont possibles) :

```json
{
  "nicheRequestId": "00000000-0000-0000-0000-000000000000",
  "publicationSlots": [
    { "dayOfWeek": 1, "time": "08:00" },
    { "dayOfWeek": 1, "time": "14:00" },
    { "dayOfWeek": 1, "time": "21:00" }
  ]
}
```

(`dayOfWeek` : **0–6**, même échelle que l’ancien champ `publication_days` ; `time` : **HH:mm** en 24 h. Les doublons exacts jour+heure sont rejetés.)

### Clés API (Grok, Nano Banana, fal.ai, DeepSeek)

**Priorité de configuration** (Spring Boot) :

1. **Variable d’environnement** (CI / prod / shell local) — ex. `export NANO_BANANA_API_KEY=...`
2. **Profil `local`** + **`src/main/resources/application-local.yml`** (fichier **ignoré par Git**) :

   ```bash
   cd backend
   SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
   ```

3. **Modèle sans secrets** : copier `backend/.env.example` → `.env.local`, remplir les clés, puis les exporter ou les dupliquer dans `application-local.yml`.

| Clé / variable | Service | Usage |
|----------------|---------|--------|
| `GROK_API_KEY` | xAI Grok | Analyse vidéo plan par plan |
| `NANO_BANANA_API_KEY` | Google Gemini Image | Génération fonds (`NanaBananaImageService`) |
| `FALAS_API_KEY` | fal.ai | Génération vidéo MiniMax |
| `DEEPSEEK_API_KEY` | DeepSeek | Bot écosystème |

Configuration dans `application.yml` :

```yaml
nano-banana:
  api-url: https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-image:generateContent
  api-key: ${NANO_BANANA_API_KEY:}
```

La clé **ne doit jamais** être commitée dans `application.yml`. En local, utilisez `application-local.yml` ou `NANO_BANANA_API_KEY`.

Si Google renvoie `403 PERMISSION_DENIED` avec *« API key was reported as leaked »* : la clé est révoquée — créez-en une nouvelle dans [Google AI Studio](https://aistudio.google.com/apikey) et mettez à jour `application-local.yml` ou la variable d’environnement.

### Clé DeepSeek (bot)

1. **Recommandé (CI / serveur)** : variable d’environnement  
   `export DEEPSEEK_API_KEY=sk-...`

2. **Développement local** : fichier **`src/main/resources/application-local.yml`** (déjà dans `.gitignore`) + profil Spring **`local`** :  
   `SPRING_PROFILES_ACTIVE=local mvn spring-boot:run`  
   Les valeurs de `application-local.yml` se fusionnent avec `application.yml` et fournissent `deepseek.api-key` si `DEEPSEEK_API_KEY` est vide.

3. **Sans clé API** (tests UI uniquement) : dans `application-local.yml` ou `application.yml`,  
   `deepseek.stub-without-key: true` — le bot renvoie une réponse factice au lieu d’appeler DeepSeek (pas de vraie IA).

Si le message d’accueil fonctionne mais le **premier message utilisateur** affiche une erreur, la cause est presque toujours : pas de clé (`DEEPSEEK_API_KEY` vide) ou serveur démarré **sans** `SPRING_PROFILES_ACTIVE=local` alors que la clé est dans `application-local.yml`.

Ne commite jamais une vraie clé dans `application.yml`.

### Stockage : local vs Cloudflare R2

**Par défaut** (`R2_ENABLED=false` ou absent) : fichiers sous `app.storage.local-dir`, URLs  
`{app.storage.public-base-url}/api/storage/...` (voir `StorageController`).

**Avec R2** (`R2_ENABLED=true`) : uploads via l’API S3 vers le bucket ; l’URL enregistrée est  
`{R2_PUBLIC_BASE_URL}/{clé-objet}` (ex. `https://pub-….r2.dev/demos/...`).  
Configurer **CORS** sur le bucket pour `http://localhost:3000` (et la prod).

| Variable | Rôle |
|----------|------|
| `R2_ENABLED` | `true` pour activer R2 (désactive le stockage local pour `StorageService`) |
| `R2_BUCKET` | Nom du bucket (ex. `plateforme-media`) |
| `R2_ENDPOINT` | **Base** S3 : `https://<ACCOUNT_ID>.r2.cloudflarestorage.com` (sans `/nom-bucket` ; si tu colles l’URL du dashboard avec `/plateforme-media`, elle est corrigée automatiquement) |
| `R2_ACCESS_KEY` / `R2_SECRET_KEY` | Token API R2 (S3) |
| `R2_PUBLIC_BASE_URL` | URL publique sans slash final (ex. `https://pub-….r2.dev` ou `https://pub-….r2.dev/plateforme-media` si ton hôte exige le préfixe bucket) |

- Si l’API tourne derrière un reverse proxy, `STORAGE_PUBLIC_BASE_URL` reste utile en mode **local** uniquement.

En production, un **custom domain** sur le bucket est préférable au seul `r2.dev`.

### Variables d’environnement principales

| Variable | Rôle |
|----------|------|
| `DEEPSEEK_API_KEY` | Appels bot `/api/ecosystem/bot/**` |
| `GROK_API_KEY` | Analyse vidéo Grok |
| `NANO_BANANA_API_KEY` | Génération images Gemini (Nano Banana) |
| `FALAS_API_KEY` | Génération vidéo fal.ai |
| `STORAGE_PUBLIC_BASE_URL` | Base URL des liens `/api/storage/**` (mode stockage local) |
| `STRIPE_SECRET_KEY` | Sessions Checkout + API Stripe |
| `STRIPE_WEBHOOK_SECRET` | Validation signature webhook |
| `PAYMENT_SIMULATE_WITHOUT_STRIPE` | Hors profil `local` : `true` + **sans** `STRIPE_SECRET_KEY` active la simulation checkout. Profil `local` : défaut **true** (voir section Paiement simulé). |
| `FRONTEND_URL` | URLs succès / annulation Stripe (`app.frontend-url`) |

### Paiement Stripe simulé (développement)

Avec **`SPRING_PROFILES_ACTIVE=local`**, la simulation est **activée par défaut** tant que `STRIPE_SECRET_KEY` est vide (`application.yml`, second document YAML). Tu n’as rien à ajouter dans `application-local.yml` pour éviter `STRIPE_NOT_CONFIGURED`.

Pour **désactiver** la simulation en local (tester de vrais appels Stripe) :  
`export PAYMENT_SIMULATE_WITHOUT_STRIPE=false` et configure `STRIPE_SECRET_KEY`.

Sans profil `local`, il faut soit `PAYMENT_SIMULATE_WITHOUT_STRIPE=true`, soit `app.payment.simulate-without-stripe: true` dans ta config, **sans** clé Stripe.

Dès que `STRIPE_SECRET_KEY` est renseignée, le comportement **reste Stripe réel** (le flag ne court-circuite pas une vraie clé). L’URL retournée après validation du modèle pointe vers  
`/dashboard/requests/{id}?payment=success&simulated=true` au lieu de Stripe.

### E-mail (notifications `EMAIL` / `BOTH`)

Sans configuration SMTP, les notifications sont toujours enregistrées en base ; l’envoi réel est ignoré (trace en `DEBUG`).

Pour activer l’envoi (Spring Mail + `JavaMailSender`), définir au minimum `spring.mail.host` (ex. variables ci‑dessous). L’envoi SMTP est **asynchrone** (`@Async`) pour ne pas bloquer les requêtes HTTP.

| Variable / propriété | Rôle |
|----------------------|------|
| `MAIL_HOST` | `spring.mail.host` (ex. `smtp-relay.brevo.com`) |
| `MAIL_PORT` | `spring.mail.port` (souvent `587`) |
| `MAIL_USERNAME` | `spring.mail.username` |
| `MAIL_PASSWORD` | `spring.mail.password` |
| `MAIL_FROM` | `app.mail.from` — adresse expéditeur **vérifiée** chez le fournisseur ; si vide, `spring.mail.username` est utilisé |

Exemple dans `application-local.yml` (non versionné) :

```yaml
spring:
  mail:
    host: smtp-relay.brevo.com
    port: 587
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
app:
  mail:
    from: no-reply@votredomaine.com
```

### Exemples curl

Lister ses demandes (client JWT) :

```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/ecosystem/my-requests?page=0&size=10"
```

Webhook Stripe (sans JWT — signature requise) :

```bash
curl -s -X POST http://localhost:8080/api/payments/webhook/stripe \
  -H "Stripe-Signature: $SIG" \
  -d @payload.json
```

Tarif unitaire (admin) :

```bash
curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
  http://localhost:8080/api/admin/config/tarif
```

## Compte client test (crédits IA)

Migration **`V22__seed_test_client_credits.sql`** :

| Champ | Valeur |
|--------|--------|
| Email | `client@noprobleme.com` |
| Mot de passe | `Client123!` |
| Rôle | `CLIENT` |
| Solde initial | **1000** crédits |

**Ajuster le solde** (admin) :

```bash
# Consulter
curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
  http://localhost:8080/api/admin/users/{userId}/credits

# Fixer (ex. 500 crédits)
curl -s -X PUT -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"balance":500}' \
  http://localhost:8080/api/admin/users/{userId}/credits
```

Comptes admin/agent de test : voir `V8__seed_test_accounts.sql` (`admin@noprobleme.com` / `Admin123!`).

## Développement

Avec clé DeepSeek dans `application-local.yml` :

```bash
cd backend && SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

Sans profil local (clé uniquement via env) :

```bash
mvn spring-boot:run
```

Tests :

```bash
mvn clean verify
```
