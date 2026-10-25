# RedisLite — Redis-Inspired Key-Value Server in Java

RedisLite is a lightweight, Redis-inspired key-value server built from scratch using Core Java. It communicates with clients over TCP sockets and implements a subset of the Redis Serialization Protocol (RESP).

## Features

- TCP server listening on port 6379
- RESP command parsing and responses
- PING, SET, GET, DEL, and TTL commands
- Key expiration using `SET key value EX seconds`
- Thread-pool handling for multiple client connections
- Thread-safe in-memory storage using `ConcurrentHashMap`
- Disk persistence across server restarts
- Validation of commands and TTL values
- Automated tests using JUnit 5

## Technology Stack

- Java 21
- Maven
- TCP sockets
- RESP protocol
- ConcurrentHashMap
- JUnit 5

## Requirements

- JDK 21 or later
- Maven 3.9 or later

## Run the Project

Run the automated tests:

```bash
mvn clean test
```

Compile the project:

```bash
mvn compile
```

Start the server:

```bash
java -cp target/classes com.redislite.Main
```

The server listens on port `6379`.

## Supported Commands

| Command | Description | Example |
|---|---|---|
| PING | Check server responsiveness | `PING` |
| SET | Store a value | `SET name Java` |
| GET | Retrieve a value | `GET name` |
| DEL | Delete a key | `DEL name` |
| TTL | Get remaining expiry time | `TTL name` |
| SET with EX | Store a value with expiration | `SET session active EX 30` |

TTL results follow Redis-style conventions:

- `-1`: Key exists without an expiry.
- `-2`: Key does not exist or has expired.
- Non-negative integer: Remaining expiry time in seconds.

## Project Structure

```text
RedisLite/
├── pom.xml
├── .gitignore
├── README.md
└── src/
    ├── main/java/com/redislite/
    │   ├── Main.java
    │   ├── RespProtocol.java
    │   └── RedisStore.java
    └── test/java/com/redislite/
        └── RedisStoreTest.java
```

## Testing

Run all automated tests with:

```bash
mvn clean test
```

Manual tests have covered basic TCP commands, expiration, persistence across restarts, deletion persistence, Unicode values, invalid commands, and multiple client connections.

## Scope

RedisLite is an educational implementation of a subset of Redis functionality. It is not intended to replace production Redis. Compatibility with the official `redis-cli` has not yet been verified.

## Learning Outcomes

This project demonstrates Java networking, protocol parsing, concurrency, thread-safe data structures, TTL-based expiration, file persistence, and automated testing.
