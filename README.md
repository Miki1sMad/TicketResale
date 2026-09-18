# TicketResale

Platforma za preprodaju sezonskih karata razvijena u Java 25, Spring Boot 4.1.1 i Spring Modulith arhitekturi.

### Ideja projekta
Vlasnici sezonskih ulaznica često ne mogu da prisustvuju svakoj utakmici u sezoni. Kroz ovu platformu oni svoje mesto za pojedinačnu utakmicu mogu da ponude na berzi. Kada drugi korisnik kupi to mesto, sistem automatski ukida pravo ulaska originalne sezonske karte za taj meč i novom kupcu generiše jednokratni bar-kod validan isključivo za tu utakmicu.

Sistem je građen da izdrži visoku konkurentnost i nagle skokove saobraćaja (npr. kada se pred važnu utakmicu na berzi pojavi oglas za koji se bori više desetina kupaca u istoj milisekundi). Rešen je problem dvostruke prodaje (double-selling), trka pri simultanom skeniranju na kapijama (anti-replay validacija), uz Transactional Outbox asinhronu obradu notifikacija.

---

## Pokretanje u Jednom Koraku (Quickstart)

Kompletan sistem (Spring Boot aplikacija, PostgreSQL 18, Redis i Mailpit) pokreće se jednom Docker Compose komandom:

```bash
docker compose up -d --build
```

