# broker-platform-core

Java/Spring Boot backend for the Lynx broker platform. Exposes a REST + WebSocket API consumed by the React UI and the automated trading bot, and connects to an external stock exchange via HTTP and WebSocket.

## Architecture

The backend is a set of Spring Boot microservices orchestrated with Docker Compose. Two API gateways (one for human traders, one for bots) are the only services reachable from outside. All other services communicate over an isolated internal Docker network.

```
                        ┌─────────────┐
                   ┌───▶│ user-gateway│:8180  ◀── React UI / end users
                   │    └──────┬──────┘
                   │           │ (internal network)
                   │    ┌──────┴──────┐
                   │    │ bot-gateway │:8190  ◀── Trading Bot
                   │    └──────┬──────┘
                   │           │
          ┌────────▼───────────▼────────┐
          │         Internal Network     │
          │                             │
          │  user-service       :8182   │
          │  account-service    :8183   │
          │  transaction-service:8184   │
          │  holdings-service   :8185   │
          │  trade-service      :8186   │
          │  exchange-client    :8187   │
          │  notification-service:8188  │
          │  PostgreSQL         :5439   │
          └─────────────────────────────┘
```

## Services

| Service | Port | Responsibility |
|---|---|---|
| `user-gateway` | 8180 | API gateway for traders — JWT auth, request routing |
| `bot-gateway` | 8190 | API gateway for bot accounts — separate auth filter |
| `user-service` | 8182 | Registration, login, email verification, JWT issuance |
| `account-service` | 8183 | Account management, balance tracking, deposits/withdrawals |
| `transaction-service` | 8184 | Transaction ledger (buys, sells, deposits, withdrawals) |
| `holdings-service` | 8185 | Portfolio positions — stocks and options |
| `trade-service` | 8186 | Order placement (market & limit), options contracts |
| `exchange-client-service` | 8187 | WebSocket connection to external exchange, order sync |
| `notification-service` | 8188 | Real-time WebSocket push notifications to connected clients |
| `db` (PostgreSQL 16) | 5439 | Shared database for all services |

## Gateway Routes

Both gateways expose the same route table:

| Path prefix | Upstream service |
|---|---|
| `/users/**`, `/bots/**` | user-service |
| `/accounts/**`, `/funds/**` | account-service |
| `/transactions/**` | transaction-service |
| `/orders/**`, `/options/**` | trade-service |
| `/holdings/**`, `/portfolio/**` | holdings-service |
| `/notifications/ws` | notification-service (WebSocket) |
| `/exchange/market/**` (GET, public) | external exchange (no JWT required) |
| `/exchange/**` | external exchange (JWT required) |

## Tech Stack

- **Java 11+**, Spring Boot, Spring Cloud Gateway
- **PostgreSQL 16**
- **Spring WebSocket + SockJS** for real-time notifications
- **JWT (HMAC-SHA256)** for authentication
- **Docker + Docker Compose** for local orchestration

## Prerequisites

- Docker and Docker Compose
- A running stock exchange instance (or a mock) accessible at the URLs configured in `.env`

## Getting Started

1. Copy the environment template and fill in all required values:

```bash
cp core/.env.example core/.env
# edit core/.env
```

2. Start all services from the `core/` directory:

```bash
cd core
docker compose up --build
```

The user-gateway will be available at `http://localhost:8180`.

## Environment Variables

Create `core/.env` with the following variables:

```env
# Database
DB_USERNAME=
DB_PASSWORD=
DB_NAME=brokerplatformdb          # optional, defaults to brokerplatformdb

# Authentication
JWT_SECRET=                        # min 32 chars, used by all services
INTERNAL_TOKEN=                    # shared secret for inter-service calls

# Email (user-service password reset)
SMTP_USERNAME=
SMTP_PASSWORD=

# External exchange
EXCHANGE_BASE_URL=
EXCHANGE_WS_URL=
EXCHANGE_URL=                      # used by the gateways for proxying
EXCHANGE_API_KEY=
EXCHANGE_API_SECRET=
EXCHANGE_ADMIN_TOKEN=
EXCHANGE_PRICE_FEED_TICKERS=       # optional comma-separated ticker list

# Trading bot (auto-created account)
BOT_EMAIL=
BOT_PASSWORD=
BOT_USERNAME=team2-bot             # optional
SEED_DEPOSIT=100000                # optional
SEED_CURRENCY=USD                  # optional
LOG_LEVEL=INFO                     # optional
```

## Project Layout

```
broker-platform-core/
└── core/
    ├── docker-compose.yml
    ├── common/                    # Shared DTOs, domain models, enums, validators
    ├── user-gateway/
    ├── bot-gateway/
    ├── user-service/
    ├── account-service/
    ├── transaction-service/
    ├── holdings-service/
    ├── trade-service/
    ├── exchange-client-service/
    └── notification-service/
```

Each service follows the standard Maven/Spring Boot layout under `src/main/java/lynx/team2/`.

## Related Repositories

| Repo | Description |
|---|---|
| `broker-platform-ui` | React 19 frontend |
| `broker-platform-bot-gateway-spec` | Python algorithmic trading bot |
