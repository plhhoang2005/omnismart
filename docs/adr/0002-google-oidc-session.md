# ADR 0002: Google OIDC with server-managed sessions

- Status: Accepted
- Date: 2026-08-14

## Context

OmniSmart needs Google single sign-on for a React frontend and Spring Boot backend. The application must map an authenticated identity to its own users and store roles without exposing provider credentials to browser JavaScript or persisting provider access tokens unnecessarily.

## Decision

OmniSmart uses Spring Security OAuth2 Client and Google's OpenID Connect provider with the Authorization Code flow.

- Spring Boot initiates login at `/oauth2/authorization/google` and receives the callback at `/login/oauth2/code/google`.
- The stable Google `sub` claim, paired with provider `GOOGLE`, is the external identity key.
- Verified email and display name are copied into `app_user`. Because OmniSmart does not call Google APIs on a user's behalf, its authorized-client repository deliberately discards provider access and refresh tokens after authentication.
- A first-time user receives one store and an `OWNER` membership. Existing users retain their current memberships.
- Spring Security maintains authentication in the server session. `JSESSIONID` is HttpOnly and SameSite=Lax, and is Secure in HTTPS environments.
- State-changing API requests retain CSRF protection. React obtains a CSRF token immediately before logout.
- Store authorization is evaluated on the backend from `store_member`; the frontend's displayed role is never trusted for access decisions.

## Consequences

- React does not need a Google SDK or token storage.
- Running real login requires a Google Cloud Web client and an exact registered redirect URI.
- Horizontal scaling will eventually require a shared session store or sticky sessions.
- A later multi-provider feature must define explicit account-linking rules; matching email alone is not sufficient to link identities.

## References

- https://docs.spring.io/spring-security/reference/servlet/oauth2/login/
- https://github.com/spring-projects/spring-security-samples/tree/main/servlet/spring-boot/java/oauth2/login
- https://developers.google.com/identity/openid-connect/openid-connect
