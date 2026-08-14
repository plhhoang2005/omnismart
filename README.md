# OmniSmart

OmniSmart is an open-source product for helping small stores turn structured product data into reviewed, channel-ready marketing content through one traceable workflow.

> Status: product planning and repository foundation. Application code is not implemented yet.

## Product goal

The first production slice is:

```text
Google Sheets / CSV / form
→ normalized product data
→ AI-assisted content draft
→ human review and approval
→ scheduled publishing or safe export
→ status and outcome dashboard
```

AI output is always reviewed by a human before publication. OmniSmart must never invent structured facts such as price, inventory or promotions.

## Repository roadmap

- `backend/` - Java and Spring Boot modular monolith.
- `frontend/` - React, TypeScript and Vite application.
- `infra/` - local and deployment infrastructure.
- `docs/` - architecture decisions, operations and demo materials.
- `.github/` - contribution templates and automated quality gates.

These application directories will be introduced by focused pull requests as implementation starts.

## Current documentation

- [Product and delivery plan](PLAN_PRODUCT_OMNISMART.md)
- [Contribution guide](CONTRIBUTING.md)
- [Security policy](SECURITY.md)
- [Third-party notices](THIRD_PARTY_NOTICES.md)

## Development workflow

1. Start from an issue with acceptance criteria.
2. Create a short-lived branch such as `feat/123-google-sso`.
3. Use Conventional Commits.
4. Open a pull request and complete the checklist.
5. Merge only after review and required checks pass.

See [CONTRIBUTING.md](CONTRIBUTING.md) for the complete workflow.

## Security

Do not commit credentials, `.env` files, OAuth secrets, database dumps or personal data. Report security concerns according to [SECURITY.md](SECURITY.md).

## License

Copyright 2026 Pham Le Huy Hoang and OmniSmart contributors.

Licensed under the [Apache License 2.0](LICENSE).

