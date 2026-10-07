# Skraft — Backend

API Spring Boot 3.3 (Java 21) : portfolios créateurs, marketplace (produits / services), messagerie et notifications.

## Configuration

**Priorité de configuration** (Spring Boot) :

1. **Variable d’environnement** (CI / prod / shell local).
2. **Profil `local`** + **`src/main/resources/application-local.yml`** (fichier **ignoré par Git**) :

   ```bash
   cd backend
   SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
   ```

3. **Modèle sans secrets** : copier `backend/.env.example` → `.env.local`, remplir les valeurs, puis les exporter ou les dupliquer dans `application-local.yml`.

Ne commite jamais un vrai secret dans `application.yml`.

### Stockage : local vs Cloudflare R2

**Par défaut** (`R2_ENABLED=false` ou absent) : fichiers sous `app.storage.local-dir`, URLs  
`{app.storage.public-base-url}/api/storage/...` (voir `StorageController`).

**Avec R2** (`R2_ENABLED=true`) : uploads via l’API S3 vers le bucket ; l’URL enregistrée est  
`{R2_PUBLIC_BASE_URL}/{clé-objet}`.  
Configurer **CORS** sur le bucket pour `http://localhost:3000` (et la prod).

| Variable | Rôle |
|----------|------|
| `R2_ENABLED` | `true` pour activer R2 (désactive le stockage local pour `StorageService`) |
| `R2_BUCKET` | Nom du bucket (ex. `plateforme-media`) |
| `R2_ENDPOINT` | **Base** S3 : `https://<ACCOUNT_ID>.r2.cloudflarestorage.com` (sans `/nom-bucket` ; si tu colles l’URL du dashboard avec `/plateforme-media`, elle est corrigée automatiquement) |
| `R2_ACCESS_KEY` / `R2_SECRET_KEY` | Token API R2 (S3) |
| `R2_PUBLIC_BASE_URL` | URL publique sans slash final (ex. `https://pub-….r2.dev`) |

- Si l’API tourne derrière un reverse proxy, `STORAGE_PUBLIC_BASE_URL` reste utile en mode **local** uniquement.

En production, un **custom domain** sur le bucket est préférable au seul `r2.dev`.

### Variables d’environnement principales

| Variable | Rôle |
|----------|------|
| `DB_PASSWORD` | Mot de passe PostgreSQL |
| `JWT_SECRET` | Clé de signature des JWT (longue chaîne aléatoire) |
| `STORAGE_PUBLIC_BASE_URL` | Base URL des liens `/api/storage/**` (mode stockage local) |
| `FRONTEND_URL` | URL du frontend (`app.frontend-url`) |

### E-mail (notifications `EMAIL` / `BOTH`)

Sans configuration SMTP, les notifications sont toujours enregistrées en base ; l’envoi réel est ignoré (trace en `DEBUG`).

Pour activer l’envoi (Spring Mail + `JavaMailSender`), définir au minimum `spring.mail.host`. L’envoi SMTP est **asynchrone** (`@Async`) pour ne pas bloquer les requêtes HTTP.

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

## Comptes de test

Voir `V8__seed_test_accounts.sql` (`admin@noprobleme.com` / `Admin123!`) et `V22__seed_test_client_credits.sql` (`client@noprobleme.com` / `Client123!`).

## Développement

```bash
cd backend && SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

Tests :

```bash
mvn clean verify
```