### Pristup servisima
- Swagger UI (Interaktivno testiranje API-ja): [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- OpenAPI JSON specifikacija: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- Mailpit Web UI (Pregled poslatih mejlova i barkodova): [http://localhost:8025](http://localhost:8025)
- Podrazumevani Admin nalog: `admin@ticketresale.com` / `Admin123!Safe`

### Alternativno: Lokalni razvoj kroz Maven
Ukoliko se aplikacija pokreće lokalno van kontejnera:
```bash
# 1. Pokretanje samo zavisnih servisa
docker compose up -d postgres redis mailpit

# 2. Pokretanje aplikacije
./mvnw spring-boot:run
```

---

## Pregled Arhitekture

```
                      +---------------------------------------+
                      |         Spring Security & JWT         |
                      +-------------------+-------------------+
                                          |
        +---------------------------------+---------------------------------+
        |                                 |                                 |
        v                                 v                                 v
  [ auth & users ]                [ events & seasontickets ]        [ listings & orders ]
  - Stateless JWT auth            - Raspored utakmica               - Pesimistički lock u bazi
  - RBAC (Admin, User, Operator)  - MatchEntitlement prava          - Redisson distribuirani lock
  - BCrypt heširanje lozinki      - Claim sezonskih karata          - 10-minutni TTL rezervacija
        |                                 |                                 |
        +---------------------------------+---------------------------------+
                                          |
                      +-------------------+-------------------+
                      | Spring Modulith Domenski Događaji     |
                      | (Transactional Outbox u PostgreSQL)   |
                      +-------------------+-------------------+
                                          |
                         +----------------+----------------+
                         v                                 v
               [ notifications ]                     [ scanning ]
               - Asinhrono slanje mejlova            - Atomska provera na kapiji
               - ZXing Code 128 i QR kodovi          - Anti-replay zaštita
               - Mailpit integracija                 - Audit scan logovi
```

### Zašto Modularni Monolit?
U ranoj fazi sistema mikroservisi donose mrežnu latenciju, kompleksnost distribuiranih transakcija (2PC / Sagas) i operativni trošak bez realne potrebe za odvojenim deployment jedinicama.

Spring Modulith pruža ključne prednosti:
- Izolacija modula: Granice paketa se proveravaju kroz automatske testove (`ModularityTests.java`). Modulith sprečava neovlašćeno prelivanje koda između modula.
- Asinhroni događaji sa garancijom isporuke: Domenski događaji se upisuju u PostgreSQL tabelu `event_publication` unutar iste bazične transakcije (Transactional Outbox patern).
- Jednostavan deployment: Jedan kontejner i nula eksternih message brokera (nema potrebe za Kafka/RabbitMQ klasterom).

---

## Rešenja za Konkurentnost i Integritet Podataka

### 1. Sprečavanje dvostruke prodaje (Zero Double-Selling)
Kada 50 paralelnih zahteva pokuša da rezerviše isti oglas u istom trenutku:
- Pesimističko zaključavanje reda u bazi: `SELECT ... FOR UPDATE` zaključava oglas unutar transakcije.
- PostgreSQL parcijalni unikatni indeks:
  ```sql
  CREATE UNIQUE INDEX uq_active_reservation_per_listing 
  ON reservations (listing_id) 
  WHERE status = 'PENDING';
  ```
  Baza fizički odbija duplirane rezervacije čak i ako aplikativna logika zakaže.
- Redisson distribuirani lock: `RLock lock = redissonClient.getLock("lock:listing:" + id)` štiti bazu od iscrpljivanja connection pool-a tokom saobraćajnih pikova.

### 2. Turnstile Skener i Anti-Replay Zaštita
Sprečavanje ulaska dve osobe sa istim bar-kodom na različitim ulazima:
- Atomski SQL uslovni update:
  ```sql
  UPDATE resale_tickets 
  SET status = 'USED', scanned_at = NOW(), turnstile_id = :turnstileId 
  WHERE id = :ticketId AND status = 'VALID'
  RETURNING id;
  ```
  Ako upit vrati red: ulazak je odobren (`GRANTED`). Ako vrati 0 redova: ulazak se odbija (`ALREADY_USED`) i incident se beleži u tabeli `turnstile_scan_logs`.
- Heširanje tokena: Sirovi bar-kodovi (`TKT_<uuid>_<hmac>`) se u bazi čuvaju kao `SHA-256` heš. Kompromitovana baza podataka ne otkriva upotrebljive karte za ulazak.

### 3. Asinhroni Događaji i Slanje Notifikacija
Po završetku kupovine emituje se `OrderCompletedEvent`. Modulith ga automatski perzistira u outbox tabelu pre nego što ga asinhroni listener obradi. Mejl sa priloženim bar-kodom i QR kodom stiže kupcu bez blokiranja glavne HTTP niti. U slučaju pada mail servisa, neobrađeni događaji se automatski ponavljaju nakon restarta.

---

## Tehnološki Stack

| Komponenta | Tehnologija |
|---|---|
| Programski jezik | Java 25 LTS (Records, Pattern Matching, Sealed Classes) |
| Framework | Spring Boot 4.1.1 (Security, Data JPA, Validation) |
| Arhitektura | Spring Modulith 2.1.1 + ArchUnit |
| Baza podataka | PostgreSQL 18 + Flyway migracije |
| Keširanje i Lockovi | Redis + Redisson 4.7.0 |
| Email i Bar-kodovi | Mailpit (SMTP/REST) + ZXing 3.5.4 (Code 128 / QR) |
| API Dokumentacija | Springdoc OpenAPI 3.1.1 (Swagger UI) |
| Testiranje | JUnit 5, Testcontainers, Pytest / Python 3.14 (uv) |
| CI/CD i Registar | GitHub Actions + GitHub Container Registry (GHCR) |

---

## Pokretanje putem Docker Slike (GHCR)

Aplikacija se automatski gradi i objavljuje na svaki push na `main` granu:

```bash
docker pull ghcr.io/miki1smad/ticketresale:latest
```

---

## Ključni REST Endpointi

### Autentifikacija i Korisnici
| Metod | Putanja | Pristup | Opis |
|---|---|---|---|
| `POST` | `/api/v1/auth/register` | Javno | Registracija (opcioni claim sezonske karte) |
| `POST` | `/api/v1/auth/login` | Javno | Prijava i preuzimanje JWT para |
| `POST` | `/api/v1/auth/refresh` | Javno | Osvežavanje Access tokena |

### Utakmice i Sezonske Karte
| Metod | Putanja | Pristup | Opis |
|---|---|---|---|
| `GET` | `/api/v1/matches` | Javno | Lista predstojećih utakmica |
| `POST` | `/api/v1/matches` | `ADMIN` | Kreiranje rasporeda utakmice |
| `POST` | `/api/v1/season-tickets/claim` | `USER` | Preuzimanje sezonske karte preko bar-koda |
| `GET` | `/api/v1/season-tickets/my` | `USER` | Pregled svojih sezonskih karata i prava |

### Berza, Narudžbine i Validacija na Ulazu
| Metod | Putanja | Pristup | Opis |
|---|---|---|---|
| `POST` | `/api/v1/listings` | `USER` | Postavljanje karte na berzu |
| `GET` | `/api/v1/listings` | Javno | Pretraga aktivnih oglasa (po utakmici) |
| `POST` | `/api/v1/reservations` | `USER` | Rezervacija karte (10 min hold + lock) |
| `POST` | `/api/v1/orders/checkout` | `USER` | Kupovina karte (`Idempotency-Key` podrška) |
| `POST` | `/api/v1/turnstile/validate` | `STADIUM_OPERATOR` | Skeniranje na kapiji sa anti-replay proverom |

---

## Testiranje i Verifikacija

### Pokretanje svih integracionih testova (sa Testcontainers)
```bash
./mvnw clean test
```

### Pokretanje stress testova konkurentnosti
```bash
./mvnw test -Dtest=ListingConcurrencyIntegrationTest,TurnstileScanConcurrencyTest
```

### Pokretanje End-to-End test suite-a (Python + Pytest)
```bash
cd tests_e2e
uv run pytest -v
```

### Verifikacija modularnosti i generisanje dijagrama
```bash
./mvnw test -Dtest=ModularityTests
```
Generiše PlantUML dijagrame i module canvas dokumente u `target/spring-modulith-docs/`.

---

## Struktura Projekta

```
src/main/java/com/miki1smad/ticketresale/
├── auth/            # JWT autentifikacija, filteri i sigurnosna konfiguracija
├── users/           # Korisnički entiteti, uloge i profili
├── events/          # Klubovi, stadioni, sektori i utakmice
├── seasontickets/   # Sezonske karte i MatchEntitlement prava
├── listings/        # Berza karata i optimističko/pesimističko zaključavanje
├── orders/          # Rezervacije (10-min TTL), narudžbine i payment gateway
├── scanning/        # Validacija na ulazu, audit logovi i heširanje tokena
├── notifications/   # Domenski listeneri, email templejti i ZXing bar-kodovi
└── common/          # Globalna obrada grešaka, Redis lockovi i OpenAPI konfiguracija
```

---

## Licenca
MIT
