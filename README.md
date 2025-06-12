# 📘 Installatiehandleiding – Cycling Manager App

Volg deze stappen om de backend en frontend van het project correct op te zetten met Supabase en DigitalOcean.

---

## 🧱 Backend Database opzetten met Supabase

1. Ga naar [https://supabase.com](https://supabase.com) en maak een nieuw account aan.
2. Ga naar je **Dashboard** en:
   - Maak een **nieuwe organisatie** aan.
   - Maak vervolgens een **nieuw project** aan.
3. Geef het project een naam en kies een sterk wachtwoord.  
   **⚠️ SLA DIT WACHTWOORD OP!**
4. Selecteer regio: **EU West (Paris)**.
5. Klik op **Create project**.
6. Na aanmaak, klik bovenaan op **Connect**. Hier vind je alle nodige gegevens om een databaseverbinding op te stellen.

---

## ⚙️ Backend configureren

1. Open de GitHub-repository van de **backend**.
2. Navigeer naar:

   ```
   src/main/resources/application.properties
   ```

3. Vervang de volgende regels:

   ```properties
   spring.datasource.url=jdbc:postgresql://<HIER_SUPABASE_HOST>:5432/postgres?prepareThreshold=0
   spring.datasource.username=<HIER_SUPABASE_USERNAME>
   spring.datasource.password=<HIER_SUPABASE_WACHTWOORD>
   ```

   > Vervang `<HIER_SUPABASE_HOST>`, `<HIER_SUPABASE_USERNAME>` en `<HIER_SUPABASE_WACHTWOORD>` met de gegevens van Supabase.

✅ Dat is alles voor het instellen van de databaseverbinding!

---

## ☁️ Backend hosten op DigitalOcean

1. Ga naar [https://www.digitalocean.com/](https://www.digitalocean.com/) en maak een account aan.
2. Maak een **nieuw project** aan.
3. Klik rechtsboven op **Create** > kies **App Platform**.
4. Selecteer **Git** als provider en kies de **Cycling Manager Scraper** repository.
5. Kies:
   - **Branch**: `main`
   - Laat **autodeploy** aan staan
   - Klik op **Size** en selecteer het budget van **$10/maand**
6. Kies regio: **Amsterdam**
7. Geef de app een naam en klik op **Create App**

✅ Je backend is nu live!

---

## 🌐 Frontend configureren

1. Kopieer de URL van de gedeployde backend uit DigitalOcean.
2. Navigeer naar de frontend-repository.
3. Open het bestand `.env` en vervang:

   ```env
   NEXT_PUBLIC_API_URL=XXXX
   ```

   met de juiste URL van je backend.

---

## ✅ Klaar!

Je app is nu correct verbonden met de database en live gedeployed!
