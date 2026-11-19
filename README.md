<<<<<<< HEAD
<div align="center">

# Anchor (web)

**Attention, protected.**

A Spring Boot web app that plans your day around your energy, turns focus sessions into binding commitments, helps you ride out urges, and blocks adult and distracting sites on your devices.

<br />

![Java](https://img.shields.io/badge/Java-21-E2553E?style=flat-square&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5-2E7D5B?style=flat-square&logo=springboot&logoColor=white)
![Thymeleaf](https://img.shields.io/badge/Thymeleaf-server--rendered-F2994A?style=flat-square&logo=thymeleaf&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-prod-1F4D3A?style=flat-square&logo=postgresql&logoColor=white)

</div>

<br />

## Running it

### Option 1: IntelliJ IDEA or VS Code (no setup)

**Requirements:** JDK 21 and Maven. IntelliJ bundles Maven; for VS Code install the *Extension Pack for Java*.

1. Open the `anchor-web` folder.
2. Run `AnchorApplication` (or `mvn spring-boot:run` in a terminal).
3. Open **http://localhost:8080**, create an account, and choose **Explore with sample data**.

Development uses an embedded H2 database stored in `./data`, so there is nothing to install or configure.

### Option 2: Docker with PostgreSQL (production-style)

```bash
docker compose up --build
```

Then open **http://localhost:8080**. Set `DATABASE_PASSWORD` and `ANCHOR_REMEMBER_ME_KEY` in your environment before deploying anywhere real.

### Tests

```bash
mvn test
```

Unit tests cover the analytics, domain parsing and formatting. An end-to-end MockMvc test signs up, loads sample data, renders every screen and runs a focus session.

<br />

## Features

| Screen | What it does |
|---|---|
| **Today** | The One Thing hero card, up to three nice-to-haves, everything else, day progress ring, focus minutes, energy peak advice, carry-over of unfinished tasks, evening shutdown prompt. |
| **Focus** | Commit to an intention and a duration. A live dial counts down; the session survives closed tabs and auto-completes on time. Ending early offers a 60-second pause first, then asks for a reason. |
| **Energy check-in** | Five levels from Drained to Peak, logged in two seconds. |
| **Urge timer** | A guided 60-second breathing pause, then a reflection on the trigger, plus practical next steps. |
| **Shield** | Clean-streak ring with milestones, urge history, block lists, a downloadable hosts file with 76,000+ adult domains, and step-by-step family-DNS setup for Android, Windows, iPhone and routers. |
| **Blocked sites** | Distraction and always-blocked lists with one-tap presets. Removing an always-blocked site requires a cooling-off period, then a 15-minute window to confirm. |
| **Insights** | Focus-time bar chart, weekday × hour energy heatmap, peak and dip windows, urge triggers, reasons for ending early, and a written summary. |
| **Evening shutdown** | Review the day, choose what moves to tomorrow, rate the day and write one note. |
| **Settings** | Name and time zone, theme (system, light, dark), focus defaults, cooling-off period, password, JSON export, sample data, erase history, delete account. |

**Why the Shield works this way:** a website can't change your device's network settings, so the web app does what the web can. It keeps your lists, generates a ready-made hosts file, and walks you through Cloudflare for Families DNS (`1.1.1.3` / `family.cloudflare-dns.com`), which blocks adult content on every device at once. The .NET MAUI version of Anchor applies the blocking automatically on Windows and Android.

<br />

## Architecture

```
src/main/java/com/anchor
├── AnchorApplication.java
├── config/       Security (form login, remember-me, CSP), typed properties, clock
├── security/     Principal and user lookup
├── domain/       JPA entities: users, tasks, sessions, energy, urges, sites, relapses, reviews
├── repo/         Spring Data repositories; every query is scoped by user id
├── service/      Tasks, focus, energy, shield, blocklist, reviews, sample data, analytics
├── util/         Per-user clock (time zones), domain parsing, formatting
└── web/          Controllers, onboarding gate, shared view model

src/main/resources
├── db/migration/ Flyway schema (runs on both H2 and PostgreSQL)
├── templates/    Thymeleaf pages and fragments
├── static/       CSS design system, small vanilla JS, Inter and Material Icons fonts
└── blocklists/   Compressed adult-domain list
```

**Engineering highlights**

- **Multi-user and secure.**
  - BCrypt passwords and CSRF protection on every form.
  - A strict Content-Security-Policy and secure cookies in production.
  - Every query is scoped by user id; another user's record returns a 404.
- **Time-zone correct.** Each user has a time zone, so "today", the energy heatmap and streaks are computed in local time while timestamps are stored in UTC.
- **Server-rendered and resilient.**
  - Every page works without JavaScript.
  - JavaScript only adds the live timer, the breathing animation, countdowns and confirmations.
- **Schema-managed.** Flyway migrations run unchanged on H2 (development) and PostgreSQL (production); Hibernate never alters the schema.
- **Testable core.** Analytics are pure functions with unit tests, and an injectable `Clock` keeps time-based logic deterministic.
- **Operations.**
  - A Docker multi-stage build running as a non-root user, with a health check.
  - An Actuator health endpoint and a `prod` profile driven by environment variables.

<br />

## Configuration

| Variable | Purpose |
|---|---|
| `SPRING_PROFILES_ACTIVE=prod` | Use PostgreSQL, template caching and secure cookies |
| `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | PostgreSQL connection |
| `ANCHOR_REMEMBER_ME_KEY` | Secret used to sign "keep me signed in" cookies |

<br />

## Licences

Third-party assets keep their own licences (see `docs/licenses`): Inter (SIL Open Font License 1.1), Material Icons (Apache 2.0), StevenBlack/hosts blocklist (MIT).
=======
# anchor-web-app
>>>>>>> ce08236702fb2bf43b479dbd971c6619ceb1fc3e
