# REST Login And AI Chatbot

## Runtime Ports

- Socket server: `8080` by default, configurable with `AUCTION_SERVER_PORT` or `-Dauction.server.port`.
- REST API server: `8081` by default, configurable with `AUCTION_REST_PORT` or `-Dauction.rest.port`.

## Auth Flow

1. JavaFX client calls `POST /api/auth/login`.
2. Server validates credentials and returns a session token.
3. Client keeps using the socket for auction features.
4. After login or socket reconnect, client sends `AUTH_SOCKET` with the session token.

## Gemini Setup

The Gemini API key must stay on the server. Do not put it in JavaFX source code.

```powershell
$env:GEMINI_API_KEY="AIzaSyA3H5_youAGfr6Uf3-V9hMboTHKSfnDgv0"
$env:GEMINI_MODEL="gemini-2.5-flash"
mvn exec:java -Dexec.mainClass="com.auction.server.ServerMain"
```

You can also use `-Dgemini.api.key=...` and `-Dgemini.model=...`.
The server also reads `.env`, `application.properties`, or `config/application.properties` from the project root.
If no API key is configured, the chatbot shows a friendly server-side configuration error.
