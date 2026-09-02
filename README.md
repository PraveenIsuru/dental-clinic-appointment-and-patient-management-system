# Sunrise Dental Clinic — Appointment & Patient Management System

A three-tier web application for a private dental clinic in Colombo: appointment booking,
patient records, treatment billing and management reports.

**CIS6003 Advanced Programming · WRIT1 · ICBT Campus**

---

## Built in plain Java — no application framework

The HTTP server, front controller, filter chain, dependency-injection container, JSON codec,
session store, password hashing and connection pool are all **written by hand against the JDK**.
There is no Spring, no Hibernate, no Thymeleaf, no React, no Bootstrap.

| Concern | How it is done here | Framework normally used |
|---|---|---|
| HTTP transport | `com.sun.net.httpserver.HttpServer` (JDK) | Tomcat / Netty |
| Routing | `web.Router` — Front Controller | Spring MVC |
| Cross-cutting concerns | `web.FilterChain` — Chain of Responsibility | Servlet filters / AOP |
| Wiring | `config.ServiceRegistry` — constructor injection | Spring IoC |
| Persistence | `dao` interfaces + JDBC — DAO + Template Method | Spring Data JPA |
| Connection reuse | `dao.jdbc.ConnectionPool` — Singleton + Object Pool | HikariCP |
| JSON | `web.json` — hand-written writer and parser | Jackson |
| Sessions & cookies | `security.SessionManager` + `Set-Cookie` | Spring Session |
| Password hashing | PBKDF2-HMAC-SHA256 via `javax.crypto` | Spring Security BCrypt |
| Front end | HTML5 + CSS + vanilla `fetch()` | React / Thymeleaf |

**Two external artifacts, neither of them a framework:**

- `mysql-connector-j` — a JDBC **driver**. Without it the JVM cannot open a MySQL socket.
  It implements `java.sql.Driver` and imposes no architecture.
- `junit-jupiter` — **test scope only**, never on the runtime classpath. Task C of the brief
  explicitly requires test classes and test automation.

Writing the plumbing by hand is deliberate. The 40-mark Task B criterion asks for a *critical
evaluation* of each design pattern — and a pattern you implemented yourself, with trade-offs you
chose, is far easier to evaluate honestly than one hidden inside somebody else's framework.

## Architecture

```
Browser  ──HTTP──▶  PRESENTATION  ──DTOs──▶  BUSINESS  ──domain──▶  DATA  ──JDBC──▶  MySQL 8
                    web/                     service/               dao/
```

Tier boundaries are enforced, not just documented: a `web` class may never `import java.sql`,
and a `dao` class may never `import com.sun.net.httpserver`. Milestone 5 adds an
`ArchitectureTest` that scans imports and fails the build on a violation.

```
src/main/java/lk/dentalclinic
├── Main.java          entry point and routing table
├── config/            configuration + DI registry
├── model/             domain entities and enums
├── dao/  dao/jdbc/    DATA TIER
├── service/           BUSINESS TIER
│   └── pricing/       Strategy + Factory
├── event/             Observer
├── security/          hashing, sessions, cookies
├── validation/        business rules
└── web/               PRESENTATION TIER
    ├── handler/  dto/  json/
```

## Requirements

- **JDK 21 or newer** (built and verified on JDK 22)
- **Maven 3.9+**
- **MySQL 8** — WAMP on port 3306, or MAMP on 8889

## Setup

```bash
# 1. Configuration — the real file is git-ignored, so start from the example
cp config/application.properties.example config/application.properties
#    then edit db.url / db.user / db.password to match your MySQL

# 2. Database (from Milestone 1 onward)
mysql -u root -p < database/V1__schema.sql
mysql -u root -p < database/V2__routines.sql
mysql -u root -p < database/V3__seed.sql

# 3. Build and test
mvn clean verify

# 4. Run
java -jar target/sunrise-clinic.jar
```

Then open <http://localhost:8080>.

The port can be overridden without touching the config file, which is how the deployment target
supplies it:

```bash
SERVER_PORT=9090 java -jar target/sunrise-clinic.jar
```

## Testing

```bash
mvn test
```

The suite drives a **real server over real HTTP** using `java.net.http.HttpClient` — no MockMvc
and no mocking library. Tests bind port 0 so they never collide with a development instance.

## Acknowledgement

The initial database table shapes and the treatment price list were adapted from a peer's JavaFX
prototype, then extended with an audit table, triggers, stored routines and a redesigned
three-tier architecture. No source code from that prototype is reused here.
